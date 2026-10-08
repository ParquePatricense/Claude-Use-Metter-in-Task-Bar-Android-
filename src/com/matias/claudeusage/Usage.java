package com.matias.claudeusage;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Último uso conocido + helpers de formato y dibujo compartidos. */
final class Usage {

    int pct = -1, week = -1;
    long reset, weekReset, updated;
    String error, extra;

    static Usage parse(JSONObject j) {
        JSONObject five = j.optJSONObject("five_hour");
        JSONObject seven = j.optJSONObject("seven_day");
        Usage u = new Usage();
        u.pct = five == null ? 0 : clamp(five.optDouble("utilization", 0));
        u.week = seven == null ? 0 : clamp(seven.optDouble("utilization", 0));
        u.reset = five == null ? 0 : iso(five.optString("resets_at", null));
        u.weekReset = seven == null ? 0 : iso(seven.optString("resets_at", null));
        u.updated = System.currentTimeMillis();
        // Límites por modelo, si el plan los informa
        StringBuilder ex = new StringBuilder();
        String[][] models = {{"seven_day_opus", "Opus"}, {"seven_day_sonnet", "Sonnet"}};
        for (String[] m : models) {
            JSONObject o = j.optJSONObject(m[0]);
            if (o != null && !o.isNull("utilization")) {
                if (ex.length() > 0) ex.append(" · ");
                ex.append(m[1]).append(" semana ").append(clamp(o.optDouble("utilization", 0))).append("%");
            }
        }
        u.extra = ex.length() > 0 ? ex.toString() : null;
        return u;
    }

    static Usage load(Context c) {
        SharedPreferences p = Prefs.get(c);
        Usage u = new Usage();
        u.pct = p.getInt("pct", -1);
        u.week = p.getInt("week", -1);
        u.reset = p.getLong("reset", 0);
        u.weekReset = p.getLong("weekReset", 0);
        u.updated = p.getLong("updated", 0);
        u.error = p.getString("error", null);
        u.extra = p.getString("extra", null);
        return u;
    }

    void save(Context c) {
        Prefs.get(c).edit().putInt("pct", pct).putInt("week", week).putLong("reset", reset)
                .putLong("weekReset", weekReset).putLong("updated", updated).putString("error", error).putString("extra", extra).apply();
    }

    boolean hasData() { return pct >= 0; }

    private static int clamp(double v) { return (int) Math.max(0, Math.min(100, Math.round(v))); }

    private static long iso(String s) {
        if (s == null || s.isEmpty() || "null".equals(s)) return 0;
        return OffsetDateTime.parse(s).toInstant().toEpochMilli();
    }

    static int color(int pct) { return Theme.cur.level(pct); }

    static String left(long t) {
        long m = Math.max(0, (t - System.currentTimeMillis()) / 60000);
        if (m >= 1440) return (m / 1440) + "d " + (m % 1440 / 60) + "h";
        if (m >= 60) return (m / 60) + "h " + (m % 60) + "m";
        return m + "m";
    }

    static String clock(long t) {
        return Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    static String dayClock(long t) {
        return Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEE d HH:mm", new Locale("es")));
    }

    String resetLine() {
        return reset > 0 ? "Reinicia " + clock(reset) + " (en " + left(reset) + ")" : "Sin sesión activa";
    }

    String sessionLine() { return "Sesión " + pct + "% · " + resetLine(); }

    String weekLine() {
        return "Semana " + week + "%" + (weekReset > 0 ? " · reinicia " + dayClock(weekReset) : "");
    }

    /** Cuenta regresiva con segundos: 4h 57m 12s. */
    static String leftSec(long t) {
        long s = Math.max(0, (t - System.currentTimeMillis()) / 1000);
        if (s >= 86400) return (s / 86400) + "d " + (s % 86400 / 3600) + "h";
        if (s >= 3600) return (s / 3600) + "h " + (s % 3600 / 60) + "m " + (s % 60) + "s";
        return (s / 60) + "m " + (s % 60) + "s";
    }
}
