package com.example.batteryguard;

import android.app.*;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.provider.Settings;

@SuppressWarnings("deprecation")
public class GuardService extends Service {

    interface Op { boolean go() throws Exception; }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (lv < 0 || sc <= 0) return;
            lv = lv * 100 / sc;
            boolean charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            SharedPreferences sp = c.getSharedPreferences("g", MODE_PRIVATE);
            if (charging || lv > sp.getInt("th", 20)) {      // re-arm
                sp.edit().putBoolean("fired", false).apply();
                return;
            }
            if (!sp.getBoolean("fired", false)) {             // fire once per drop
                sp.edit().putBoolean("fired", true).apply();
                run(c);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(
            new NotificationChannel("guard", "Battery Guard", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "guard")
            .setContentTitle("Battery Guard Active")
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setOngoing(true)
            .build();
        startForeground(1, n);
        registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }

    @Override public void onDestroy() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    static String step(String name, Op op) {
        try {
            return (op.go() ? "✓ " : "✗ ") + name + "\n";
        } catch (Exception e) {
            return "✗ " + name + ": " + e.getClass().getSimpleName() + "\n";
        }
    }

    static String run(Context ctx) {
        final Context c = ctx.getApplicationContext();
        final ContentResolver cr = c.getContentResolver();
        SharedPreferences sp = c.getSharedPreferences("g", MODE_PRIVATE);
        StringBuilder o = new StringBuilder();

        if (sp.getBoolean("data", true))
            o.append(step("Mobile data off", () -> Settings.Global.putInt(cr, "mobile_data", 0)));
        if (sp.getBoolean("loc", true))
            o.append(step("Location off", () -> Settings.Secure.putInt(cr, "location_mode", 0)));
        if (sp.getBoolean("saver", true))
            o.append(step("Battery saver on", () -> Settings.Global.putInt(cr, "low_power", 1)));
        if (sp.getBoolean("wifi", false))
            o.append(step("Wi-Fi off", () ->
                ((WifiManager) c.getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(false)));
        if (sp.getBoolean("bt", false))
            o.append(step("Bluetooth off", () -> {
                BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
                return a == null || !a.isEnabled() || a.disable();
            }));
        if (sp.getBoolean("dim", false))
            o.append(step("Brightness low", () -> {
                if (!Settings.System.canWrite(c)) return false;
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                return Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 30);
            }));

        return o.length() == 0 ? "No actions selected." : o.toString().trim();
    }

    public static class Boot extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())
                && c.getSharedPreferences("g", MODE_PRIVATE).getBoolean("on", false)) {
                c.startForegroundService(new Intent(c, GuardService.class));
            }
        }
    }
}
