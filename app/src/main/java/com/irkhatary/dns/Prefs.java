package com.irkhatary.dns;

import android.content.Context;
import android.content.SharedPreferences;

/** Small wrapper around SharedPreferences shared by the UI, service and boot receiver. */
final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("irk_dns", Context.MODE_PRIVATE);
    }
    static String str(Context c, String k, String d) { return sp(c).getString(k, d); }
    static boolean bool(Context c, String k, boolean d) { return sp(c).getBoolean(k, d); }
    static void put(Context c, String k, String v) { sp(c).edit().putString(k, v).apply(); }
    static void put(Context c, String k, boolean v) { sp(c).edit().putBoolean(k, v).apply(); }
}
