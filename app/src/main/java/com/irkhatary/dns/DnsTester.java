package com.irkhatary.dns;

import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.Charset;

/** DNS helpers: IP parsing (never does a name lookup), query building and latency test. */
final class DnsTester {
    private DnsTester() {}

    /** Parses an IPv4/IPv6 literal. Returns null for anything else (no DNS lookup is ever made). */
    static InetAddress parseIp(String s) {
        if (s == null) return null;
        s = s.trim();
        try {
            if (s.matches("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$")) {
                String[] p = s.split("\\.");
                byte[] b = new byte[4];
                for (int i = 0; i < 4; i++) {
                    int v = Integer.parseInt(p[i]);
                    if (v > 255) return null;
                    b[i] = (byte) v;
                }
                return InetAddress.getByAddress(b);
            }
            if (s.indexOf(':') >= 0 && s.matches("^[0-9a-fA-F:.]+$")) return InetAddress.getByName(s);
        } catch (Exception ignored) { }
        return null;
    }

    static byte[] buildQuery(int id, String name) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(id >> 8); o.write(id & 255);
        o.write(0x01); o.write(0x00);          // recursion desired
        o.write(0); o.write(1);                // QDCOUNT = 1
        for (int i = 0; i < 6; i++) o.write(0); // AN/NS/AR = 0
        for (String label : name.split("\\.")) {
            byte[] lb = label.getBytes(Charset.forName("US-ASCII"));
            o.write(lb.length);
            o.write(lb, 0, lb.length);
        }
        o.write(0);
        o.write(0); o.write(1);                // TYPE A
        o.write(0); o.write(1);                // CLASS IN
        return o.toByteArray();
    }

    /** Returns the round-trip time in ms, or -1 when the server did not answer. */
    static int ping(String host, int timeoutMs) {
        InetAddress ip = parseIp(host);
        if (ip == null) return -1;
        DatagramSocket s = null;
        try {
            s = new DatagramSocket();
            int id = (int) (System.nanoTime() & 0x7fff) | 0x4000;
            byte[] q = buildQuery(id, "example.com");
            long t0 = SystemClock.elapsedRealtime();
            long end = t0 + timeoutMs;
            s.send(new DatagramPacket(q, q.length, ip, 53));
            byte[] buf = new byte[1500];
            while (true) {
                long left = end - SystemClock.elapsedRealtime();
                if (left <= 0) return -1;
                s.setSoTimeout((int) left);
                DatagramPacket r = new DatagramPacket(buf, buf.length);
                s.receive(r);
                if (r.getLength() >= 12 && (((buf[0] & 255) << 8) | (buf[1] & 255)) == id && (buf[2] & 0x80) != 0) {
                    return (int) Math.max(1, SystemClock.elapsedRealtime() - t0);
                }
            }
        } catch (Exception e) {
            return -1;
        } finally {
            if (s != null) s.close();
        }
    }
}
