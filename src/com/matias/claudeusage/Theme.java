package com.matias.claudeusage;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;

/** Paletas (Claude, Monocromo, Pastel, Neón, Game Boy) en claro/oscuro + tipografía. */
final class Theme {
    static final String[] PALETTES = {"Claude", "Monocromo", "Pastel", "Neón", "Game Boy",
            "Estilo NES", "Estilo Nintendo DS", "Estilo PlayStation 1", "Estilo PlayStation 2", "Estilo PlayStation 3",
            "Estilo PlayStation 4", "Estilo PlayStation 5", "Estilo Switch", "Estilo Switch 2", "Estilo GameCube",
            "Estilo Game Boy Advance", "PC gamer (RGB)", "PC retro (DOS)", "Estilo Steam Deck", "Estilo ROG Ally",
            "Estilo Legion Go", "Estilo MSI Claw", "Estilo PSP", "Estilo PS Vita"};
    static final String[] PAL_CATS = {"Básicos", "Nintendo", "PlayStation", "PC y portátiles"};
    static final int[][] PAL_GROUPS = {{0, 1, 2, 3}, {4, 15, 5, 6, 12, 13, 14}, {7, 8, 9, 10, 11, 22, 23}, {16, 17, 18, 19, 20, 21}};

    /** Tipografías: archivo en assets (null = del sistema) y si es pixelada. */
    static final String[] FONTS = {"Normal", "Pixel", "Retro arcade", "Terminal", "Moderna", "Cuadrada", "Redonda", "Cómic", "Monoespaciada", "Arcade gruesa"};
    private static final String[] FONT_FILES = {null, "pixel.ttf", "retro.ttf", "terminal.ttf", null, "cuadrada.ttf", "redonda.ttf", "comic.ttf", null, "arcade.ttf"};
    private static final Typeface[] faces = new Typeface[FONTS.length];
    static volatile int fontIdx;

    final boolean dark;
    int pal;
    int bg, card;
    final int fg, dim, track, accent, low, mid, high;

    /** Paleta en uso (la app y el servicio comparten proceso). */
    static volatile Theme cur = build(0, true);
    static volatile boolean pixel;

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
            case 5: return dark // NES: gris consola, rojo de los botones
                    ? new Theme(true, "#2B2B2B", "#3A3A3A", "#E8E8E8", 170, "#33FFFFFF", "#D21E2A", "#7FB069", "#F2C14E", "#D21E2A")
                    : new Theme(false, "#D8D8D8", "#C4C4C4", "#222222", 170, "#26000000", "#B3161F", "#4E8A3E", "#C28A10", "#B3161F");
            case 6: return dark // DS: plateado y azul
                    ? new Theme(true, "#1C2430", "#26303E", "#EEF2F7", 170, "#2DFFFFFF", "#5DA9E9", "#6BCB77", "#FFD93D", "#FF6B6B")
                    : new Theme(false, "#E9EDF2", "#DCE2EA", "#1C2430", 165, "#1E000000", "#2F7BC4", "#3E9A50", "#C99A12", "#D9434A");
            case 7: return dark // PS1: gris y los cuatro colores del logo
                    ? new Theme(true, "#232323", "#2F2F2F", "#E6E6E6", 170, "#2DFFFFFF", "#0072CE", "#00A651", "#FFD400", "#E60012")
                    : new Theme(false, "#CFCFCF", "#BDBDBD", "#1A1A1A", 170, "#26000000", "#005BA6", "#00843F", "#C9A600", "#C4000F");
            case 8: return new Theme(true, "#0A0A12", "#141428", "#E8ECFF", 170, "#2DFFFFFF", "#2A5BD7", "#2FD3A1", "#F5C542", "#FF4D6D"); // PS2
            case 9: return new Theme(true, "#000000", "#121212", "#F2F2F2", 170, "#2DFFFFFF", "#9FB4C7", "#4CAF50", "#FFC107", "#E53935"); // PS3
            case 10: return dark // PS4: azul profundo
                    ? new Theme(true, "#0B1A33", "#13284D", "#EAF1FF", 170, "#2DFFFFFF", "#3D8BFF", "#2ECC71", "#F1C40F", "#E74C3C")
                    : new Theme(false, "#EAF1FB", "#D6E4F7", "#0B1A33", 165, "#1E000000", "#0059A8", "#1E9E57", "#C99A00", "#C9372C");
            case 11: return dark // PS5: blanco y negro con azul
                    ? new Theme(true, "#121417", "#1E2126", "#FFFFFF", 170, "#2DFFFFFF", "#4F7CFF", "#3DDC97", "#FFC24B", "#FF5A5F")
                    : new Theme(false, "#F7F8FA", "#E9ECF1", "#121417", 165, "#1E000000", "#2D5BFF", "#1FA56E", "#D99A10", "#E0383E");
            case 12: return dark // Switch: azul y rojo neón
                    ? new Theme(true, "#1B1B1B", "#262626", "#FFFFFF", 170, "#2DFFFFFF", "#00C3E3", "#00C3E3", "#FFDD00", "#FF4554")
                    : new Theme(false, "#F5F5F5", "#E6E6E6", "#1B1B1B", 165, "#1E000000", "#0098B3", "#008FA8", "#C9A800", "#E0303F");
            case 13: return new Theme(true, "#101113", "#1B1D20", "#F4F4F4", 170, "#2DFFFFFF", "#1FB6FF", "#36D399", "#FBBD23", "#FF4C5B"); // Switch 2
            case 14: return dark // GameCube: índigo
                    ? new Theme(true, "#1E1A3A", "#2A2452", "#EEEAFF", 170, "#2DFFFFFF", "#8B7CF6", "#7BD389", "#F7C948", "#F25F5C")
                    : new Theme(false, "#E6E3F7", "#D6D1F0", "#1E1A3A", 165, "#1E000000", "#4B3FA8", "#3E9A50", "#C99A12", "#D9434A");
            case 15: return dark // Game Boy Advance: índigo
                    ? new Theme(true, "#1D1838", "#2A2350", "#EDE9FF", 170, "#2DFFFFFF", "#7B68EE", "#6BCB77", "#FFD93D", "#FF6B6B")
                    : new Theme(false, "#E3DEFA", "#D2CBF5", "#1D1838", 165, "#1E000000", "#4B3FA8", "#3E9A50", "#C99A12", "#D9434A");
            case 16: return new Theme(true, "#0D0D12", "#17171F", "#F2F2F2", 170, "#33FFFFFF", "#00E5FF", "#39FF14", "#FFEA00", "#FF1744"); // PC RGB
            case 17: return new Theme(true, "#0000AA", "#1414C8", "#FFFFFF", 200, "#40FFFFFF", "#FFFF55", "#55FF55", "#FFFF55", "#FF5555"); // DOS
            case 18: return dark // Steam Deck
                    ? new Theme(true, "#1B1E24", "#262A33", "#F2F4F7", 170, "#2DFFFFFF", "#1A9FFF", "#59BF40", "#F7B32B", "#E24A4A")
                    : new Theme(false, "#EEF1F5", "#DDE2EA", "#1B1E24", 165, "#1E000000", "#0B7FD6", "#3E9A50", "#C99A12", "#D9434A");
            case 19: return dark // ROG Ally
                    ? new Theme(true, "#121214", "#1E1E22", "#FFFFFF", 170, "#2DFFFFFF", "#FF1E46", "#00E5A0", "#FFC400", "#FF1E46")
                    : new Theme(false, "#F4F4F6", "#E4E4E8", "#121214", 165, "#1E000000", "#D90F33", "#1FA56E", "#C99A00", "#D90F33");
            case 20: return new Theme(true, "#16181D", "#22252C", "#F0F2F5", 170, "#2DFFFFFF", "#E23D28", "#3FB27F", "#F2B134", "#E23D28"); // Legion Go
            case 21: return new Theme(true, "#0F0F10", "#1B1B1D", "#F5F5F5", 170, "#2DFFFFFF", "#E6B422", "#3FBF7F", "#E6B422", "#E5484D"); // MSI Claw
            case 22: return new Theme(true, "#0E0E10", "#1A1A1E", "#EDEDED", 170, "#2DFFFFFF", "#3D6FD9", "#4CAF50", "#FFC107", "#E53935"); // PSP
            case 23: return new Theme(true, "#0A0F1A", "#141C2C", "#EEF4FF", 170, "#2DFFFFFF", "#00A5FF", "#2ECC71", "#F1C40F", "#E74C3C"); // PS Vita
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
        // AMOLED: negro puro en modo oscuro (ahorra batería en pantallas OLED)
        if (t.dark && Prefs.get(c).getBoolean("amoled", false)) { t.bg = Color.BLACK; t.card = Color.parseColor("#0E0E0E"); }
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
        int f = Prefs.get(c).getInt("font", 0);
        if (f < 0 || f >= FONTS.length) f = 0;
        fontIdx = f;
        // Pixel, retro arcade y terminal: también pixelan los dibujos
        pixel = f == 1 || f == 2 || f == 3;
        if (FONT_FILES[f] != null && faces[f] == null) {
            try { faces[f] = Typeface.createFromAsset(c.getAssets(), FONT_FILES[f]); } catch (Exception e) { faces[f] = Typeface.DEFAULT; }
        }
    }

    static Typeface font(boolean bold) {
        int f = fontIdx;
        if (FONT_FILES[f] != null && faces[f] != null) return faces[f];
        if (f == 4) return Typeface.create("sans-serif-medium", bold ? Typeface.BOLD : Typeface.NORMAL);
        if (f == 8) return Typeface.create(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        return bold ? Typeface.create(Typeface.DEFAULT, Typeface.BOLD) : Typeface.DEFAULT;
    }

    int level(int pct) { return pct >= 90 ? high : pct >= 70 ? mid : low; }
}
