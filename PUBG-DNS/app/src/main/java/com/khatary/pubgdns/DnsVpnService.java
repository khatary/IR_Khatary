package com.khatary.pubgdns;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public class DnsVpnService extends VpnService {
    public static final String ACTION_START = "com.khatary.pubgdns.START";
    public static final String ACTION_STOP = "com.khatary.pubgdns.STOP";
    public static final String EXTRA_SERVER = "server_index";
    public static final String ACTION_STATUS = "com.khatary.pubgdns.STATUS";
    public static final String EXTRA_RUNNING = "running";

    private static final String CHANNEL_ID = "pubg_dns_vpn";
    private ParcelFileDescriptor vpnInterface;
    private Thread worker;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private int serverIndex = 0;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopTunnel();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null) serverIndex = Math.max(0, Math.min(3, intent.getIntExtra(EXTRA_SERVER, 0)));
        if (running.get()) return START_STICKY;
        createNotificationChannel();
        startForeground(41, buildNotification("در حال راه‌اندازی DNS"));
        try {
            Builder builder = new Builder()
                    .setSession("PUBG DNS")
                    .setMtu(1500)
                    .addAddress("10.0.0.2", 32)
                    .addDnsServer("10.0.0.1")
                    .addRoute("10.0.0.1", 32);
            vpnInterface = builder.establish();
            if (vpnInterface == null) throw new IllegalStateException("VPN permission not granted");
            running.set(true);
            worker = new Thread(this::runPacketLoop, "PUBG-DNS-packet-loop");
            worker.start();
            updateNotification("DNS فعال است");
            broadcastStatus(true);
        } catch (Exception e) {
            stopTunnel();
            broadcastStatus(false);
            stopSelf();
        }
        return START_STICKY;
    }

    private void runPacketLoop() {
        byte[] packet = new byte[32767];
        FileInputStream input = new FileInputStream(vpnInterface.getFileDescriptor());
        FileOutputStream output = new FileOutputStream(vpnInterface.getFileDescriptor());
        while (running.get()) {
            try {
                int length = input.read(packet);
                if (length <= 0) continue;
                byte[] response = handleDnsPacket(packet, length);
                if (response != null && running.get()) output.write(response);
            } catch (Exception e) {
                if (running.get()) {
                    // Ignore malformed packets and transient resolver timeouts.
                }
            }
        }
    }

    private byte[] handleDnsPacket(byte[] packet, int length) {
        if (length < 28 || (packet[0] >> 4 & 0x0f) != 4) return null;
        int ihl = (packet[0] & 0x0f) * 4;
        if (ihl < 20 || length < ihl + 8 || (packet[9] & 0xff) != 17) return null;
        int totalLength = u16(packet, 2);
        if (totalLength > length || totalLength < ihl + 8) return null;
        int udpOffset = ihl;
        int sourcePort = u16(packet, udpOffset);
        int destPort = u16(packet, udpOffset + 2);
        int udpLength = u16(packet, udpOffset + 4);
        if (destPort != 53 || udpLength < 8 || udpOffset + udpLength > totalLength) return null;
        int fragment = u16(packet, 6);
        if ((fragment & 0x3fff) != 0) return null;
        int dnsLength = udpLength - 8;
        if (dnsLength < 12) return null;
        byte[] dnsQuery = new byte[dnsLength];
        System.arraycopy(packet, udpOffset + 8, dnsQuery, 0, dnsLength);
        byte[] dnsResponse = queryUpstream(dnsQuery);
        if (dnsResponse == null) return null;

        byte[] result = new byte[20 + 8 + dnsResponse.length];
        result[0] = 0x45;
        result[1] = 0;
        put16(result, 2, result.length);
        put16(result, 4, u16(packet, 4));
        put16(result, 6, 0x4000);
        result[8] = 64;
        result[9] = 17;
        // Reply source is the virtual DNS address and destination is the app's original source.
        System.arraycopy(packet, 16, result, 12, 4);
        System.arraycopy(packet, 12, result, 16, 4);
        put16(result, 10, checksum(result, 0, 20));
        put16(result, 20, 53);
        put16(result, 22, sourcePort);
        put16(result, 24, 8 + dnsResponse.length);
        put16(result, 26, 0); // UDP checksum is optional for IPv4.
        System.arraycopy(dnsResponse, 0, result, 28, dnsResponse.length);
        return result;
    }

    private byte[] queryUpstream(byte[] query) {
        String[] ips = {"1.1.1.1", "8.8.8.8", "9.9.9.9", "208.67.222.222"};
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(3500);
            if (!protect(socket)) return null;
            InetAddress server = InetAddress.getByName(ips[serverIndex]);
            DatagramPacket outgoing = new DatagramPacket(query, query.length, server, 53);
            socket.send(outgoing);
            byte[] buffer = new byte[4096];
            DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);
            socket.receive(incoming);
            byte[] response = new byte[incoming.getLength()];
            System.arraycopy(incoming.getData(), incoming.getOffset(), response, 0, incoming.getLength());
            return response;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int u16(byte[] b, int off) {
        return ((b[off] & 0xff) << 8) | (b[off + 1] & 0xff);
    }
    private static void put16(byte[] b, int off, int value) {
        b[off] = (byte) ((value >> 8) & 0xff);
        b[off + 1] = (byte) (value & 0xff);
    }
    private static int checksum(byte[] data, int offset, int length) {
        long sum = 0;
        for (int i = offset; i < offset + length; i += 2) {
            int word = (data[i] & 0xff) << 8;
            if (i + 1 < offset + length) word |= data[i + 1] & 0xff;
            sum += word;
        }
        while ((sum >> 16) != 0) sum = (sum & 0xffff) + (sum >> 16);
        return (int) (~sum) & 0xffff;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "PUBG DNS", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
    private Notification buildNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setContentTitle("PUBG DNS").setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_warning).setOngoing(true).build();
    }
    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(41, buildNotification(text));
    }
    private void broadcastStatus(boolean active) {
        Intent i = new Intent(ACTION_STATUS);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_RUNNING, active);
        sendBroadcast(i);
    }
    private void stopTunnel() {
        running.set(false);
        if (worker != null) worker.interrupt();
        try { if (vpnInterface != null) vpnInterface.close(); } catch (Exception ignored) { }
        vpnInterface = null;
        stopForeground(true);
        broadcastStatus(false);
    }
    @Override public void onDestroy() {
        stopTunnel();
        super.onDestroy();
    }
}