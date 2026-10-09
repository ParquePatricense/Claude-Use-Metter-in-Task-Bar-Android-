package com.matias.claudeusage;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Muestras ts,sesión,semana,reinicio por cuenta (últimos 8 días) + estadísticas. */
final class History {
    static final long DAY = 24L * 3600 * 1000;
    static final long KEEP = 29 * DAY;
    private static final long MIN_GAP = 5 * 60 * 1000;

    static final class Point {
        final long ts, reset; final int pct, week;
        Point(long ts, int pct, int week, long reset) { this.ts = ts; this.pct = pct; this.week = week; this.reset = reset; }
    }

    static final class Day {
        LocalDate date; int peak; int sessions; int limits;
    }

    static final class Stats {
        List<Day> days = new ArrayList<Day>();
        int avgPeak = -1, sessions, limits, topHour = -1, streak;
    }

    static File file(Context c) { return new File(c.getFilesDir(), "history_" + Prefs.accountId(c) + ".csv"); }

    static synchronized void add(Context c, Usage u) {
        List<Point> all = load(c, 0);
        Point last = all.isEmpty() ? null : all.get(all.size() - 1);
        if (last != null && last.pct == u.pct && last.week == u.week && u.updated - last.ts < MIN_GAP) return;
        String line = u.updated + "," + u.pct + "," + u.week + "," + (u.reset / 60000) + "\n";
        try {
            if (all.size() > 12000) {
                FileWriter w = new FileWriter(file(c), false);
                for (Point p : all) if (p.ts > u.updated - KEEP) w.write(p.ts + "," + p.pct + "," + p.week + "," + (p.reset / 60000) + "\n");
                w.write(line);
                w.close();
            } else {
                FileWriter w = new FileWriter(file(c), true);
                w.write(line);
                w.close();
            }
        } catch (Exception ignored) {}
    }

    static synchronized List<Point> load(Context c, long since) {
        List<Point> out = new ArrayList<Point>();
        File f = file(c);
        if (!f.exists()) return out;
        try {
            BufferedReader r = new BufferedReader(new FileReader(f));
            String line;
            while ((line = r.readLine()) != null) {
                String[] s = line.split(",");
                if (s.length < 3) continue;
                long ts = Long.parseLong(s[0]);
                long reset = s.length > 3 ? Long.parseLong(s[3]) * 60000 : 0;
                if (ts >= since) out.add(new Point(ts, Integer.parseInt(s[1]), Integer.parseInt(s[2]), reset));
            }
            r.close();
        } catch (Exception ignored) {}
        return out;
    }

    static Stats stats(List<Point> pts, int nDays) { return stats(pts, nDays, 0); }

    /** offset = días hacia atrás donde empieza el período (7 = semana anterior). */
    static Stats stats(List<Point> pts, int nDays, int offset) {
        Stats st = new Stats();
        ZoneId z = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(z);
        long[] hours = new long[24];
        Set<Long> allWin = new HashSet<Long>(), allLim = new HashSet<Long>();
        int peakSum = 0, peakDays = 0;
        for (int k = 0; k < nDays; k++) {
            LocalDate d = today.minusDays(k + offset);
            Day day = new Day();
            day.date = d;
            day.peak = -1;
            Set<Long> win = new HashSet<Long>(), lim = new HashSet<Long>();
            for (Point p : pts) {
                if (!Instant.ofEpochMilli(p.ts).atZone(z).toLocalDate().equals(d)) continue;
                day.peak = Math.max(day.peak, p.pct);
                if (p.reset > 0 && p.pct > 0) { win.add(p.reset); allWin.add(p.reset); }
                if (p.reset > 0 && p.pct >= 100) { lim.add(p.reset); allLim.add(p.reset); }
            }
            day.sessions = win.size();
            day.limits = lim.size();
            if (day.peak >= 0) { peakSum += day.peak; peakDays++; }
            st.days.add(day);
        }
        LocalDate from = today.minusDays(offset + nDays - 1), to = today.minusDays(offset);
        for (int i = 1; i < pts.size(); i++) {
            Point a = pts.get(i - 1), b = pts.get(i);
            LocalDate bd = Instant.ofEpochMilli(b.ts).atZone(z).toLocalDate();
            if (bd.isBefore(from) || bd.isAfter(to)) continue;
            if (a.reset == b.reset && b.pct > a.pct && b.ts - a.ts < 3600_000) {
                hours[Instant.ofEpochMilli(b.ts).atZone(z).getHour()] += b.pct - a.pct;
            }
        }
        long best = 0;
        for (int h = 0; h < 24; h++) if (hours[h] > best) { best = hours[h]; st.topHour = h; }
        st.avgPeak = peakDays > 0 ? peakSum / peakDays : -1;
        // Racha: días seguidos (con datos) sin llegar al límite
        for (Day day : st.days) {
            if (day.limits > 0) break;
            if (day.peak >= 0) st.streak++;
        }
        st.sessions = allWin.size();
        st.limits = allLim.size();
        return st;
    }

    /** Uso acumulado por día de la semana (0 = lunes) y hora, últimas 4 semanas. */
    static float[][] heatmap(List<Point> pts) {
        float[][] h = new float[7][24];
        ZoneId z = ZoneId.systemDefault();
        for (int i = 1; i < pts.size(); i++) {
            Point a = pts.get(i - 1), b = pts.get(i);
            if (a.reset == b.reset && b.pct > a.pct && b.ts - a.ts < 3600_000) {
                java.time.ZonedDateTime t = Instant.ofEpochMilli(b.ts).atZone(z);
                h[t.getDayOfWeek().getValue() - 1][t.getHour()] += b.pct - a.pct;
            }
        }
        return h;
    }

    /** Ritmo de la sesión actual en %/hora (última hora y media). -1 si no hay datos suficientes. */
    static double rate(Context c, Usage u) {
        long now = System.currentTimeMillis();
        Point first = null, last = null;
        for (Point q : load(c, now - 90 * 60_000L)) {
            if (q.reset != (u.reset / 60000) * 60000) continue;
            if (first == null) first = q;
            last = q;
        }
        if (first == null || last == null || last.ts - first.ts < 10 * 60_000L) return -1;
        return (last.pct - first.pct) / ((last.ts - first.ts) / 3_600_000.0);
    }

    /** Predicción en una línea para la pantalla principal. */
    static String prediction(Context c, Usage u) {
        if (!u.hasData()) return "";
        if (u.pct >= 100) return L.t("Llegaste al límite. ") + u.resetLine();
        double r = rate(c, u);
        if (r < 0) return L.t("Predicción: juntando datos de tu ritmo…");
        if (r <= 0.5) return L.t("A tu ritmo actual no vas a llegar al límite en esta sesión");
        long limitAt = System.currentTimeMillis() + (long) ((100 - u.pct) / r * 3_600_000);
        if (u.reset > 0 && limitAt >= u.reset) return L.t("A este ritmo (") + Math.round(r) + L.t("%/h) no llegás al límite antes de las ") + Usage.clock(u.reset);
        return L.t("A este ritmo (") + Math.round(r) + L.t("%/h) llegás al límite a las ") + Usage.clock(limitAt);
    }

    /** Horario de sueño detectado: el bloque más largo de horas sin uso (2 semanas). {inicio, fin} o null. */
    static int[] quietWindow(Context c) {
        List<Point> pts = load(c, System.currentTimeMillis() - 14 * DAY);
        if (pts.size() < 2 || pts.get(pts.size() - 1).ts - pts.get(0).ts < 3 * DAY) return null;
        float[] hours = new float[24];
        for (float[] row : heatmap(pts)) for (int h = 0; h < 24; h++) hours[h] += row[h];
        int bestStart = -1, bestLen = 0;
        for (int start = 0; start < 24; start++) {
            int len = 0;
            while (len < 24 && hours[(start + len) % 24] == 0) len++;
            if (len > bestLen) { bestLen = len; bestStart = start; }
        }
        if (bestLen < 4 || bestLen >= 24) return null;
        return new int[]{bestStart, (bestStart + bestLen) % 24};
    }

    static boolean inQuiet(Context c) {
        if (!Prefs.get(c).getBoolean("smartDnd", true)) return false;
        int[] w = quietWindow(c);
        if (w == null) w = new int[]{1, 8};
        int h = java.time.LocalTime.now().getHour();
        return w[0] < w[1] ? h >= w[0] && h < w[1] : h >= w[0] || h < w[1];
    }
}
