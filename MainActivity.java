package com.example.batteryguard;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {

    static final String CMD =
        "adb shell pm grant com.example.batteryguard android.permission.WRITE_SECURE_SETTINGS";

    SharedPreferences sp;
    LinearLayout root;
    TextView perm, opt, thLabel, res;
    Button copy, optBtn;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("g", MODE_PRIVATE);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, dp(40), p, p);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF121212);
        sv.addView(root);
        setContentView(sv);

        text("Battery Guard", 24);
        perm = text("", 14);
        copy = button("Copy ADB command", v -> ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
            .setPrimaryClip(ClipData.newPlainText("adb", CMD)));
        opt = text("", 14);
        optBtn = button("Allow background running", v -> startActivity(
            new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()))));

        Switch master = sw("Enable Battery Guard", "on", false);
        master.setOnCheckedChangeListener((v, on) -> {
            sp.edit().putBoolean("on", on).putBoolean("fired", false).apply();
            if (on) startGuard(); else stopService(new Intent(this, GuardService.class));
        });

        int th = sp.getInt("th", 20);
        thLabel = text("Trigger at or below: " + th + "%", 16);
        SeekBar sb = new SeekBar(this);
        sb.setMax(45);
        sb.setProgress(th - 5);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean u) {
                thLabel.setText("Trigger at or below: " + (v + 5) + "%");
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {
                sp.edit().putInt("th", s.getProgress() + 5).putBoolean("fired", false).apply();
            }
        });
        root.addView(sb);

        text("Actions", 16);
        sw("Mobile data off", "data", true);
        sw("Location off", "loc", true);
        sw("Battery saver on", "saver", true);
        sw("Wi-Fi off", "wifi", false);
        sw("Bluetooth off", "bt", false);
        Switch dim = sw("Lower brightness", "dim", false);
        dim.setOnCheckedChangeListener((v, on) -> {
            sp.edit().putBoolean("dim", on).apply();
            if (on && !Settings.System.canWrite(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
            }
        });

        button("Test Actions Now", v -> res.setText(GuardService.run(this)));
        res = text("", 14);

        if (sp.getBoolean("on", false)) startGuard();
    }

    @Override protected void onResume() {
        super.onResume();
        boolean g = checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
            == PackageManager.PERMISSION_GRANTED;
        perm.setText(g ? "WRITE_SECURE_SETTINGS: granted ✓"
            : "WRITE_SECURE_SETTINGS: NOT granted ✗\n" + CMD
              + "\n(LADB / Termux: leave out 'adb shell')");
        perm.setTextColor(g ? 0xFF66BB6A : 0xFFEF5350);
        copy.setVisibility(g ? View.GONE : View.VISIBLE);

        boolean o = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        opt.setText(o ? "Battery optimization: ignored ✓" : "Battery optimization: active ✗");
        opt.setTextColor(o ? 0xFF66BB6A : 0xFFFFA726);
        optBtn.setVisibility(o ? View.GONE : View.VISIBLE);
    }

    void startGuard() { startForegroundService(new Intent(this, GuardService.class)); }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    TextView text(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(0xFFEEEEEE);
        t.setPadding(0, dp(8), 0, dp(4));
        root.addView(t);
        return t;
    }

    Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setOnClickListener(l);
        root.addView(b);
        return b;
    }

    Switch sw(String label, String key, boolean def) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextSize(16);
        s.setTextColor(0xFFEEEEEE);
        s.setPadding(0, dp(10), 0, dp(10));
        s.setChecked(sp.getBoolean(key, def));
        s.setOnCheckedChangeListener((v, on) -> sp.edit().putBoolean(key, on).apply());
        root.addView(s);
        return s;
    }
}
