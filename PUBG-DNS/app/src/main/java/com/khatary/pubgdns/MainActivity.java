package com.khatary.pubgdns;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
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
    private boolean requested = false;
    private final String[] servers = {"Cloudflare", "Google Public DNS", "Quad9", "OpenDNS"};
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
        title.setText("PUBG DNS"); title.setTextSize(30); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(100, 190, 255)); title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(62)));

        TextView subtitle = new TextView(this);
        subtitle.setText("DNS profile selector"); subtitle.setTextSize(14); subtitle.setTextColor(Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER); root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView label = new TextView(this);
        label.setText("انتخاب سرویس DNS"); label.setTextSize(16); label.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44)); lp.topMargin = dp(28); root.addView(label, lp);

        serverSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, servers);
        serverSpinner.setAdapter(adapter);
        serverSpinner.setSelection(Math.max(0, Math.min(servers.length - 1, prefs.getInt("server", 0))));
        root.addView(serverSpinner, new LinearLayout.LayoutParams(-1, dp(52)));

        connectButton = new Button(this); connectButton.setText("شروع اتصال");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(54)); bp.topMargin = dp(26); root.addView(connectButton, bp);
        connectButton.setOnClickListener(v -> requestVpn());

        TextView note = new TextView(this);
        note.setText("این نسخه آزمایشی است؛ تا تکمیل و آزمایش انتقال واقعی بسته‌های DNS، اتصال یا کاهش پینگ تضمین نمی‌شود.");
        note.setTextSize(12); note.setTextColor(Color.rgb(180, 185, 205)); note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2); np.topMargin = dp(24); root.addView(note, np);
        setContentView(root);
    }

    private void requestVpn() {
        prefs.edit().putInt("server", serverSpinner.getSelectedItemPosition()).apply();
        Intent intent = VpnService.prepare(this);
        if (intent != null) startActivityForResult(intent, VPN_REQUEST);
        else onActivityResult(VPN_REQUEST, RESULT_OK, null);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST && resultCode == RESULT_OK) {
            Toast.makeText(this, "مجوز VPN دریافت شد؛ سرویس انتقال DNS هنوز در مرحله توسعه است.", Toast.LENGTH_LONG).show();
        } else if (requestCode == VPN_REQUEST) {
            Toast.makeText(this, "مجوز اتصال داده نشد.", Toast.LENGTH_SHORT).show();
        }
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
