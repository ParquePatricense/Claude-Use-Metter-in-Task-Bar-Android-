package com.matias.claudeusage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Mascotas pixel art: sprites, ánimo según el uso y animación fluida (60 FPS). */
final class Mascots {
    private static final int[][] EYE_OPEN = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
    private static final int[][] EYE_LINE = {{0, 1}, {1, 1}};
    private static final int[][] EYE_X = {{-1, -1}, {1, -1}, {0, 0}, {-1, 1}, {1, 1}};
    private static final int[][] EYE_HAPPY = {{-1, 1}, {0, 0}, {1, 1}};
    private static final int[][] EYE_SOCKET = {{-1, 0}, {0, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    private static final int[][][] MOUTH = {
            {{0, 0}, {3, 0}, {1, 1}, {2, 1}}, {{0, 0}, {3, 0}, {1, 1}, {2, 1}}, {{0, 1}, {1, 1}, {2, 1}, {3, 1}},
            {{0, 1}, {1, 0}, {2, 1}, {3, 0}}, {{1, 0}, {2, 0}, {1, 1}, {2, 1}}, {{1, 1}, {2, 1}}};
    private static final int[][] SWEAT = {{0, 0}, {0, 1}, {-1, 2}, {0, 2}, {1, 2}, {0, 3}};

    private static final Map<String, Bitmap> cache = new HashMap<String, Bitmap>();
    private static final Random rnd = new Random();

    static int count() { return MascotData.ALL.length; }

    /**
     * Duración del bucle cuando se pre-renderizan cuadros (widget). 0 = tiempo libre (app).
     * En modo bucle cada período se ajusta a un divisor del bucle para que la animación cierre sin saltos.
     */
    static long loopMs = 0;

    static double P(double period) {
        if (loopMs <= 0) return period;
        return loopMs / (double) Math.max(1, Math.round(loopMs / period));
    }

    /** Fase 0..1 de un ciclo de period ms. */
    static double ph(long ms, double period) { double p = P(period); return (ms % p) / p; }

    /** Ruido determinístico (temblor) que cambia cada 50 ms. */
    private static float noise(long ms, int salt) {
        long h = (ms / 50 + salt * 7919L) * 2654435761L;
        return ((h >>> 16) & 0xFFFF) / 65535f;
    }

    /** Mascota en uso: la elegida o una distinta cada día. */
    static int current(Context c) {
        android.content.SharedPreferences p = Prefs.get(c);
        if (p.getBoolean("randomDaily", false)) {
            long day = java.time.LocalDate.now().toEpochDay();
            return (int) ((day * 7919L) % count());
        }
        String key = p.getString("mascotKey", null);
        if (key == null) {
            // Migración: índice viejo → clave (las profesiones y minerales ya no existen)
            String[] old = {"blob", "slime", "ghost", "cat", "robot", "invader", "dragon", "ninja", "mush", "capy", "penguin",
                    "skull", "dog", "blackcat", "parrot", "hamster", "fish"};
            int o = p.getInt("mascotId", 0);
            key = o >= 0 && o < old.length ? old[o] : o >= 24 && o <= 26 ? new String[]{"sword", "axe", "bow"}[o - 24] : "blob";
            p.edit().putString("mascotKey", key).apply();
        }
        return indexOf(key);
    }

    static int indexOf(String key) {
        for (int i = 0; i < MascotData.KEYS.length; i++) if (MascotData.KEYS[i].equals(key)) return i;
        return 0;
    }

    /** Etapa de evolución: 0 huevo, 1 bebé, 2 adulta, 3 legendaria. Según días usando la app. */
    static int stage(Context c) {
        android.content.SharedPreferences p = Prefs.get(c);
        if (!p.getBoolean("evolve", true)) return 2;
        int xp = p.getInt("xpDays", 0);
        return xp < 3 ? 0 : xp < 10 ? 1 : xp < 30 ? 2 : 3;
    }

    static final String[] STAGES = {"Huevo", "Bebé", "Adulta", "Legendaria"};

    private static final String[] EGG = {
            "................", "......oooo......", ".....owwwwo.....", "....owwhwwwo....", "....owhwwwwo....",
            "...owwwwwwwwo...", "...owwwwwwwwo...", "..owwwwswwwwwo..", "..owwwsswwwwwo..", "..owwwwwwwswwo..",
            "..owwwwwwwsswo..", "..owwwwwwwwwwo..", "...owwwwwwwwo...", "....owwwwwwo....", ".....oooooo.....", "................"};

    /** Huevo con manchas del color de la mascota; con grietas cuando está por nacer. */
    static synchronized Bitmap egg(Theme t, int id, boolean cracked) {
        String key = "egg:" + id + ":" + cracked + ":" + t.pal;
        Bitmap cached = cache.get(key);
        if (cached != null) return cached;
        Map<Character, Integer> col = colors((String) MascotData.ALL[id][3]);
        int spot = col.get('b'), shell = Color.parseColor("#F4EBDC"), out = Color.parseColor("#6B5B4B"), hi = Color.WHITE;
        Bitmap g = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            char ch = EGG[y].charAt(x);
            if (ch == 'o') g.setPixel(x, y, out);
            else if (ch == 'w') g.setPixel(x, y, shell);
            else if (ch == 's') g.setPixel(x, y, spot);
            else if (ch == 'h') g.setPixel(x, y, hi);
        }
        if (cracked) {
            int[][] crack = {{4, 6}, {5, 7}, {6, 6}, {7, 7}, {8, 6}, {9, 7}, {10, 6}, {11, 7}};
            for (int[] p : crack) g.setPixel(p[0], p[1], out);
        }
        g = recolor(g, t);
        cache.put(key, g);
        return g;
    }

    static String name(int i) { return (String) MascotData.ALL[i][0]; }

    static String[] names() {
        String[] n = new String[count()];
        for (int i = 0; i < n.length; i++) n[i] = name(i);
        return n;
    }

    static int frames(int id) { return ((String[][]) MascotData.ALL[id][2]).length; }

    /** Sprite 16x16 de la mascota con la cara según el ánimo. */
    static synchronized Bitmap sprite(Theme t, int id, int pct, boolean blink, int frame) {
        id = Math.max(0, Math.min(count() - 1, id));
        int mood = Art.mood(pct);
        String key = id + ":" + mood + ":" + blink + ":" + frame + ":" + t.pal;
        Bitmap cached = cache.get(key);
        if (cached != null) return cached;
        if (cache.size() > 200) cache.clear();

        Object[] m = MascotData.ALL[id];
        String[][] fr = (String[][]) m[2];
        String[] rows = fr[frame % fr.length];
        Map<Character, Integer> col = colors((String) m[3]);
        Bitmap g = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            char ch = rows[y].charAt(x);
            if (ch != '.' && col.containsKey(ch)) g.setPixel(x, y, col.get(ch));
        }
        int[] eyes = (int[]) m[4];
        int[] mouth = (int[]) m[5];
        int[] sweat = (int[]) m[6];
        String flags = (String) m[9];
        boolean noFace = flags.contains("n"), sockets = flags.contains("s");
        int eyeCol = sockets ? col.get('o') : col.get(((String) m[7]).charAt(0));
        if (!noFace) {
            int[][] eye = blink || mood == 3 || mood == 5 ? EYE_LINE : mood == 4 ? EYE_X : mood == 0 ? EYE_HAPPY
                    : sockets ? EYE_SOCKET : EYE_OPEN;
            for (int k = 0; k < 2; k++) for (int[] p : eye) px(g, eyes[k * 2] + p[0], eyes[k * 2 + 1] + p[1], eyeCol);
            if (mouth != null) for (int[] p : MOUTH[mood]) px(g, mouth[0] + p[0], mouth[1] + p[1], col.get('o'));
            if (mood <= 1) { px(g, eyes[0] - 1, eyes[1] + 3, col.get('p')); px(g, eyes[2] + 2, eyes[1] + 3, col.get('p')); }
        }
        if (m[8] != null) { int[] n = (int[]) m[8]; px(g, n[0], n[1], col.get('p')); px(g, n[0] + 1, n[1], col.get('p')); }
        if (mood == 3 || mood == 4) for (int[] p : SWEAT) px(g, sweat[0] + p[0], sweat[1] + p[1], Color.parseColor("#7EC8F2"));
        g = recolor(g, t);
        cache.put(key, g);
        return g;
    }

    private static void px(Bitmap g, int x, int y, int c) { if (x >= 0 && y >= 0 && x < 16 && y < 16) g.setPixel(x, y, c); }

    private static Map<Character, Integer> colors(String spec) {
        Map<Character, Integer> c = new HashMap<Character, Integer>();
        String[] base = {"w#FFFFFF", "k#1E1E24", "r#E5484D", "y#F5C542", "p#F29CA3", "g#8A8A8A", "c#5FE3F0", "s#F6D186"};
        for (String b : base) c.put(b.charAt(0), Color.parseColor(b.substring(1)));
        Character first = null;
        for (String part : spec.split(",")) {
            if (part.isEmpty()) continue;
            if (first == null) first = part.charAt(0);
            c.put(part.charAt(0), Color.parseColor(part.substring(1)));
        }
        if (!spec.contains("b#") && first != null) c.put('b', c.get(first));
        int b = c.get('b');
        if (!spec.contains("o#")) c.put('o', Art.mix(b, Color.BLACK, 0.6f));
        if (!spec.contains("h#")) c.put('h', Art.mix(b, Color.WHITE, 0.45f));
        if (!spec.contains("d#")) c.put('d', Art.mix(b, Color.BLACK, 0.2f));
        c.put('S', Art.mix(c.get('s'), Color.BLACK, 0.25f));
        return c;
    }

    /** Monocromo y Game Boy: cuantiza los colores a los tonos de la paleta. */
    private static Bitmap recolor(Bitmap g, Theme t) {
        int[] tones;
        if (t.pal == 4) tones = new int[]{0xFF0F380F, 0xFF306230, 0xFF8BAC0F, 0xFF9BBC0F};
        else if (t.pal == 1) tones = new int[]{0xFF141414, 0xFF5A5A5A, 0xFFAAAAAA, 0xFFF2F2F2};
        else return g;
        Bitmap out = g.copy(Bitmap.Config.ARGB_8888, true);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = out.getPixel(x, y);
            if (Color.alpha(c) == 0) continue;
            float lum = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
            out.setPixel(x, y, tones[Math.min(3, (int) (lum * 4))]);
        }
        return out;
    }

    private static final Map<String, List<Bitmap>> loopCache = new HashMap<String, List<Bitmap>>();

    /** Cuadros de la mascota animada en bucle de 2,4 s (para la notificación). */
    static synchronized List<Bitmap> loopFrames(Context ctx, Theme t, int pct, int size, int fps, long loop) {
        String key = current(ctx) + "/" + stage(ctx) + "/" + Art.mood(pct) + "/" + t.pal + "/" + size + "/" + fps
                + "/" + loop;
        List<Bitmap> hit = loopCache.get(key);
        if (hit != null) return hit;
        if (loopCache.size() > 4) loopCache.clear();
        List<Bitmap> out = new ArrayList<Bitmap>();
        long prev = loopMs;
        loopMs = loop;
        int n = fps <= 0 ? 1 : (int) (loopMs * fps / 1000);
        Paint pix = new Paint();
        pix.setFilterBitmap(false);
        float pad = size * 0.12f;
        for (int i = 0; i < n; i++) {
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            draw(ctx, new Canvas(b), new RectF(pad, pad * 1.4f, size - pad, size - pad * 0.6f), t, pct,
                    (long) (i * loopMs / (double) n), fps > 0, pix, -1);
            out.add(b);
        }
        loopMs = prev;
        loopCache.put(key, out);
        return out;
    }

    /** Cuadro del widget (0/1): alterna el cuadro propio de la mascota o parpadea. */
    static Bitmap widgetFrame(Context c, Theme t, int id, int pct, int frame) {
        if (stage(c) == 0) return egg(t, id, Prefs.get(c).getInt("xpDays", 0) >= 2);
        if (frames(id) > 1 && Art.mood(pct) != 5) return sprite(t, id, pct, false, frame);
        return sprite(t, id, pct, frame == 1 && Art.mood(pct) != 5, 0);
    }

    /**
     * Dibuja la mascota animada dentro de box. ms = reloj de animación.
     * Cada tipo tiene su movimiento; el ánimo lo modifica (festeja saltando, tiembla agotada, respira dormida).
     */
    static void draw(Context ctx, Canvas c, RectF box, Theme t, int pct, long ms, boolean anim, Paint pix, float react) {
        int id = current(ctx);
        int stage = stage(ctx);
        int mood = Art.mood(pct);
        int kind = (Integer) MascotData.ALL[id][1];
        float u = Math.min(box.width(), box.height()) / 16f;
        float sec = ms / 1000f;
        double tau = Math.PI * 2;
        float amp = mood == 3 ? 0.5f : 1f;

        int frame = 0;
        boolean blink = false;
        float dx = 0, dy = 0, sx = 1, sy = 1, rot = 0;
        if (anim) {
            blink = mood != 5 && mood != 3 && (ms % P(3600)) < 140;
            if (frames(id) > 1 && mood != 5) {
                if (kind == 3) frame = (ms % P(4200)) < 260 ? 1 : 0;           // gato: mueve la oreja
                else if (kind == 5) frame = ph(ms, 1000) < 0.5 ? 0 : 1;          // invasor: marcha
                else if (kind == 4) frame = ph(ms, 1400) < 0.5 ? 0 : 1;          // robot: luz
                else frame = ph(ms, 700) < 0.5 ? 0 : 1;                         // alas, bufanda, ola, mandíbula
            }
            if (mood == 5) {
                float b = (float) Math.sin(tau * ph(ms, 2600));
                sy = 1 + 0.035f * b; sx = 1 - 0.015f * b;
            } else {
                switch (kind) {
                    case 1: { float s = (float) Math.sin(tau * ph(ms, 900)); sy = 1 + 0.08f * s * amp; sx = 1 - 0.08f * s * amp; break; }
                    case 2: dy = (float) Math.sin(tau * ph(ms, 2000)) * 1.0f * u * amp; dx = (float) Math.sin(tau * ph(ms, 3000)) * 0.4f * u; break;
                    case 3: dy = (float) Math.sin(tau * ph(ms, 1400)) * 0.3f * u * amp; break;
                    case 4: dy = (ph(ms, 800) < 0.5 ? 0 : 1) * 0.5f * u * amp; break;
                    case 5: dx = (ph(ms, 2000) < 0.5 ? -0.6f : 0.6f) * u; break;
                    case 6: dy = (float) Math.sin(tau * ph(ms, 800)) * 0.8f * u * amp; break;
                    case 7: { float s = (float) Math.sin(tau * ph(ms, 3200)); sx = sy = 1 + 0.025f * s; break; }
                    case 8: rot = (float) Math.sin(tau * ph(ms, 1000)) * 6f * amp; break;
                    default: dy = (float) Math.sin(tau * ph(ms, 1200)) * 0.6f * u * amp;
                }
                if (mood == 0) dy -= Math.abs(Math.sin(tau * ph(ms, 1400))) * 2.2f * u;            // festeja saltando
                if (mood == 4) { dx += (noise(ms, 1) - 0.5f) * 0.6f * u; dy += (noise(ms, 2) - 0.5f) * 0.4f * u; } // tiembla
            }
        }
        // Reacción al tocarla: salto con giro corto
        if (react >= 0) { dy -= (float) Math.sin(Math.PI * react) * 3.5f * u; rot += (float) Math.sin(tau * react) * 8f; }
        float cx = box.centerX(), bottom = box.centerY() + 8 * u;
        Bitmap s;
        if (stage == 0) {
            // Huevo: se tambalea de vez en cuando
            s = egg(t, id, Prefs.get(ctx).getInt("xpDays", 0) >= 2);
            float w = (ms % P(2600)) < 600 ? (float) Math.sin(tau * (ms % P(2600)) / 300.0) * 9f : 0;
            dx = 0; dy = react >= 0 ? dy : 0; sx = sy = 1; rot = anim ? w + (react >= 0 ? rot : 0) : 0;
        } else {
            s = sprite(t, id, pct, blink, frame);
        }
        if (stage == 1) { sx *= 0.72f; sy *= 0.72f; }
        c.save();
        c.translate(dx, dy);
        c.rotate(rot, cx, bottom);
        c.scale(sx, sy, cx, bottom);
        RectF dst = new RectF(cx - 8 * u, bottom - 16 * u, cx + 8 * u, bottom);
        c.drawBitmap(s, null, dst, pix);
        c.restore();
        if (stage == 3) aura(c, cx, box.centerY(), u, ms, anim, t);

        Paint p = new Paint();
        if (mood == 5) {
            // Zzz que suben y se desvanecen
            for (int i = 0; i < 3; i++) {
                float ph = anim ? (float) ((ph(ms, 2400) + i / 3f) % 1f) : i / 3f;
                float zx = cx + 5 * u + ph * 3 * u, zy = box.centerY() - 5 * u - ph * 5 * u;
                p.setColor(Art.fade(t.fg, (int) (255 * Math.sin(Math.PI * ph))));
                float k = u * (0.5f + 0.3f * ph);
                zLetter(c, zx, zy, k, p);
            }
        } else if (mood == 0) {
            // Brillitos de festejo
            float[][] pos = {{-7, -6}, {7, -4}, {-6, 3}};
            for (int i = 0; i < pos.length; i++) {
                float ph = anim ? (float) Math.abs(Math.sin(tau * (ph(ms, 1200) + i * 0.33))) : 1f;
                p.setColor(Art.fade(t.mid, (int) (255 * ph)));
                sparkle(c, cx + pos[i][0] * u, box.centerY() + pos[i][1] * u, u * (0.4f + 0.4f * ph), p);
            }
        }
    }

    private static void cell(Canvas c, RectF dst, float u, int x, int y, int w, int h, Paint p) {
        c.drawRect(dst.left + x * u, dst.top + y * u, dst.left + (x + w) * u, dst.top + (y + h) * u, p);
    }

    /** Aura dorada de la etapa legendaria. */
    private static void aura(Canvas c, float cx, float cy, float u, long ms, boolean anim, Theme t) {
        Paint p = new Paint();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10 + (anim ? ph(ms, 10000) * Math.PI * 2 : 0);
            float r = 10.5f * u;
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r * 0.8f;
            int alpha = (int) (120 + 135 * Math.abs(Math.sin(a * 2 + (anim ? ph(ms, 2400) * Math.PI * 2 : 0))));
            p.setColor(Art.fade(Color.parseColor("#F5C542"), alpha));
            c.drawRect(x - u * 0.4f, y - u * 0.4f, x + u * 0.4f, y + u * 0.4f, p);
        }
    }

    private static void zLetter(Canvas c, float x, float y, float k, Paint p) {
        String[] z = {"1111", "0010", "0100", "1111"};
        for (int j = 0; j < 4; j++) for (int i = 0; i < 4; i++)
            if (z[j].charAt(i) == '1') c.drawRect(x + i * k, y + j * k, x + (i + 1) * k, y + (j + 1) * k, p);
    }

    private static void sparkle(Canvas c, float x, float y, float k, Paint p) {
        c.drawRect(x - k / 2, y - k * 1.5f, x + k / 2, y + k * 1.5f, p);
        c.drawRect(x - k * 1.5f, y - k / 2, x + k * 1.5f, y + k / 2, p);
    }
}
