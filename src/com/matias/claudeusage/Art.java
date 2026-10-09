package com.matias.claudeusage;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/** Todo lo que se dibuja como imagen: barras, anillo, estilos del widget, mascota pixel art. */
final class Art {
    static final String[] STYLES = {"Anillo", "Barra", "Solo número", "Batería retro", "Corazones", "Punto", "Mascota"};
    static final int RING = 0, BAR = 1, NUMBER = 2, BATTERY = 3, HEARTS = 4, DOT = 5, MASCOT = 6;

    // ---------- Barras ----------

    static Bitmap bar(Theme t, int pct, int w, int h) {
        if (Theme.pixel) {
            int gw = 40, gh = 2;
            Bitmap g = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(g);
            Paint p = new Paint();
            p.setColor(t.track);
            c.drawRect(0, 0, gw, gh, p);
            if (pct > 0) { p.setColor(t.level(pct)); c.drawRect(0, 0, Math.max(1, gw * pct / 100f), gh, p); }
            return Bitmap.createScaledBitmap(g, w, h, false);
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.argb(70, 128, 128, 128));
        c.drawRoundRect(new RectF(0, 0, w, h), h / 2f, h / 2f, p);
        if (pct > 0) {
            p.setColor(t.level(pct));
            c.drawRoundRect(new RectF(0, 0, Math.max(h, w * pct / 100f), h), h / 2f, h / 2f, p);
        }
        return b;
    }

    // ---------- Estilos del widget ----------

    /** frame 0/1 = dos cuadros para la animación del widget. */
    static Bitmap badge(android.content.Context ctx, Theme t, int style, int pct, int week, int size, int frame, int mascotId) {
        switch (style) {
            case BATTERY: return up(battery(t, pct, frame), size);
            case HEARTS: return up(hearts(t, pct, frame), size);
            case MASCOT: return up(mascotBadge(ctx, t, mascotId, pct, frame), size);
            case DOT: return Theme.pixel ? up(pixelDot(t, pct, frame), size) : dot(t, pct, frame, size);
            default:
                if (Theme.pixel) return up(smooth(t, style, pct, week, size / 5, frame), size);
                return smooth(t, style, pct, week, size, frame);
        }
    }

    private static Bitmap up(Bitmap small, int size) {
        int s = Math.max(small.getWidth(), small.getHeight());
        int k = Math.max(1, size / s);
        return Bitmap.createScaledBitmap(small, small.getWidth() * k, small.getHeight() * k, false);
    }

    private static Bitmap smooth(Theme t, int style, int pct, int week, int size, int frame) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Theme.pixel ? 0 : Paint.ANTI_ALIAS_FLAG);
        boolean pulse = frame == 1 && pct >= 90;
        int col = pulse ? fade(t.level(pct), 110) : t.level(pct);
        String txt = pct < 0 ? "–" : String.valueOf(pct);
        p.setTypeface(Theme.font(true));
        p.setTextAlign(Paint.Align.CENTER);
        if (style == RING) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            float sw = size * 0.10f, sw2 = size * 0.05f;
            RectF outer = new RectF(sw / 2, sw / 2, size - sw / 2, size - sw / 2);
            float in = sw + sw2 * 1.4f;
            RectF inner = new RectF(in, in, size - in, size - in);
            p.setStrokeWidth(sw);
            p.setColor(t.track);
            c.drawArc(outer, 0, 360, false, p);
            if (pct > 0) { p.setColor(col); c.drawArc(outer, -90, 360f * pct / 100, false, p); }
            p.setStrokeWidth(sw2);
            p.setColor(t.track);
            c.drawArc(inner, 0, 360, false, p);
            if (week > 0) { p.setColor(t.dim); c.drawArc(inner, -90, 360f * week / 100, false, p); }
            p.setStyle(Paint.Style.FILL);
            p.setColor(t.fg);
            String s = pct < 0 ? "–" : pct + "%";
            p.setTextSize(size * (s.length() >= 4 ? 0.20f : 0.24f));
            center(c, p, s, size / 2f, size / 2f);
        } else if (style == NUMBER) {
            p.setColor(pct < 0 ? t.fg : col);
            p.setTextSize(size * (txt.length() >= 3 ? 0.42f : 0.58f));
            center(c, p, txt, size / 2f, size * 0.46f);
            p.setColor(t.dim);
            p.setTextSize(size * 0.15f);
            if (pct >= 0) c.drawText("% sesión", size / 2f, size * 0.92f, p);
        } else {
            p.setColor(t.fg);
            p.setTextSize(size * (txt.length() >= 3 ? 0.30f : 0.38f));
            center(c, p, pct < 0 ? txt : txt + "%", size / 2f, size * 0.38f);
            float x0 = size * 0.08f, x1 = size * 0.92f, h = size * 0.12f, y = size * 0.66f;
            p.setColor(t.track);
            c.drawRoundRect(new RectF(x0, y, x1, y + h), h / 2, h / 2, p);
            if (pct > 0) { p.setColor(col); c.drawRoundRect(new RectF(x0, y, Math.max(x0 + h, x0 + (x1 - x0) * pct / 100f), y + h), h / 2, h / 2, p); }
            float h2 = size * 0.06f, y2 = y + h + size * 0.07f;
            p.setColor(t.track);
            c.drawRoundRect(new RectF(x0, y2, x1, y2 + h2), h2 / 2, h2 / 2, p);
            if (week > 0) { p.setColor(t.dim); c.drawRoundRect(new RectF(x0, y2, Math.max(x0 + h2, x0 + (x1 - x0) * week / 100f), y2 + h2), h2 / 2, h2 / 2, p); }
        }
        return b;
    }

    private static void center(Canvas c, Paint p, String s, float x, float y) {
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, p);
    }

    private static Bitmap dot(Theme t, int pct, int frame, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean pulse = frame == 1 && pct >= 90;
        p.setColor(pct < 0 ? t.track : pulse ? fade(t.level(pct), 120) : t.level(pct));
        c.drawCircle(size / 2f, size / 2f, size * (pulse ? 0.34f : 0.38f), p);
        return b;
    }

    // ---------- Widget animado (cuadros pre-renderizados) ----------

    /**
     * Un cuadro del widget en la fase ph (0..1) de un bucle de Mascots.loopMs.
     * Cada estilo tiene su movimiento: brillo que recorre el anillo, latido de corazones, barrido de batería…
     */
    static Bitmap badgeAt(android.content.Context ctx, Theme t, int style, int pct, int week, float ph) {
        double tau = Math.PI * 2;
        boolean hot = pct >= 90;
        if (style == MASCOT) {
            Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint pix = new Paint();
            pix.setFilterBitmap(false);
            Mascots.draw(ctx, c, new RectF(16, 10, 80, 74), t, pct, (long) (ph * Mascots.loopMs), true, pix, -1);
            digits(b, pct < 0 ? "-" : pct + "%", 48, 79, 3, t.fg);
            return b;
        }
        if (style == BATTERY || style == HEARTS) {
            Bitmap g = style == BATTERY ? battery(t, pct, 0) : hearts(t, pct, 0);
            Canvas c = new Canvas(g);
            Paint p = new Paint();
            int left = pct < 0 ? 0 : 100 - pct;
            if (style == BATTERY) {
                // Barrido de brillo sobre los bloques llenos; el último titila si queda poco
                int blocks = pct < 0 ? 0 : (int) Math.ceil(left / 20.0);
                int sweep = (int) (ph * 7) - 1;
                if (sweep >= 0 && sweep < blocks) { p.setColor(Color.argb(110, 255, 255, 255)); c.drawRect(4 + sweep * 5, 9, 8 + sweep * 5, 19, p); }
                if (blocks <= 2 && blocks > 0 && Math.sin(tau * ph * 2) < 0) { p.setColor(t.track); c.drawRect(4 + (blocks - 1) * 5, 9, 8 + (blocks - 1) * 5, 19, p); }
            } else {
                // Latido: los corazones saltan 1 px dos veces por ciclo
                boolean beat = (ph % 0.5f) < 0.08f || ((ph % 0.5f) > 0.14f && (ph % 0.5f) < 0.2f);
                if (beat) {
                    Bitmap shifted = Bitmap.createBitmap(g.getWidth(), g.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas sc = new Canvas(shifted);
                    sc.drawBitmap(Bitmap.createBitmap(g, 0, 0, g.getWidth(), 18), 0, -1, null);
                    sc.drawBitmap(Bitmap.createBitmap(g, 0, 18, g.getWidth(), g.getHeight() - 18), 0, 18, null);
                    g = shifted;
                }
            }
            return up(g, 96);
        }
        if (style == DOT) {
            int size = 96;
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint(Theme.pixel ? 0 : Paint.ANTI_ALIAS_FLAG);
            float br = (float) Math.sin(tau * ph);
            int col = pct < 0 ? t.track : t.level(pct);
            p.setColor(fade(col, 60));
            c.drawCircle(size / 2f, size / 2f, size * (0.42f + 0.04f * br), p);
            p.setColor(hot ? fade(col, (int) (170 + 85 * br)) : col);
            c.drawCircle(size / 2f, size / 2f, size * (0.34f + 0.02f * br), p);
            return Theme.pixel ? up(Bitmap.createScaledBitmap(b, 20, 20, false), 96) : b;
        }
        // Anillo, barra, número: base + efecto encima
        int size = 120;
        Bitmap b = smooth(t, style, pct, week, size, hot && Math.sin(tau * ph) < 0 ? 1 : 0);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (style == RING && pct > 0) {
            // Cometa de luz que recorre el arco lleno
            float sw = size * 0.10f;
            RectF outer = new RectF(sw / 2, sw / 2, size - sw / 2, size - sw / 2);
            float sweep = 360f * pct / 100;
            float head = sweep * ph;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(sw * 0.55f);
            for (int k = 0; k < 6; k++) {
                p.setColor(fade(Color.WHITE, 150 - k * 24));
                c.drawArc(outer, -90 + Math.max(0, head - k * 4), Math.min(4, head), false, p);
            }
        } else if (style == BAR && pct > 0) {
            // Brillo que cruza la barra llena
            float x0 = size * 0.08f, x1 = size * 0.92f, h = size * 0.12f, y = size * 0.66f;
            float fill = x0 + (x1 - x0) * pct / 100f, bx = x0 + (fill - x0) * ph;
            p.setColor(fade(Color.WHITE, 120));
            c.save();
            c.clipRect(x0, y, fill, y + h);
            c.drawRect(bx - h * 0.6f, y, bx + h * 0.6f, y + h, p);
            c.restore();
        } else if (style == NUMBER) {
            // Respira suave
            float k = 1 + 0.035f * (float) Math.sin(tau * ph);
            Bitmap r = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas rc = new Canvas(r);
            rc.scale(k, k, size / 2f, size / 2f);
            rc.drawBitmap(b, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
            b = r;
        }
        return Theme.pixel ? up(Bitmap.createScaledBitmap(b, 24, 24, false), 96) : b;
    }

    // ---------- Pixel art ----------

    private static Bitmap grid(int w, int h) { return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); }

    private static void px(Bitmap g, int x, int y, int color) {
        if (x >= 0 && y >= 0 && x < g.getWidth() && y < g.getHeight()) g.setPixel(x, y, color);
    }

    private static void rect(Bitmap g, int x, int y, int w, int h, int color) {
        for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) px(g, x + i, y + j, color);
    }

    private static final String[] DIGITS = {
            "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
            "111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111"};
    private static final String PERCENT = "101001010100101", DASH = "000000111000000";

    /** Texto con dígitos de 3x5 centrado en x. */
    static void digits(Bitmap g, String s, int cx, int y, int cell, int color) {
        int w = (s.length() * 4 - 1) * cell;
        int x = cx - w / 2;
        for (char ch : s.toCharArray()) {
            String pat = ch == '%' ? PERCENT : ch >= '0' && ch <= '9' ? DIGITS[ch - '0'] : DASH;
            for (int i = 0; i < 15; i++) {
                if (pat.charAt(i) == '1') rect(g, x + (i % 3) * cell, y + (i / 3) * cell, cell, cell, color);
            }
            x += 4 * cell;
        }
    }

    /** Pila retro que se vacía a medida que usás la sesión. Muestra lo que te queda. */
    static Bitmap battery(Theme t, int pct, int frame) {
        Bitmap g = grid(32, 32);
        int ol = t.fg, left = pct < 0 ? 0 : 100 - pct;
        rect(g, 2, 7, 26, 1, ol); rect(g, 2, 20, 26, 1, ol);
        rect(g, 2, 7, 1, 14, ol); rect(g, 27, 7, 1, 14, ol);
        rect(g, 28, 11, 2, 6, ol);
        int blocks = pct < 0 ? 0 : (int) Math.ceil(left / 20.0);
        for (int i = 0; i < 5; i++) {
            int color = i < blocks ? t.level(pct) : t.track;
            if (frame == 1 && i == blocks - 1 && blocks <= 2) color = t.track;
            rect(g, 4 + i * 5, 9, 4, 10, color);
        }
        digits(g, pct < 0 ? "-" : left + "%", 16, 24, 1, t.fg);
        return g;
    }

    private static final String[] HEART_FULL = {".11.11.", "1111111", "1111111", ".11111.", "..111..", "...1..."};
    private static final String[] HEART_EMPTY = {".11.11.", "1..1..1", "1.....1", ".1...1.", "..1.1..", "...1..."};

    /** 5 corazones = la sesión que te queda (cada uno 20%). */
    static Bitmap hearts(Theme t, int pct, int frame) {
        Bitmap g = grid(40, 40);
        int left = pct < 0 ? 0 : 100 - pct;
        int full = left / 20;
        boolean half = left % 20 >= 10;
        for (int i = 0; i < 5; i++) {
            int x0 = 1 + i * 8, y0 = 9;
            boolean blink = frame == 1 && left <= 20 && i == (half ? full : full - 1);
            for (int y = 0; y < 6; y++) for (int x = 0; x < 7; x++) {
                boolean f = HEART_FULL[y].charAt(x) == '1', e = HEART_EMPTY[y].charAt(x) == '1';
                boolean filled = i < full || (i == full && half && x <= 3);
                if (blink) filled = false;
                if (filled && f) px(g, x0 + x, y0 + y, t.high);
                else if (e) px(g, x0 + x, y0 + y, t.dim);
            }
        }
        digits(g, pct < 0 ? "-" : left + "%", 20, 22, 2, t.fg);
        return g;
    }

    private static Bitmap pixelDot(Theme t, int pct, int frame) {
        Bitmap g = grid(16, 16);
        boolean pulse = frame == 1 && pct >= 90;
        int col = pct < 0 ? t.track : pulse ? fade(t.level(pct), 120) : t.level(pct);
        float r = pulse ? 5.2f : 6.2f;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            float dx = x - 7.5f, dy = y - 7.5f;
            if (dx * dx + dy * dy <= r * r) px(g, x, y, col);
        }
        return g;
    }

    // ---------- Mascota ----------

    /** Estado de ánimo según el uso. */
    static int mood(int pct) {
        if (pct < 0) return 1;
        if (pct <= 5) return 0;   // fresca, festejando
        if (pct < 50) return 1;   // contenta
        if (pct < 75) return 2;   // normal
        if (pct < 90) return 3;   // cansada
        if (pct < 100) return 4;  // agotada
        return 5;                 // dormida (límite)
    }

    /** Mascota + número para el widget (24x24). */
    static Bitmap mascotBadge(android.content.Context ctx, Theme t, int id, int pct, int frame) {
        Bitmap g = grid(24, 24);
        Canvas c = new Canvas(g);
        c.drawBitmap(Mascots.widgetFrame(ctx, t, id, pct, frame), 4, 0, null);
        if (mood(pct) == 5) zzz(g, 19, frame == 1 ? 0 : 2, t.fg);
        if (mood(pct) == 0 && frame == 1) sparkle(g, 1, 2, t.mid);
        digits(g, pct < 0 ? "-" : pct + "%", 12, 18, 1, t.fg);
        return g;
    }

    static void zzz(Bitmap g, int x, int y, int color) {
        String[] z = {"1111", "0010", "0100", "1111"};
        for (int j = 0; j < 4; j++) for (int i = 0; i < 4; i++) if (z[j].charAt(i) == '1') px(g, x + i, y + j, color);
    }

    static void sparkle(Bitmap g, int x, int y, int color) {
        px(g, x + 1, y, color); px(g, x, y + 1, color); px(g, x + 1, y + 1, color); px(g, x + 2, y + 1, color); px(g, x + 1, y + 2, color);
    }

    // ---------- Ícono de la barra de estado / Ajustes rápidos ----------

    static Bitmap numberIcon(int pct) {
        int s = 96;
        String txt = pct < 0 ? "-" : String.valueOf(pct);
        if (Theme.pixel) {
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            int cell = txt.length() >= 3 ? 8 : 12;
            digits(b, txt, s / 2, (s - 5 * cell) / 2, cell, Color.WHITE);
            return b;
        }
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTypeface(Theme.font(true));
        p.setTextAlign(Paint.Align.CENTER);
        if (pct < 0) txt = "–";
        p.setTextSize(txt.length() >= 3 ? 50 : 74);
        center(cv, p, txt, s / 2f, s / 2f);
        return bmp;
    }

    /** Ícono de la barra de estado. Android lo pinta de un solo color: se distingue por la forma. */
    static Bitmap statusIcon(android.content.Context ctx, int pct) {
        int style = Prefs.get(ctx).getInt("statusIcon", 1);
        int s = 96;
        String txt = pct < 0 ? "-" : String.valueOf(pct);
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        if (style == 3) {
            // Silueta de la mascota (decorativa, sin número)
            Bitmap spr = Mascots.sprite(Theme.widget(ctx), Mascots.current(ctx), pct, false, 0);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int px = spr.getPixel(x, y);
                if (Color.alpha(px) == 0) continue;
                float lum = (0.299f * Color.red(px) + 0.587f * Color.green(px) + 0.114f * Color.blue(px)) / 255f;
                if (lum < 0.22f) continue;
                c.drawRect(x * 6, y * 6, x * 6 + 6, y * 6 + 6, p);
            }
            return b;
        }
        // Área disponible para el número según la forma
        float box;
        if (style == 1) {
            // Marco: borde redondeado grueso con el número adentro
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            c.drawRoundRect(new RectF(4, 4, s - 4, s - 4), 20, 20, p);
            p.setStyle(Paint.Style.FILL);
            box = 76;
        } else if (style == 2) {
            // Anillo fino de progreso con el número grande adentro
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            p.setStrokeCap(Paint.Cap.ROUND);
            RectF r = new RectF(4, 4, s - 4, s - 4);
            p.setAlpha(85);
            c.drawArc(r, 0, 360, false, p);
            p.setAlpha(255);
            if (pct > 0) c.drawArc(r, -90, 360f * pct / 100, false, p);
            p.setStyle(Paint.Style.FILL);
            box = 70;
        } else {
            box = 94;
        }
        if (Theme.pixel) {
            int cols = txt.length() * 4 - 1;
            int cell = Math.max(2, (int) Math.min(box / cols, box * 0.72f / 5));
            digits(b, txt, s / 2, (s - 5 * cell) / 2, cell, Color.WHITE);
            return b;
        }
        // Número condensado que llena el espacio
        p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(box);
        float w = p.measureText(txt);
        if (w > box) p.setTextSize(box * box / w);
        Paint.FontMetrics fm = p.getFontMetrics();
        float capH = -(fm.ascent) * 0.72f;
        if (capH > box * 0.78f) p.setTextSize(p.getTextSize() * box * 0.78f / capH);
        center(c, p, txt, s / 2f, s / 2f + 1);
        return b;
    }

    /** Ajusta un color para que se lea sobre fondo oscuro (true) o claro (false), sin llegar a blanco o negro puros. */
    static int readable(int color, boolean darkBg) {
        int c = color;
        for (int i = 0; i < 6; i++) {
            float lum = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
            if (darkBg && lum < 0.5f) c = mix(c, Color.WHITE, 0.2f);
            else if (!darkBg && lum > 0.42f) c = mix(c, Color.parseColor("#0B2A4A"), 0.25f);
            else break;
        }
        return c;
    }

    // ---------- Color ----------

    static int fade(int c, int alpha) { return (c & 0x00FFFFFF) | (alpha << 24); }

    static int mix(int a, int b, float k) {
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * k),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * k),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k));
    }
}
