package com.irkhatary.dns;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Single-screen native UI (no WebView / AndroidX, so it behaves the same on every phone). */
public class MainActivity extends Activity {
    private static final int REQ_VPN = 701, REQ_NOTIF = 702;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final int BG1 = 0xFF070B1A, BG2 = 0xFF0E1838, CARD = 0xFF0F1736, LINE = 0xFF222F63;
    private static final int TEXT = 0xFFF2F5FF, MUTED = 0xFF8E9AC9, ACCENT = 0xFF00E5A0, CYAN = 0xFF2BD4FF;
    private static final int RED = 0xFFFF5C7A, AMBER = 0xFFFFB020;

    /** Speed-test results survive rotation: id -> ms (-1 = no answer). */
    private static final HashMap<String, Integer> PINGS = new HashMap<>();

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(6);
    private final HashMap<String, TextView> badges = new HashMap<>();
    private final HashMap<String, View> rows = new HashMap<>(), radios = new HashMap<>();

    private ConnectButton btn;
    private TextView tvStatus, tvTimer, tvSub, tvQ, tvOk, tvAvg, tvTest;
    private View customBox;
    private EditText etA, etB;
    private boolean connecting, testing;
    private long connectDeadline;

    private interface OnToggle { void on(boolean v); }

    // ------------------------------------------------------------------ lifecycle

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Lang.fa = "fa".equals(Prefs.str(this, "lang", "fa"));
        getWindow().setStatusBarColor(BG1);
        getWindow().setNavigationBarColor(BG1);
        if (Prefs.str(this, "dns1", "").isEmpty()) {
            Presets.P d = Presets.byId(Presets.DEFAULT_ID);
            Prefs.put(this, "preset", d.id);
            Prefs.put(this, "dns1", d.a);
            Prefs.put(this, "dns2", d.b);
        }
        applyUpstream();
        DnsVpnService.cacheEnabled = Prefs.bool(this, "cache", true);
        render();
        if (Build.VERSION.SDK_INT >= 33 && !Prefs.bool(this, "asked_notif", false)
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Prefs.put(this, "asked_notif", true);
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() { refreshState(); ui.postDelayed(this, 500); }
    };

    @Override protected void onResume() {
        super.onResume();
        applyKeepScreen();
        ui.post(tick);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(tick);
        super.onPause();
    }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        pool.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ connect / disconnect

    private void onConnectClick() {
        if (DnsVpnService.running) { stopVpn(); return; }
        if (connecting) return;
        applyUpstream();
        try {
            Intent prep = VpnService.prepare(this);
            if (prep != null) startActivityForResult(prep, REQ_VPN);
            else startVpn();
        } catch (Exception e) {
            toast(Lang.t("این گوشی اجازه‌ی VPN نمی‌دهد", "VPN is not available on this device"));
        }
    }

    private void startVpn() {
        Intent i = new Intent(this, DnsVpnService.class).setAction(DnsVpnService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            connecting = true;
            connectDeadline = SystemClock.elapsedRealtime() + 6000;
        } catch (Exception e) {
            toast(Lang.t("اتصال برقرار نشد", "Could not connect"));
        }
        refreshState();
    }

    private void stopVpn() {
        stopService(new Intent(this, DnsVpnService.class));
        ui.postDelayed(new Runnable() { @Override public void run() { refreshState(); } }, 300);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_VPN) {
            if (res == RESULT_OK) startVpn();
            else toast(Lang.t("برای اتصال باید مجوز VPN را تأیید کنید", "Please allow the VPN permission to connect"));
        }
    }

    private void applyUpstream() {
        Presets.P d = Presets.byId(Presets.DEFAULT_ID);
        DnsVpnService.setUpstream(Prefs.str(this, "dns1", d.a), Prefs.str(this, "dns2", d.b));
    }

    private void applyKeepScreen() {
        if (Prefs.bool(this, "keep", false)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ------------------------------------------------------------------ state refresh

    private void refreshState() {
        if (btn == null) return;
        boolean on = DnsVpnService.running;
        if (on) connecting = false;
        if (connecting && SystemClock.elapsedRealtime() > connectDeadline) {
            connecting = false;
            toast(Lang.t("اتصال برقرار نشد. دوباره تلاش کنید.", "Connection failed. Please try again."));
        }
        btn.setState(on ? ConnectButton.ON : connecting ? ConnectButton.BUSY : ConnectButton.OFF);
        tvStatus.setText(on ? Lang.t("متصل شد", "Connected")
                : connecting ? Lang.t("در حال اتصال…", "Connecting…") : Lang.t("قطع است", "Disconnected"));
        tvStatus.setTextColor(on ? ACCENT : connecting ? AMBER : TEXT);

        if (on) {
            long s = Math.max(0, (SystemClock.elapsedRealtime() - DnsVpnService.startedAt) / 1000);
            tvTimer.setText(String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
        } else {
            tvTimer.setText("--:--:--");
        }

        Presets.P d = Presets.byId(Presets.DEFAULT_ID);
        String sel = Prefs.str(this, "preset", d.id);
        Presets.P cur = Presets.byId(sel);
        String name = cur != null ? Lang.t(cur.nameFa, cur.nameEn) : Lang.t("دلخواه", "Custom");
        tvSub.setText(name + "  •  " + Prefs.str(this, "dns1", d.a));

        long q = DnsVpnService.queries.get(), a = DnsVpnService.answered.get();
        tvQ.setText(on ? String.valueOf(q) : "—");
        tvOk.setText(on && q > 0 ? (a * 100 / q) + "%" : "—");
        tvAvg.setText(on && a > 0 ? (DnsVpnService.latencySum.get() / a) + " ms" : "—");
    }

    // ------------------------------------------------------------------ UI building

    private void render() {
        badges.clear(); rows.clear(); radios.clear();

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG2, BG1}));
        root.setLayoutDirection(Lang.fa ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        LinearLayout col = vbox();
        col.setPadding(dp(18), dp(12), dp(18), dp(28));
        sv.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));
        root.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets in) {
                v.setPadding(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(),
                        in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
                return in;
            }
        });

        col.addView(header());
        col.addView(hero(), lp(MATCH, WRAP, 0, 14, 0, 0));
        col.addView(statsRow(), lp(MATCH, WRAP, 0, 10, 0, 0));

        col.addView(sectionTitle(Lang.t("انتخاب DNS", "Choose DNS")), lp(WRAP, WRAP, 4, 20, 4, 8));
        for (Presets.P p : Presets.ALL) col.addView(presetRow(p), lp(MATCH, WRAP, 0, 0, 0, 8));
        col.addView(customCard(), lp(MATCH, WRAP, 0, 0, 0, 0));
        col.addView(testButton(), lp(MATCH, WRAP, 0, 12, 0, 0));

        col.addView(sectionTitle(Lang.t("تنظیمات", "Settings")), lp(WRAP, WRAP, 4, 22, 4, 8));
        col.addView(settingsCard());
        col.addView(tipCard(), lp(MATCH, WRAP, 0, 12, 0, 0));
        col.addView(footer(), lp(MATCH, WRAP, 0, 18, 0, 0));

        setContentView(root);
        root.requestApplyInsets();
        refreshSelection();
        for (Presets.P p : Presets.ALL) updateBadge(p.id);
        refreshState();
    }

    private View header() {
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout t = vbox();
        t.addView(tv("IR_Khatary DNS", 22, TEXT, true));
        t.addView(tv(Lang.t("DNS سریع و خصوصی برای همه‌ی گوشی‌ها", "Fast, private DNS for every phone"), 12, MUTED, false));
        row.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView chip = tv(Lang.fa ? "EN" : "فارسی", 12, CYAN, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(7), dp(14), dp(7));
        chip.setBackground(shape(0x142BD4FF, 16, CYAN, 1));
        chip.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            Lang.fa = !Lang.fa;
            Prefs.put(MainActivity.this, "lang", Lang.fa ? "fa" : "en");
            render();
        }});
        row.addView(chip);
        return row;
    }

    private View hero() {
        LinearLayout c = card();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        btn = new ConnectButton(this);
        btn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onConnectClick(); } });
        c.addView(btn, new LinearLayout.LayoutParams(dp(210), dp(210)));
        tvStatus = tv("", 21, TEXT, true);
        tvStatus.setGravity(Gravity.CENTER);
        c.addView(tvStatus, lp(WRAP, WRAP, 0, 2, 0, 0));
        tvTimer = tv("--:--:--", 15, MUTED, false);
        tvTimer.setTypeface(Typeface.MONOSPACE);
        c.addView(tvTimer, lp(WRAP, WRAP, 0, 4, 0, 0));
        tvSub = tv("", 12, MUTED, false);
        tvSub.setTextDirection(View.TEXT_DIRECTION_LTR);
        c.addView(tvSub, lp(WRAP, WRAP, 0, 6, 0, 0));
        return c;
    }

    private View statsRow() {
        LinearLayout row = hbox();
        tvQ = tv("—", 18, TEXT, true);
        tvOk = tv("—", 18, TEXT, true);
        tvAvg = tv("—", 18, TEXT, true);
        row.addView(statTile(tvQ, Lang.t("درخواست", "Queries")), new LinearLayout.LayoutParams(0, WRAP, 1f));
        row.addView(spacer(8));
        row.addView(statTile(tvOk, Lang.t("پاسخ موفق", "Answered")), new LinearLayout.LayoutParams(0, WRAP, 1f));
        row.addView(spacer(8));
        row.addView(statTile(tvAvg, Lang.t("میانگین تأخیر", "Avg. latency")), new LinearLayout.LayoutParams(0, WRAP, 1f));
        return row;
    }

    private View statTile(TextView value, String label) {
        LinearLayout c = card();
        c.setPadding(dp(8), dp(12), dp(8), dp(12));
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        value.setGravity(Gravity.CENTER);
        c.addView(value);
        TextView l = tv(label, 11, MUTED, false);
        l.setGravity(Gravity.CENTER);
        c.addView(l, lp(WRAP, WRAP, 0, 3, 0, 0));
        return c;
    }

    private View presetRow(final Presets.P p) {
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        rows.put(p.id, row);

        View radio = new View(this);
        radios.put(p.id, radio);
        row.addView(radio, new LinearLayout.LayoutParams(dp(20), dp(20)));
        row.addView(spacer(12));

        LinearLayout mid = vbox();
        LinearLayout nameLine = hbox();
        nameLine.setGravity(Gravity.CENTER_VERTICAL);
        nameLine.addView(tv(Lang.t(p.nameFa, p.nameEn), 15, TEXT, true));
        nameLine.addView(spacer(8));
        TextView tag = tv(Lang.t(p.tagFa, p.tagEn), 10, CYAN, false);
        tag.setPadding(dp(7), dp(2), dp(7), dp(2));
        tag.setBackground(shape(0x1A2BD4FF, 8, 0, 0));
        nameLine.addView(tag);
        mid.addView(nameLine);
        TextView addr = tv(p.a + "  •  " + p.b, 11, MUTED, false);
        addr.setTextDirection(View.TEXT_DIRECTION_LTR);
        mid.addView(addr, lp(WRAP, WRAP, 0, 3, 0, 0));
        row.addView(mid, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView badge = tv("—", 12, MUTED, true);
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(56));
        badge.setPadding(dp(8), dp(5), dp(8), dp(5));
        badge.setBackground(shape(0xFF0A1030, 10, 0, 0));
        badges.put(p.id, badge);
        row.addView(badge);

        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { select(p); } });
        return row;
    }

    private void select(Presets.P p) {
        Prefs.put(this, "preset", p.id);
        Prefs.put(this, "dns1", p.a);
        Prefs.put(this, "dns2", p.b);
        applyUpstream();
        refreshSelection();
        refreshState();
    }

    private void refreshSelection() {
        String sel = Prefs.str(this, "preset", Presets.DEFAULT_ID);
        for (Presets.P p : Presets.ALL) {
            boolean on = p.id.equals(sel);
            View r = rows.get(p.id), rd = radios.get(p.id);
            if (r != null) r.setBackground(shape(on ? 0xFF0C2A3A : CARD, 16, on ? ACCENT : LINE, on ? 1.5f : 1));
            if (rd != null) rd.setBackground(radioShape(on));
        }
        if (customBox != null) {
            boolean on = "custom".equals(sel);
            customBox.setBackground(shape(on ? 0xFF0C2A3A : CARD, 18, on ? ACCENT : LINE, on ? 1.5f : 1));
        }
    }

    private void updateBadge(String id) {
        TextView b = badges.get(id);
        if (b == null) return;
        Integer ms = PINGS.get(id);
        if (ms == null) { b.setText("—"); b.setTextColor(MUTED); }
        else if (ms < 0) { b.setText("×"); b.setTextColor(RED); }
        else { b.setText(ms + " ms"); b.setTextColor(ms < 80 ? ACCENT : ms < 200 ? AMBER : RED); }
    }

    private View customCard() {
        LinearLayout c = card();
        customBox = c;
        c.addView(tv(Lang.t("DNS دلخواه", "Custom DNS"), 15, TEXT, true));
        c.addView(tv(Lang.t("آدرس IPv4 یا IPv6 را وارد کنید", "Enter an IPv4 or IPv6 address"), 11, MUTED, false), lp(WRAP, WRAP, 0, 2, 0, 8));
        etA = field(Lang.t("اصلی  (مثلاً 1.1.1.1)", "Primary (e.g. 1.1.1.1)"), Prefs.str(this, "cdns1", ""));
        etB = field(Lang.t("ثانویه (اختیاری)", "Secondary (optional)"), Prefs.str(this, "cdns2", ""));
        c.addView(etA, lp(MATCH, WRAP, 0, 0, 0, 8));
        c.addView(etB, lp(MATCH, WRAP, 0, 0, 0, 10));
        TextView use = tv(Lang.t("استفاده از این DNS", "Use this DNS"), 14, BG1, true);
        use.setGravity(Gravity.CENTER);
        use.setPadding(dp(12), dp(11), dp(12), dp(11));
        use.setBackground(shape(ACCENT, 12, 0, 0));
        use.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { useCustom(); } });
        c.addView(use, new LinearLayout.LayoutParams(MATCH, WRAP));
        return c;
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(TEXT);
        e.setHintTextColor(0xFF5C688F);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setKeyListener(DigitsKeyListener.getInstance("0123456789abcdefABCDEF.:"));
        e.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        e.setTextDirection(View.TEXT_DIRECTION_LTR);
        e.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(shape(0xFF0A1030, 12, LINE, 1));
        return e;
    }

    private void useCustom() {
        String a = etA.getText().toString().trim(), b = etB.getText().toString().trim();
        if (DnsTester.parseIp(a) == null) { toast(Lang.t("DNS اصلی معتبر نیست", "Primary DNS is not a valid IP address")); return; }
        if (!b.isEmpty() && DnsTester.parseIp(b) == null) { toast(Lang.t("DNS ثانویه معتبر نیست", "Secondary DNS is not a valid IP address")); return; }
        Prefs.put(this, "cdns1", a);
        Prefs.put(this, "cdns2", b);
        Prefs.put(this, "preset", "custom");
        Prefs.put(this, "dns1", a);
        Prefs.put(this, "dns2", b);
        applyUpstream();
        refreshSelection();
        refreshState();
        etA.clearFocus(); etB.clearFocus();
        toast(Lang.t("DNS دلخواه ذخیره شد", "Custom DNS saved"));
    }

    private View testButton() {
        tvTest = tv("", 14, CYAN, true);
        tvTest.setGravity(Gravity.CENTER);
        tvTest.setPadding(dp(12), dp(13), dp(12), dp(13));
        tvTest.setBackground(shape(0x142BD4FF, 14, CYAN, 1));
        tvTest.setText(testing ? Lang.t("در حال تست…", "Testing…") : Lang.t("تست سرعت و انتخاب سریع‌ترین", "Speed test & pick the fastest"));
        tvTest.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { runTest(); } });
        return tvTest;
    }

    private void runTest() {
        if (testing) return;
        testing = true;
        PINGS.clear();
        for (Presets.P p : Presets.ALL) updateBadge(p.id);
        tvTest.setText(Lang.t("در حال تست…", "Testing…"));
        final AtomicInteger left = new AtomicInteger(Presets.ALL.length);
        for (final Presets.P p : Presets.ALL) {
            pool.execute(new Runnable() { @Override public void run() {
                int ms = DnsTester.ping(p.a, 1200);
                if (ms < 0) ms = DnsTester.ping(p.b, 1200);
                final int result = ms;
                final boolean last = left.decrementAndGet() == 0;
                runOnUiThread(new Runnable() { @Override public void run() {
                    PINGS.put(p.id, result);
                    updateBadge(p.id);
                    if (last) finishTest();
                }});
            }});
        }
    }

    private void finishTest() {
        testing = false;
        if (isFinishing()) return;
        if (tvTest != null) tvTest.setText(Lang.t("تست سرعت و انتخاب سریع‌ترین", "Speed test & pick the fastest"));
        Presets.P best = null;
        int bestMs = Integer.MAX_VALUE;
        for (Presets.P p : Presets.ALL) {
            Integer ms = PINGS.get(p.id);
            if (ms != null && ms > 0 && ms < bestMs) { bestMs = ms; best = p; }
        }
        if (best == null) {
            toast(Lang.t("هیچ سروری پاسخ نداد. اینترنت را بررسی کنید.", "No server answered. Check your internet connection."));
            return;
        }
        select(best);
        toast(Lang.t("سریع‌ترین: ", "Fastest: ") + Lang.t(best.nameFa, best.nameEn) + " (" + bestMs + " ms)");
    }

    private View settingsCard() {
        LinearLayout c = card();
        c.addView(toggleRow(Lang.t("روشن ماندن صفحه", "Keep screen on"),
                Lang.t("وقتی برنامه باز است (مناسب بازی)", "While the app is open (handy for gaming)"),
                Prefs.bool(this, "keep", false), new OnToggle() { @Override public void on(boolean v) {
                    Prefs.put(MainActivity.this, "keep", v); applyKeepScreen(); } }));
        c.addView(divider());
        c.addView(toggleRow(Lang.t("اتصال خودکار بعد از روشن شدن گوشی", "Connect after phone restarts"),
                Lang.t("فقط اگر قبلاً مجوز VPN را داده باشید", "Only if VPN permission was already granted"),
                Prefs.bool(this, "boot", false), new OnToggle() { @Override public void on(boolean v) {
                    Prefs.put(MainActivity.this, "boot", v); } }));
        c.addView(divider());
        c.addView(toggleRow(Lang.t("حافظه‌ی نهان DNS", "DNS cache"),
                Lang.t("پاسخ‌های تکراری سریع‌تر می‌شوند", "Repeated lookups resolve faster"),
                Prefs.bool(this, "cache", true), new OnToggle() { @Override public void on(boolean v) {
                    Prefs.put(MainActivity.this, "cache", v); DnsVpnService.cacheEnabled = v; } }));
        return c;
    }

    private View toggleRow(String title, String sub, boolean init, final OnToggle cb) {
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        LinearLayout txt = vbox();
        txt.addView(tv(title, 14, TEXT, true));
        txt.addView(tv(sub, 11, MUTED, false), lp(WRAP, WRAP, 0, 2, 0, 0));
        row.addView(txt, new LinearLayout.LayoutParams(0, WRAP, 1f));
        row.addView(spacer(12));

        final FrameLayout track = new FrameLayout(this);
        track.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        final View thumb = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.WHITE);
        thumb.setBackground(g);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(dp(20), dp(20));
        tl.setMargins(dp(3), 0, dp(3), 0);
        track.addView(thumb, tl);
        row.addView(track, new LinearLayout.LayoutParams(dp(46), dp(26)));

        final boolean[] st = {init};
        final Runnable paint = new Runnable() { @Override public void run() {
            track.setBackground(shape(st[0] ? ACCENT : 0xFF2A3466, 13, 0, 0));
            FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) thumb.getLayoutParams();
            l.gravity = Gravity.CENTER_VERTICAL | (st[0] ? Gravity.RIGHT : Gravity.LEFT);
            thumb.setLayoutParams(l);
        }};
        paint.run();
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            st[0] = !st[0];
            paint.run();
            cb.on(st[0]);
        }});
        return row;
    }

    private View tipCard() {
        LinearLayout c = card();
        c.addView(tv(Lang.t("نکته‌های مهم", "Good to know"), 14, AMBER, true));
        TextView t = tv(Lang.t(
                "• اگر «Private DNS / DNS خصوصی» گوشی روی نام میزبان (hostname) تنظیم شده، آن را روی «خودکار» یا «خاموش» بگذارید.\n"
              + "• DNS فقط سرعت پیدا کردن آدرس سرورها را تغییر می‌دهد؛ تأثیر مستقیم روی پینگ بازی تضمین‌شده نیست.\n"
              + "• ترافیک شما از این برنامه عبور نمی‌کند؛ فقط درخواست‌های DNS.",
                "• If your phone's Private DNS is set to a hostname, switch it to Automatic or Off.\n"
              + "• DNS only changes how names are looked up; it does not guarantee a lower game ping.\n"
              + "• Your traffic does not pass through this app — only DNS lookups do."), 12, MUTED, false);
        t.setLineSpacing(dp(3), 1f);
        c.addView(t, lp(MATCH, WRAP, 0, 6, 0, 10));
        TextView open = tv(Lang.t("باز کردن تنظیمات شبکه", "Open network settings"), 13, CYAN, true);
        open.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            try { startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); }
            catch (Exception e) { try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) { } }
        }});
        c.addView(open);
        return c;
    }

    private View footer() {
        LinearLayout f = vbox();
        f.setGravity(Gravity.CENTER_HORIZONTAL);
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        TextView v = tv("IR_Khatary DNS  v" + ver, 11, MUTED, false);
        v.setGravity(Gravity.CENTER);
        f.addView(v);
        TextView tg = tv("t.me/IR_Khatary_DNS", 12, CYAN, true);
        tg.setGravity(Gravity.CENTER);
        tg.setTextDirection(View.TEXT_DIRECTION_LTR);
        tg.setPadding(dp(10), dp(8), dp(10), dp(8));
        tg.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/IR_Khatary_DNS"))); }
            catch (Exception ignored) { }
        }});
        f.addView(tg);
        return f;
    }

    // ------------------------------------------------------------------ small view helpers

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable shape(int fill, float radiusDp, int stroke, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return g;
    }

    private GradientDrawable radioShape(boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? ACCENT : Color.TRANSPARENT);
        g.setStroke(dp(2), on ? ACCENT : LINE);
        return g;
    }

    private TextView tv(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView sectionTitle(String s) { return tv(s, 13, MUTED, true); }

    private LinearLayout vbox() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout hbox() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }

    private LinearLayout card() {
        LinearLayout c = vbox();
        c.setBackground(shape(CARD, 18, LINE, 1));
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        return c;
    }

    private View spacer(int widthDp) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), 1)); return v; }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(MATCH, 1));
        return v;
    }

    private LinearLayout.LayoutParams lp(int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
