package com.matias.claudeusage;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.widget.RemoteViews;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Widget redimensionable de 1x1 a 5x5: un diseño por tamaño, paleta, estilo y animación. */
public class WidgetProvider extends AppWidgetProvider {
    private static final int[] TEXTS = {R.id.w_title, R.id.w_session, R.id.w_left, R.id.w_brand};
    private static final int[] DIMS = {R.id.w_reset, R.id.w_week, R.id.w_wshort, R.id.w_upd};

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { lastKey = null; update(c); }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) { lastKey = null; update(c); }

    // Cuadros del widget animado (se reusan mientras no cambie lo que se ve)
    private static List<Bitmap> frames;
    private static String framesKey;
    private static long lastPush;
    private static String lastKey;

    static void update(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, WidgetProvider.class));
        if (ids.length == 0) return;
        SharedPreferences pp = Prefs.get(c);
        int fps = pp.getBoolean("wAnim", true) ? pp.getInt("wFps", 60) : 0;
        // Intenta a los FPS elegidos; si el launcher no acepta tantos cuadros, baja a 30 y después a 2
        // Sin cambios visibles: no reenvía (ahorra batería; el launcher sigue animando solo)
        Usage u = Usage.load(c);
        String key = visualKey(c, u, fps) + "/" + u.resetLine() + "/" + u.weekLine() + "/" + u.error + "/"
                + pp.getInt("wAlpha", 90) + "/" + pp.getInt("wTap", 0) + "/" + pp.getBoolean("wMinimal", false);
        long now = System.currentTimeMillis();
        if (key.equals(lastKey) && now - lastPush < 10 * 60_000) return;
        lastKey = key;
        lastPush = now;
        int[] tries = fps >= 60 ? new int[]{60, 30, 2} : fps >= 30 ? new int[]{30, 2} : new int[]{0};
        for (int f : tries) {
            try {
                push(c, m, ids, f);
                pp.edit().putInt("wFpsActual", f).apply();
                return;
            } catch (Exception e) {
                frames = null;
            }
        }
    }

    private static String visualKey(Context c, Usage u, int fps) {
        SharedPreferences p = Prefs.get(c);
        return u.pct + "/" + u.week + "/" + p.getInt("wStyle", 0) + "/" + p.getInt("palette", 0) + "/" + p.getInt("font", 0)
                + "/" + Mascots.current(c) + "/" + Mascots.stage(c) + "/" + p.getInt("accessory", 0) + "/" + fps;
    }

    private static List<Bitmap> frames(Context c, Theme t, Usage u, int fps) {
        String key = visualKey(c, u, fps);
        if (frames != null && key.equals(framesKey)) return frames;
        int style = Prefs.get(c).getInt("wStyle", 0);
        List<Bitmap> out = new java.util.ArrayList<Bitmap>();
        if (fps <= 0) {
            out.add(Art.badge(c, t, style, u.pct, u.week, 240, 0, Mascots.current(c)));
        } else if (fps <= 2) {
            out.add(Art.badge(c, t, style, u.pct, u.week, 240, 0, Mascots.current(c)));
            out.add(Art.badge(c, t, style, u.pct, u.week, 240, 1, Mascots.current(c)));
        } else {
            Mascots.loopMs = style == Art.MASCOT ? 2400 : 1200;
            int n = (int) (Mascots.loopMs * fps / 1000);
            for (int i = 0; i < n; i++) out.add(Art.badgeAt(c, t, style, u.pct, u.week, i / (float) n));
            Mascots.loopMs = 0;
        }
        frames = out;
        framesKey = key;
        return out;
    }

    private static void push(Context c, AppWidgetManager m, int[] ids, int fps) {
        SharedPreferences p = Prefs.get(c);
        Theme t = Theme.widget(c);
        Usage u = Usage.load(c);
        float d = c.getResources().getDisplayMetrics().density;
        List<Bitmap> fr = frames(c, t, u, fps);
        int interval = fps > 2 ? Math.max(16, 1000 / fps) : fps > 0 ? 900 : 3_600_000;
        Bitmap sbar = Art.bar(t, u.pct, 400, 14);
        Bitmap wbar = Art.bar(t, u.week, 400, 14);

        RemoteViews v;
        if (p.getBoolean("wMinimal", false)) {
            // Minimalista: solo la imagen en cualquier tamaño
            v = fill(c, t, R.layout.widget_1x1, u, fr, interval, sbar, wbar, null);
        } else if (Build.VERSION.SDK_INT >= 31) {
            List<History.Point> pts = History.load(c, System.currentTimeMillis() - History.DAY);
            Bitmap chartM = ChartView.bitmap((int) (260 * d), (int) (120 * d), d, pts, t);
            Bitmap chartL = ChartView.bitmap((int) (330 * d), (int) (170 * d), d, pts, t);
            Map<SizeF, RemoteViews> map = new HashMap<SizeF, RemoteViews>();
            map.put(new SizeF(40, 40), fill(c, t, R.layout.widget_1x1, u, fr, interval, sbar, wbar, null));
            map.put(new SizeF(110, 40), fill(c, t, R.layout.widget_2x1, u, fr, interval, sbar, wbar, null));
            map.put(new SizeF(180, 40), fill(c, t, R.layout.widget_3x1, u, fr, interval, sbar, wbar, null));
            map.put(new SizeF(40, 110), fill(c, t, R.layout.widget_1x2, u, fr, interval, sbar, wbar, null));
            map.put(new SizeF(110, 110), fill(c, t, R.layout.widget_2x2, u, fr, interval, sbar, wbar, null));
            map.put(new SizeF(180, 110), fill(c, t, R.layout.widget_3x2, u, fr, interval, sbar, wbar, null));
            map.put(new SizeF(180, 200), fill(c, t, R.layout.widget_3x3, u, fr, interval, sbar, wbar, chartM));
            map.put(new SizeF(250, 260), fill(c, t, R.layout.widget_4x4, u, fr, interval, sbar, wbar, chartL));
            v = new RemoteViews(map);
        } else {
            v = fill(c, t, R.layout.widget_3x1, u, fr, interval, sbar, wbar, null);
        }
        m.updateAppWidget(ids, v);
    }

    // Los layouts que no tienen un id ignoran ese dato
    private static RemoteViews fill(Context c, Theme t, int layout, Usage u, List<Bitmap> fr, int interval,
                                    Bitmap sbar, Bitmap wbar, Bitmap chart) {
        SharedPreferences p = Prefs.get(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        // Animación: un ImageView por cuadro dentro del ViewFlipper, que el launcher pasa a 60 FPS
        v.removeAllViews(R.id.w_flip);
        for (Bitmap b : fr) {
            RemoteViews one = new RemoteViews(c.getPackageName(), R.layout.widget_frame);
            one.setImageViewBitmap(R.id.w_frame, b);
            v.addView(R.id.w_flip, one);
        }
        v.setInt(R.id.w_flip, "setFlipInterval", interval);
        boolean ok = u.hasData();
        v.setTextViewText(R.id.w_title, ok ? "Sesión " + u.pct + "%" : "Claude");
        v.setTextViewText(R.id.w_session, ok ? "Sesión " + u.pct + "%" : "Claude");
        v.setTextViewText(R.id.w_reset, ok ? u.resetLine() : "Sin datos todavía");
        v.setTextViewText(R.id.w_left, !ok ? "–" : u.reset > 0 ? "↻ " + Usage.left(u.reset) : "Libre");
        v.setTextViewText(R.id.w_week, ok ? u.weekLine() : "Abrí la app");
        v.setTextViewText(R.id.w_wshort, ok ? "Semana " + u.week + "%" : "");
        v.setTextViewText(R.id.w_upd, u.updated > 0 ? "Actualizado " + Usage.clock(u.updated) : "");
        for (int id : TEXTS) v.setTextColor(id, t.fg);
        for (int id : DIMS) v.setTextColor(id, t.dim);
        if (layout == R.layout.widget_2x2 || layout == R.layout.widget_3x2
                || layout == R.layout.widget_3x3 || layout == R.layout.widget_4x4) {
            v.setImageViewBitmap(R.id.w_wbar, wbar);
        }
        if (layout == R.layout.widget_3x2 || layout == R.layout.widget_3x3 || layout == R.layout.widget_4x4) {
            v.setImageViewBitmap(R.id.w_sbar, sbar);
        }
        if (chart != null) v.setImageViewBitmap(R.id.w_chart, chart);
        v.setInt(R.id.w_bg, "setColorFilter", t.bg);
        v.setInt(R.id.w_bg, "setImageAlpha", p.getInt("wAlpha", 90) * 255 / 100);
        // Tocar: actualizar (con vibración) o abrir la app
        PendingIntent tap = p.getInt("wTap", 0) == 0
                ? PendingIntent.getForegroundService(c, 3, new Intent(c, UsageService.class)
                        .setAction(UsageService.ACTION_REFRESH).putExtra(UsageService.EXTRA_HAPTIC, true),
                        PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_root, tap);
        return v;
    }
}
