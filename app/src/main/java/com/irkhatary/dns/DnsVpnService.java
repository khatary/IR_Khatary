package com.irkhatary.dns;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DNS-only VPN.
 *
 * The tunnel advertises one virtual DNS server (10.7.0.1) and routes only that single address into
 * the TUN interface. Every DNS query the phone makes lands here, is forwarded to the user's chosen
 * resolver(s) over a normal socket, and the answer is written back. All other traffic never enters the
 * tunnel, so there is no speed loss and it works with IPv4-only, IPv6-only and dual-stack networks.
 *
 * Extras: parallel query handling, tiny response cache, hedged requests to the secondary resolver,
 * and automatic TCP retry when a resolver reports a truncated UDP answer.
 */
public class DnsVpnService extends VpnService {
    public static final String ACTION_START = "com.irkhatary.dns.START";
    public static final String ACTION_STOP = "com.irkhatary.dns.STOP";

    static final String VIRTUAL_DNS = "10.7.0.1";
    static final String TUN_ADDR = "10.7.0.2";

    // Live state read by the UI.
    static volatile boolean running;
    static volatile long startedAt;
    static volatile boolean cacheEnabled = true;
    static volatile InetAddress up1, up2;
    static final AtomicLong queries = new AtomicLong();
    static final AtomicLong answered = new AtomicLong();
    static final AtomicLong latencySum = new AtomicLong();

    private static final int TIMEOUT_MS = 2500;
    private static final int HEDGE_MS = 350;
    private static final long CACHE_TTL_MS = 45000;
    private static final int CACHE_MAX = 600;
    private static final String CHANNEL = "irk_dns_status";
    private static final int NOTIF_ID = 4107;

    private static final class CacheEntry {
        final byte[] data; final long expires;
        CacheEntry(byte[] d, long e) { data = d; expires = e; }
    }
    private static final LinkedHashMap<String, CacheEntry> CACHE =
            new LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> e) { return size() > CACHE_MAX; }
            };

    private volatile ParcelFileDescriptor tun;
    private Thread worker;
    private ThreadPoolExecutor pool;

    static void setUpstream(String a, String b) {
        InetAddress x = DnsTester.parseIp(a);
        if (x == null) return;
        InetAddress y = DnsTester.parseIp(b);
        up1 = x;
        up2 = (y != null && !y.equals(x)) ? y : null;
        synchronized (CACHE) { CACHE.clear(); }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopTunnel();
            stopSelf();
            return START_NOT_STICKY;
        }
        Lang.fa = "fa".equals(Prefs.str(this, "lang", "fa"));
        postForeground();               // must always be called after startForegroundService()
        if (!running) startTunnel();
        return START_STICKY;
    }

    private void postForeground() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm != null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL,
                        Lang.t("وضعیت اتصال DNS", "DNS connection status"), NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            int imm = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
            PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), imm);
            Intent stop = new Intent(this, DnsVpnService.class).setAction(ACTION_STOP);
            PendingIntent stopPi = PendingIntent.getService(this, 1, stop, imm | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
            nb.setSmallIcon(R.drawable.ic_stat_dns)
              .setContentTitle("IR_Khatary DNS")
              .setContentText(Lang.t("DNS فعال است", "DNS is active"))
              .setColor(0xFF00C98D)
              .setContentIntent(open)
              .setOngoing(true)
              .addAction(0, Lang.t("قطع اتصال", "Disconnect"), stopPi);
            Notification n = nb.build();
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIF_ID, n);
        } catch (Exception ignored) { /* service keeps working even if the notification is refused */ }
    }

    private synchronized void startTunnel() {
        try {
            cacheEnabled = Prefs.bool(this, "cache", true);
            Presets.P def = Presets.byId(Presets.DEFAULT_ID);
            setUpstream(Prefs.str(this, "dns1", def.a), Prefs.str(this, "dns2", def.b));
            if (up1 == null) setUpstream(def.a, def.b);

            int imm = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
            PendingIntent cfg = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), imm);
            ParcelFileDescriptor pfd = new Builder()
                    .setSession("IR_Khatary DNS")
                    .addAddress(TUN_ADDR, 32)
                    .addDnsServer(VIRTUAL_DNS)
                    .addRoute(VIRTUAL_DNS, 32)
                    .setMtu(1500)
                    .setBlocking(true)
                    .setConfigureIntent(cfg)
                    .establish();
            if (pfd == null) { stopSelf(); return; }

            tun = pfd;
            queries.set(0); answered.set(0); latencySum.set(0);
            startedAt = SystemClock.elapsedRealtime();
            pool = new ThreadPoolExecutor(2, 8, 20, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(256), new ThreadPoolExecutor.DiscardOldestPolicy());
            running = true;
            final ParcelFileDescriptor mine = pfd;
            worker = new Thread(new Runnable() { @Override public void run() { loop(mine); } }, "irk-tun");
            worker.start();
        } catch (Exception e) {
            stopTunnel();
            stopSelf();
        }
    }

    private synchronized void stopTunnel() {
        running = false;
        ParcelFileDescriptor t = tun;
        tun = null;
        try { if (t != null) t.close(); } catch (Exception ignored) { }
        if (worker != null) { worker.interrupt(); worker = null; }
        if (pool != null) { pool.shutdownNow(); pool = null; }
        try { stopForeground(true); } catch (Exception ignored) { }
    }

    @Override public void onRevoke() { stopTunnel(); stopSelf(); super.onRevoke(); }
    @Override public void onDestroy() { stopTunnel(); super.onDestroy(); }

    // ------------------------------------------------------------------ packet loop

    private void loop(ParcelFileDescriptor mine) {
        byte[] buf = new byte[32767];
        try {
            FileInputStream in = new FileInputStream(mine.getFileDescriptor());
            final FileOutputStream out = new FileOutputStream(mine.getFileDescriptor());
            while (running && tun == mine) {
                int n = in.read(buf);
                if (n < 0) break;
                if (n < 28) { if (n == 0) Thread.sleep(2); continue; }
                if (((buf[0] >> 4) & 15) != 4 || (buf[9] & 255) != 17) continue;   // IPv4 + UDP only
                final byte[] pkt = Arrays.copyOf(buf, n);
                ThreadPoolExecutor ex = pool;
                if (ex == null) break;
                try {
                    ex.execute(new Runnable() { @Override public void run() { answer(pkt, out); } });
                } catch (RejectedExecutionException ignored) { }
            }
        } catch (Exception ignored) { /* descriptor closed on shutdown */ }
        if (running && tun == mine) { stopTunnel(); stopSelf(); }
    }

    private void answer(byte[] p, FileOutputStream out) {
        try {
            int ihl = (p[0] & 15) * 4;
            if (ihl < 20 || p.length < ihl + 8) return;
            if ((p[16] & 255) != 10 || (p[17] & 255) != 7 || (p[18] & 255) != 0 || (p[19] & 255) != 1) return;
            if (u16(p, ihl + 2) != 53) return;
            int udpLen = u16(p, ihl + 4);
            if (udpLen < 20 || ihl + udpLen > p.length) return;
            int srcPort = u16(p, ihl);
            byte[] query = Arrays.copyOfRange(p, ihl + 8, ihl + udpLen);

            queries.incrementAndGet();
            long t0 = SystemClock.elapsedRealtime();
            String key = cacheEnabled ? cacheKey(query) : null;
            byte[] resp = key == null ? null : cacheGet(key, query);
            if (resp == null) {
                resp = forward(query);
                if (resp != null && key != null) cachePut(key, resp);
            }
            if (resp == null) return;          // let the client retry
            answered.incrementAndGet();
            latencySum.addAndGet(SystemClock.elapsedRealtime() - t0);

            byte[] reply = makeReply(p, srcPort, resp, query);
            synchronized (out) { out.write(reply); }
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ upstream

    private byte[] forward(byte[] query) {
        InetAddress a = up1, b = up2;
        if (a == null) return null;
        DatagramSocket s = null;
        try {
            s = new DatagramSocket();
            protect(s);
            boolean sentA = send(s, query, a);
            boolean hedgePending = false;
            if (!sentA) {
                if (b == null || !send(s, query, b)) return null;
            } else {
                hedgePending = b != null;
            }
            int id = ((query[0] & 255) << 8) | (query[1] & 255);
            long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
            byte[] buf = new byte[4096];
            while (true) {
                long left = deadline - SystemClock.elapsedRealtime();
                if (left <= 0) return null;
                s.setSoTimeout((int) (hedgePending ? Math.min(left, HEDGE_MS) : left));
                try {
                    DatagramPacket r = new DatagramPacket(buf, buf.length);
                    s.receive(r);
                    InetAddress from = r.getAddress();
                    boolean known = from.equals(a) || (b != null && from.equals(b));
                    int rid = ((buf[0] & 255) << 8) | (buf[1] & 255);
                    if (r.getLength() >= 12 && known && rid == id && (buf[2] & 0x80) != 0) {
                        byte[] resp = Arrays.copyOf(buf, r.getLength());
                        if ((resp[2] & 0x02) != 0) {           // truncated -> retry over TCP
                            byte[] full = tcpQuery(query, from);
                            if (full != null) return full;
                        }
                        return resp;
                    }
                } catch (SocketTimeoutException e) {
                    if (hedgePending) { send(s, query, b); hedgePending = false; }
                }
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (s != null) s.close();
        }
    }

    private boolean send(DatagramSocket s, byte[] q, InetAddress to) {
        try { s.send(new DatagramPacket(q, q.length, to, 53)); return true; }
        catch (Exception e) { return false; }
    }

    private byte[] tcpQuery(byte[] query, InetAddress to) {
        Socket s = null;
        try {
            s = new Socket();
            protect(s);
            s.connect(new InetSocketAddress(to, 53), 1500);
            s.setSoTimeout(2000);
            DataOutputStream o = new DataOutputStream(s.getOutputStream());
            o.writeShort(query.length);
            o.write(query);
            o.flush();
            DataInputStream i = new DataInputStream(s.getInputStream());
            int len = i.readUnsignedShort();
            if (len < 12) return null;
            byte[] r = new byte[len];
            i.readFully(r);
            return r;
        } catch (Exception e) {
            return null;
        } finally {
            try { if (s != null) s.close(); } catch (Exception ignored) { }
        }
    }

    // ------------------------------------------------------------------ cache

    /** Returns the offset just past the (single) question, or -1 if the packet is not cacheable. */
    private static int questionEnd(byte[] q) {
        if (q.length < 17) return -1;
        if ((((q[4] & 255) << 8) | (q[5] & 255)) != 1) return -1;
        int i = 12;
        while (i < q.length) {
            int l = q[i] & 255;
            if (l == 0) { i++; break; }
            if ((l & 0xC0) != 0) return -1;
            i += l + 1;
        }
        int end = i + 4;
        return end <= q.length ? end : -1;
    }

    private static String cacheKey(byte[] q) {
        int end = questionEnd(q);
        if (end < 0) return null;
        byte[] k = Arrays.copyOfRange(q, 12, end);
        for (int i = 0; i < k.length; i++) if (k[i] >= 'A' && k[i] <= 'Z') k[i] += 32;
        return new String(k, java.nio.charset.Charset.forName("ISO-8859-1"));
    }

    private static byte[] cacheGet(String key, byte[] query) {
        CacheEntry e;
        synchronized (CACHE) {
            e = CACHE.get(key);
            if (e != null && e.expires < SystemClock.elapsedRealtime()) { CACHE.remove(key); e = null; }
        }
        if (e == null) return null;
        byte[] r = e.data.clone();
        r[0] = query[0]; r[1] = query[1];
        return r;
    }

    private static void cachePut(String key, byte[] resp) {
        boolean ok = resp.length >= 12 && (resp[2] & 0x02) == 0 && (resp[3] & 15) == 0
                && (((resp[6] & 255) << 8) | (resp[7] & 255)) > 0;
        if (!ok) return;
        synchronized (CACHE) { CACHE.put(key, new CacheEntry(resp, SystemClock.elapsedRealtime() + CACHE_TTL_MS)); }
    }

    // ------------------------------------------------------------------ packet building

    private byte[] makeReply(byte[] orig, int clientPort, byte[] dns, byte[] query) {
        if (dns.length > 1472) {            // would not fit the MTU: answer with a truncated header
            int qe = questionEnd(query);
            int qLen = qe < 0 ? 0 : qe - 12;
            byte[] t = new byte[12 + qLen];
            System.arraycopy(dns, 0, t, 0, 12);
            t[2] |= 0x02;
            t[4] = 0; t[5] = (byte) (qLen > 0 ? 1 : 0);
            for (int i = 6; i < 12; i++) t[i] = 0;
            if (qLen > 0) System.arraycopy(query, 12, t, 12, qLen);
            dns = t;
        }
        int total = 28 + dns.length;
        byte[] r = new byte[total];
        r[0] = 0x45;
        put16(r, 2, total);
        put16(r, 4, (int) (System.nanoTime() & 0xffff));
        put16(r, 6, 0x4000);
        r[8] = 64;
        r[9] = 17;
        System.arraycopy(orig, 16, r, 12, 4);   // source = the virtual DNS address
        System.arraycopy(orig, 12, r, 16, 4);   // destination = the client
        put16(r, 10, checksum(r, 0, 20));
        put16(r, 20, 53);
        put16(r, 22, clientPort);
        put16(r, 24, 8 + dns.length);
        System.arraycopy(dns, 0, r, 28, dns.length);
        int c = udpChecksum(r);
        put16(r, 26, c == 0 ? 0xffff : c);
        return r;
    }

    private static int udpChecksum(byte[] p) {
        long sum = 0;
        for (int i = 12; i < 20; i += 2) sum += u16(p, i);
        sum += 17;
        sum += u16(p, 24);
        for (int i = 20; i < p.length; i += 2)
            sum += ((p[i] & 255) << 8) | (i + 1 < p.length ? p[i + 1] & 255 : 0);
        return fold(sum);
    }

    private static int checksum(byte[] b, int off, int len) {
        long sum = 0;
        for (int i = off; i < off + len; i += 2) sum += ((b[i] & 255) << 8) | (b[i + 1] & 255);
        return fold(sum);
    }

    private static int fold(long sum) {
        while ((sum >> 16) != 0) sum = (sum & 0xffff) + (sum >> 16);
        return (int) (~sum) & 0xffff;
    }

    private static int u16(byte[] b, int i) { return ((b[i] & 255) << 8) | (b[i + 1] & 255); }
    private static void put16(byte[] b, int i, int v) { b[i] = (byte) (v >> 8); b[i + 1] = (byte) v; }
}
