package com.matias.claudeusage;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.time.ZonedDateTime;

/** Tiempo libre: una alarma del sistema al empezar tu franja (sin consultar cada rato). */
public class Reminders extends BroadcastReceiver {
    static final int ID_FREE = 9;

    @Override
    public void onReceive(Context c, Intent i) {
        Prefs.migrate(c);
        SharedPreferences p = Prefs.get(c);
        if (p.getBoolean("freeOn", false)) {
            long today = java.time.LocalDate.now().toEpochDay();
            if (p.getLong("freeDay", -1) != today) {
                p.edit().putLong("freeDay", today).apply();
                notifyFree(c);
            }
        }
        schedule(c);
    }

    /** Programa (o cancela) la próxima alarma de la franja. */
    static void schedule(Context c) {
        SharedPreferences p = Prefs.get(c);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 11, new Intent(c, Reminders.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        if (!p.getBoolean("freeOn", false)) return;
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime at = now.withHour(p.getInt("freeStart", 19)).withMinute(0).withSecond(0).withNano(0);
        if (!at.isAfter(now)) at = at.plusDays(1);
        // Ventana de 10 min: no necesita el permiso de alarmas exactas
        am.setWindow(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), 10 * 60_000L, pi);
    }

    /** ¿Ahora estás dentro de tu franja libre? */
    static boolean inFree(Context c) {
        SharedPreferences p = Prefs.get(c);
        if (!p.getBoolean("freeOn", false)) return false;
        int a = p.getInt("freeStart", 19), b = p.getInt("freeEnd", 23), h = java.time.LocalTime.now().getHour();
        return a < b ? h >= a && h < b : h >= a || h < b;
    }

    /** Texto del panel "Esta noche": margen, reinicio y pendientes. */
    static String summary(Context c) {
        SharedPreferences p = Prefs.get(c);
        Usage u = Usage.load(c);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(java.util.Locale.US, L.t("Tu franja: %02d–%02d h"), p.getInt("freeStart", 19), p.getInt("freeEnd", 23)));
        if (u.hasData()) {
            sb.append('\n');
            if (u.weekOut()) sb.append(u.weekBackLine());
            else sb.append(L.t("Sesión con ")).append(100 - u.pct).append(L.t("% libre")).append(" · ")
                    .append(u.reset > 0 ? L.t("reinicia ") + Usage.clock(u.reset) : L.t("Sin sesión activa"))
                    .append(" · ").append(L.t("Semana ")).append(u.week).append('%');
        }
        String top = Todos.top(c, 3);
        if (!top.isEmpty()) sb.append('\n').append(top);
        return sb.toString();
    }

    static void notifyFree(Context c) {
        UsageService.channels(c);
        Usage u = Usage.load(c);
        int open = Todos.open(c);
        String text = !u.hasData() ? L.t("Abrí la app para ver tu uso")
                : u.weekOut() ? u.weekBackLine()
                : L.t("Sesión con ") + (100 - u.pct) + L.t("% libre") + " · " + L.t("Semana ") + u.week + "%";
        if (open > 0) text += "\n" + open + L.t(" pendientes: ") + Todos.tags(c);
        Notification n = new Notification.Builder(c, UsageService.reminderChannel(c))
                .setSmallIcon(R.drawable.ic_stat).setContentTitle(L.t("Empieza tu tiempo libre"))
                .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                .setColor(Theme.widget(c).accent).setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(c, 12, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
                .build();
        c.getSystemService(NotificationManager.class).notify(ID_FREE, n);
    }
}
