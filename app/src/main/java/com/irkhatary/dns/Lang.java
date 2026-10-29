package com.irkhatary.dns;

/** Tiny bilingual (Persian / English) string helper. */
final class Lang {
    static volatile boolean fa = true;
    private Lang() {}
    static String t(String persian, String english) { return fa ? persian : english; }
}
