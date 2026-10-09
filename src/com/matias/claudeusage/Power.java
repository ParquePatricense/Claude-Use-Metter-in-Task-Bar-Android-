package com.matias.claudeusage;

import android.content.Context;
import android.os.BatteryManager;
import android.os.PowerManager;

/** Modo ahorro: batería baja (sin cargar) o ahorro de energía del sistema. */
final class Power {
    private static long checkedAt;
    private static boolean low;

    static boolean saving(Context c) {
        if (!Prefs.get(c).getBoolean("autoSave", true)) return false;
        long now = System.currentTimeMillis();
        if (now - checkedAt < 30_000) return low;
        checkedAt = now;
        try {
            BatteryManager bm = c.getSystemService(BatteryManager.class);
            int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            boolean charging = bm.isCharging();
            boolean sysSaver = c.getSystemService(PowerManager.class).isPowerSaveMode();
            low = sysSaver || (!charging && level > 0 && level < Prefs.get(c).getInt("saveAt", 20));
        } catch (Exception e) {
            low = false;
        }
        return low;
    }
}
