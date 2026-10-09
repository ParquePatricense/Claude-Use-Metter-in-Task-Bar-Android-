package com.matias.claudeusage;

import android.app.AutomaticZenRule;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.service.notification.Condition;

/**
 * Integración con los modos del teléfono y apps de automatización.
 * - Crea el modo "Concentración Claude" (No molestar) que la app activa al llegar al umbral y apaga al reiniciarse la sesión.
 * - Emite el aviso com.matias.claudeusage.EVENT para Tasker, MacroDroid y similares.
 */
final class Focus {
    private static final Uri COND = Uri.parse("condition://com.matias.claudeusage/focus");
    static final String EVENT = "com.matias.claudeusage.EVENT";

    static boolean hasAccess(Context c) {
        return c.getSystemService(NotificationManager.class).isNotificationPolicyAccessGranted();
    }

    private static String ruleId(Context c) {
        if (Build.VERSION.SDK_INT < 29 || !hasAccess(c)) return null;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        String id = Prefs.get(c).getString("zenRule", null);
        if (id != null && nm.getAutomaticZenRule(id) != null) return id;
        AutomaticZenRule rule = new AutomaticZenRule("Concentración Claude", null,
                new ComponentName(c, MainActivity.class), COND, null,
                NotificationManager.INTERRUPTION_FILTER_PRIORITY, true);
        id = nm.addAutomaticZenRule(rule);
        Prefs.get(c).edit().putString("zenRule", id).apply();
        return id;
    }

    /** Activa el modo (si el usuario lo habilitó y dio el permiso). */
    static void on(Context c, String why) {
        if (!Prefs.get(c).getBoolean("focusMode", false) || Build.VERSION.SDK_INT < 29) return;
        try {
            String id = ruleId(c);
            if (id != null) c.getSystemService(NotificationManager.class)
                    .setAutomaticZenRuleState(id, new Condition(COND, why, Condition.STATE_TRUE));
        } catch (Exception ignored) {}
    }

    static void off(Context c) {
        if (Build.VERSION.SDK_INT < 29) return;
        try {
            String id = Prefs.get(c).getString("zenRule", null);
            if (id != null && hasAccess(c)) c.getSystemService(NotificationManager.class)
                    .setAutomaticZenRuleState(id, new Condition(COND, "Sesión reiniciada", Condition.STATE_FALSE));
        } catch (Exception ignored) {}
    }

    /** Aviso para apps de automatización: event = level75 | level90 | limit | reset. */
    static void broadcast(Context c, String event, int pct) {
        if (!Prefs.get(c).getBoolean("broadcast", true)) return;
        c.sendBroadcast(new Intent(EVENT).putExtra("event", event).putExtra("pct", pct));
    }
}
