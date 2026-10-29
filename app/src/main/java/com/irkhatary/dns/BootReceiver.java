package com.irkhatary.dns;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;

/** Optionally reconnects after the phone restarts (only if VPN permission was already granted). */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String a = i == null ? null : i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !"android.intent.action.QUICKBOOT_POWERON".equals(a)) return;
        if (!Prefs.bool(c, "boot", false)) return;
        try {
            if (VpnService.prepare(c) != null) return;       // permission not granted yet
            Intent s = new Intent(c, DnsVpnService.class).setAction(DnsVpnService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
        } catch (Exception ignored) { }
    }
}
