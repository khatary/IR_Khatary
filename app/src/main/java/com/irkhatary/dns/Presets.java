package com.irkhatary.dns;

/** Built-in resolver presets. Availability differs per country / ISP, so the app can speed-test them. */
final class Presets {
    private Presets() {}

    static final class P {
        final String id, nameFa, nameEn, a, b, tagFa, tagEn;
        P(String id, String nameFa, String nameEn, String a, String b, String tagFa, String tagEn) {
            this.id = id; this.nameFa = nameFa; this.nameEn = nameEn;
            this.a = a; this.b = b; this.tagFa = tagFa; this.tagEn = tagEn;
        }
    }

    static final String DEFAULT_ID = "irk";

    static final P[] ALL = {
        new P("irk", "IR_Khatary", "IR_Khatary", "87.248.145.40", "193.242.208.98", "پیش‌فرض", "Default"),
        new P("shecan", "شکن", "Shecan", "178.22.122.100", "185.51.200.2", "ضدتحریم", "Anti-sanction"),
        new P("electro", "الکترو", "Electro", "78.157.42.100", "78.157.42.101", "گیمینگ", "Gaming"),
        new P("radar", "رادار گیم", "Radar Game", "10.202.10.10", "10.202.10.11", "گیمینگ", "Gaming"),
        new P("403", "۴۰۳", "403.online", "10.202.10.202", "10.202.10.102", "توسعه‌دهنده", "Developers"),
        new P("begzar", "بگذر", "Begzar", "185.55.226.26", "185.55.225.25", "ضدتحریم", "Anti-sanction"),
        new P("cloudflare", "Cloudflare", "Cloudflare", "1.1.1.1", "1.0.0.1", "سریع", "Fast"),
        new P("google", "Google", "Google", "8.8.8.8", "8.8.4.4", "پایدار", "Reliable"),
        new P("quad9", "Quad9", "Quad9", "9.9.9.9", "149.112.112.112", "امنیت", "Security"),
        new P("adguard", "AdGuard", "AdGuard", "94.140.14.14", "94.140.15.15", "بدون تبلیغ", "Ad-free"),
    };

    static P byId(String id) {
        for (P p : ALL) if (p.id.equals(id)) return p;
        return null;
    }
}
