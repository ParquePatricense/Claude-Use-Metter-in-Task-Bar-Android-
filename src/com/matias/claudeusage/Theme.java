package com.matias.claudeusage;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;

/** Paletas (Claude, Monocromo, Pastel, Neón, Game Boy) en claro/oscuro + tipografía. */
final class Theme {
    static final String[] PALETTES = {"Claude", "Monocromo", "Pastel", "Neón", "Game Boy"};

    final boolean dark;
    int pal;
    final int bg, card, fg, dim, track, accent, low, mid, high;

    /** Paleta en uso (la app y el servicio comparten proceso). */
    static volatile Theme cur = build(0, true);
    static volatile boolean pixel;
    private static Typeface pixelFace;

    private Theme(boolean dark, String bg, String card, String fg, int dimA, String track, String accent,
                  String low, String mid, String high) {
        this.dark = dark;
        this.bg = Color.parseColor(bg);
        this.card = Color.parseColor(card);
        this.fg = Color.parseColor(fg);
        this.dim = (this.fg & 0x00FFFFFF) | (dimA << 24);
        this.track = Color.parseColor(track);
        this.accent = Color.parseColor(accent);
        this.low = Color.parseColor(low);
        this.mid = Color.parseColor(mid);
        this.high = Color.parseColor(high);
    }

    static Theme build(int pal, boolean dark) {
        Theme t = palette(pal, dark);
        t.pal = pal;
        return t;
    }

    private static Theme palette(int pal, boolean dark) {
        switch (pal) {
            case 1: return dark
                    ? new Theme(true, "#111111", "#1C1C1C", "#FFFFFF", 170, "#2DFFFFFF", "#FFFFFF", "#8A8A8A", "#C8C8C8", "#FFFFFF")
                    : new Theme(false, "#FFFFFF", "#F0F0F0", "#111111", 160, "#1E000000", "#111111", "#A0A0A0", "#555555", "#000000");
            case 2: return dark
                    ? new Theme(true, "#23212B", "#2E2B38", "#F5F3FF", 170, "#2DFFFFFF", "#B7A6F0", "#9AD4B8", "#F6D186", "#F4A3A3")
                    : new Theme(false, "#FBF8FF", "#F1ECFA", "#3A3550", 160, "#1E000000", "#9A86E0", "#6CC3A0", "#E8B85C", "#E88B8B");
            case 3: return new Theme(true, "#0B0B12", "#161625", "#FFFFFF", 170, "#33FFFFFF", "#FF2BD6", "#00F5A0", "#FFE600", "#FF3355");
            case 4: return new Theme(false, "#9BBC0F", "#8BAC0F", "#0F380F", 200, "#8BAC0F", "#306230", "#306230", "#306230", "#0F380F");
            default: return dark
                    ? new Theme(true, "#1F1E1D", "#2A2927", "#FFFFFF", 170, "#2DFFFFFF", "#D97757", "#46A758", "#F5A524", "#E5484D")
                    : new Theme(false, "#FAF9F5", "#F0EEE6", "#1F1E1D", 160, "#1E000000", "#D97757", "#3E9A50", "#E39A12", "#D93F45");
        }
    }

    /** Tema de la app según ajustes (claro/oscuro/sistema + paleta). */
    static Theme of(Context c) {
        int m = Prefs.get(c).getInt("theme", 0);
        boolean night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        Theme t = build(Prefs.get(c).getInt("palette", 0), m == 2 || (m == 0 && night));
        load(c);
        cur = t;
        return t;
    }

    /** Tema para widget, notificación e íconos: la variante oscura de la paleta. */
    static Theme widget(Context c) {
        load(c);
        Theme t = build(Prefs.get(c).getInt("palette", 0), true);
        cur = t;
        return t;
    }

    private static void load(Context c) {
        pixel = Prefs.get(c).getInt("font", 0) == 1;
        if (pixelFace == null) {
            try { pixelFace = Typeface.createFromAsset(c.getAssets(), "pixel.ttf"); } catch (Exception e) { pixelFace = Typeface.MONOSPACE; }
        }
    }

    static Typeface font(boolean bold) {
        if (pixel && pixelFace != null) return pixelFace;
        return bold ? Typeface.create(Typeface.DEFAULT, Typeface.BOLD) : Typeface.DEFAULT;
    }

    int level(int pct) { return pct >= 90 ? high : pct >= 70 ? mid : low; }
}
