package com.khatary.pubgdns;

import android.net.VpnService;
import android.content.Intent;
import android.os.ParcelFileDescriptor;

/** Development placeholder only. DNS packet forwarding is not implemented yet. */
public class DnsVpnService extends VpnService {
    private ParcelFileDescriptor vpnInterface;
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // Deliberately do not report a connected state: real DNS forwarding must be implemented first.
        stopSelf();
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() {
        try { if (vpnInterface != null) vpnInterface.close(); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
