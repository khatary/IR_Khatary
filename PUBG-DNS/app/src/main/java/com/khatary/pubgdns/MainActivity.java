package com.khatary.pubgdns;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int VPN_REQUEST = 301;
    private Spinner serverSpinner;
    private Button connectButton;
    private TextView statusText;
    private final String[] servers = {"Cloudflare DNS", "Google DNS", "Quad9 DNS", "OpenDNS"};
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("pubg_dns", MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.rgb(10, 12, 28));
        getWindow().setNavigationBarColor(Color.rgb(10, 12, 28));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(30), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(10, 12, 28));

        TextView title = new TextView(this);
        title.setText("PUBG DNS");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(100, 190, 255));
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(62)));

        TextView subtitle = new TextView(this);
        subtitle.setText("IR_Khatary • DNS Tunnel");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(42)));

        statusText = new TextView(this);
        statusText.setTextSize(16);
        statusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusText.setTextColor(Color.rgb(255, 190, 90));
        statusText.setGravity(Gravity.CENTER);
        statusText.setText(prefs.getBoolean("active", false) ? "وضعیت: در حال اتصال" : "وضعیت: قطع");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(54));
        sp.topMargin = dp(18);
        root.addView(statusText, sp);

        TextView label = new TextView(this);
        label.setText("انتخاب سرویس DNS");
        label.setTextSize(16);
        label.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.topMargin = dp(18);
        root.addView(label, lp);

        serverSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, servers);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        serverSpinner.setAdapter(adapter);
        serverSpinner.setSelection(Math.max(0, Math.min(servers.length - 1, prefs.getInt("server", 0))));
        root.addView(serverSpinner, new LinearLayout.LayoutParams(-1, dp(52)));

        connectButton = new Button(this);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(54));
        bp.topMargin = dp(26);
        root.addView(connectButton, bp);
        refreshButton();
        connectButton.setOnClickListener(v -> toggleConnection());

        TextView note = new TextView(this);
        note.setText("این برنامه فقط درخواست‌های DNS را از تونل عبور می‌دهد؛ ترافیک بازی را VPN نمی‌کند و کاهش پینگ تضمین‌شده نیست. عملکرد باید روی شبکه واقعی آزمایش شود.");
        note.setTextSize(12);
        note.setTextColor(Color.rgb(180, 185, 205));
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(24);
        root.addView(note, np);
        setContentView(root);
    }

    private void toggleConnection() {
        if (prefs.getBoolean("active", false)) {
            Intent stop = new Intent(this, DnsVpnService.class);
            stop.setAction(DnsVpnService.ACTION_STOP);
            startService(stop);
            prefs.edit().putBoolean("active", false).apply();
            statusText.setText("وضعیت: قطع");
            statusText.setTextColor(Color.rgb(255, 190, 90));
            refreshButton();
            return;
        }
        prefs.edit().putInt("server", serverSpinner.getSelectedItemPosition()).apply();
        Intent permissionIntent = VpnService.prepare(this);
        if (permissionIntent != null) {
            startActivityForResult(permissionIntent, VPN_REQUEST);
        } else {
            startTunnel();
        }
    }

    private void startTunnel() {
        Intent service = new Intent(this, DnsVpnService.class);
        service.setAction(DnsVpnService.ACTION_START);
        service.putExtra(DnsVpnService.EXTRA_SERVER, serverSpinner.getSelectedItemPosition());
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            prefs.edit().putBoolean("active", true).apply();
            statusText.setText("وضعیت: اتصال درخواست شد");
            statusText.setTextColor(Color.rgb(100, 220, 150));
            refreshButton();
        } catch (Exception e) {
            prefs.edit().putBoolean("active", false).apply();
            Toast.makeText(this, "شروع سرویس ناموفق بود؛ دوباره تلاش کن.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) startTunnel();
            else Toast.makeText(this, "مجوز VPN داده نشد.", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshButton() {
        connectButton.setText(prefs.getBoolean("active", false) ? "قطع اتصال" : "شروع اتصال");
    }

    @Override protected void onResume() {
        super.onResume();
        if (connectButton != null) refreshButton();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}