package com.matias.claudeusage;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.View;

/** Cuadros por segundo: automático (tasa de refresco de la pantalla) o un valor fijo. */
final class Fps {
    static final int[] OPTIONS = {0, 24, 30, 48, 60, 90, 120, 144, 165};

    static String label(Context c, int fps) {
        return fps == 0 ? "Automático (tu pantalla: hasta " + maxRefresh(c) + " Hz)" : fps + " FPS";
    }

    static String[] labels(Context c) {
        String[] out = new String[OPTIONS.length];
        for (int i = 0; i < out.length; i++) out[i] = label(c, OPTIONS[i]);
        return out;
    }

    /** Tasa de refresco máxima que soporta la pantalla (60, 90, 120, 144, 165…). */
    static int maxRefresh(Context c) {
        try {
            Display d = c.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            float max = d.getRefreshRate();
            for (Display.Mode m : d.getSupportedModes()) max = Math.max(max, m.getRefreshRate());
            return Math.round(max);
        } catch (Exception e) {
            return 60;
        }
    }

    /** FPS efectivos de la app: lo elegido, sin pasar lo que da la pantalla. */
    static int app(Context c) {
        int f = Prefs.get(c).getInt("appFps", 0);
        int max = maxRefresh(c);
        return f == 0 ? max : Math.min(f, max);
    }

    /** FPS del widget: lo elegido (automático = tasa de la pantalla). */
    static int widget(Context c) {
        int f = Prefs.get(c).getInt("wFps2", 60);
        return f == 0 ? maxRefresh(c) : f;
    }

    /** Pide el próximo cuadro respetando el límite elegido. */
    static void next(View v) {
        int f = Prefs.get(v.getContext()).getInt("appFps", 0);
        if (f == 0 || f >= maxRefreshCached(v.getContext())) v.postInvalidateOnAnimation();
        else v.postInvalidateDelayed(Math.max(1, 1000 / f));
    }

    private static int cached = -1;

    private static int maxRefreshCached(Context c) {
        if (cached < 0) cached = maxRefresh(c);
        return cached;
    }
}
