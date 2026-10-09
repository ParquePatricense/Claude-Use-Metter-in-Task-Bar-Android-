package com.matias.claudeusage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/** Fondos animados pixel. Respetan los FPS elegidos. */
class BgView extends View {
    static final String[] KINDS = {"Ninguno", "Estrellas", "Lluvia", "Nieve", "Gato arcoíris", "Código que cae",
            "Hiperespacio", "Burbujas", "Luciérnagas", "Fuegos artificiales", "Corazones", "Hojas de otoño"};
    private static final int N = 70;
    private static final int[] RAINBOW = {0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF007AFF, 0xFFAF52DE};
    private static final String GLYPHS = "01アイウエオカキクケコサシスセソタチツテト0123456789";
    private final float[] x = new float[N], y = new float[N], sp = new float[N], ph = new float[N];
    private final Paint p = new Paint();
    private final float d;
    private final int kind;
    private final Theme th;
    private long last;
    private final Random rnd = new Random(7);

    BgView(Context c, int kind, Theme th) {
        super(c);
        this.kind = kind;
        this.th = th;
        d = c.getResources().getDisplayMetrics().density;
        for (int i = 0; i < N; i++) { x[i] = rnd.nextFloat(); y[i] = rnd.nextFloat(); sp[i] = 0.4f + rnd.nextFloat(); ph[i] = rnd.nextFloat() * 6.28f; }
        p.setTypeface(Theme.font(false));
    }

    private void sq(Canvas c, float X, float Y, float s, int col) { p.setColor(col); c.drawRect(X, Y, X + s, Y + s, p); }

    private float snap(float v, float px) { return Math.round(v / px) * px; }

    @Override
    protected void onDraw(Canvas c) {
        if (kind == 0) return;
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        int w = getWidth(), h = getHeight();
        float px = 3 * d;
        boolean anim = Prefs.get(getContext()).getBoolean("anim", true);
        if (!anim) dt = 0;
        double t = now / 1000.0;

        switch (kind) {
            case 4: rainbowCat(c, w, h, px, t, dt); break;
            case 5: matrix(c, w, h, dt); break;
            case 6: hyperspace(c, w, h, px, dt); break;
            case 7: case 8: case 10: case 11: floaters(c, w, h, px, t, dt); break;
            case 9: fireworks(c, w, h, px, t); break;
            default: classic(c, w, h, px, t, dt);
        }
        if (anim) Fps.next(this);
    }

    /** Estrellas, lluvia y nieve. */
    private void classic(Canvas c, int w, int h, float px, double t, float dt) {
        for (int i = 0; i < N; i++) {
            if (kind == 1) x[i] -= dt * 0.004f * sp[i];
            else if (kind == 2) { y[i] += dt * 0.9f * sp[i]; x[i] -= dt * 0.08f * sp[i]; }
            else { y[i] += dt * 0.07f * sp[i]; x[i] += (float) Math.sin(t * 1.1 + ph[i]) * dt * 0.02f; }
            wrap(i);
            float X = snap(x[i] * w, px), Y = snap(y[i] * h, px);
            if (kind == 1) sq(c, X, Y, sp[i] > 1.1f ? px : px * 0.66f, Art.fade(th.fg, (int) (60 + 120 * Math.abs(Math.sin(t * 1.4 * sp[i] + ph[i])))));
            else if (kind == 2) { p.setColor(Art.fade(th.accent, 70)); c.drawRect(X, Y, X + px * 0.5f, Y + px * 3, p); }
            else sq(c, X, Y, px, Art.fade(th.fg, 110));
        }
    }

    private void wrap(int i) {
        if (y[i] > 1) { y[i] -= 1; x[i] = (x[i] + 0.37f) % 1; }
        if (y[i] < 0) { y[i] += 1; x[i] = (x[i] + 0.37f) % 1; }
        if (x[i] < 0) x[i] += 1;
        if (x[i] > 1) x[i] -= 1;
    }

    /** Gato pixel con estela de arcoíris que cruza la pantalla. */
    private void rainbowCat(Canvas c, int w, int h, float px, double t, float dt) {
        for (int i = 0; i < N; i++) {
            x[i] -= dt * 0.05f * sp[i];
            wrap(i);
            sq(c, snap(x[i] * w, px), snap(y[i] * h, px), px * 0.7f, Art.fade(Color.WHITE, (int) (80 + 100 * Math.abs(Math.sin(t * 3 + ph[i])))));
        }
        float cycle = (float) ((t % 7.0) / 7.0);
        float cs = px * 1.4f;
        float catX = -14 * cs + cycle * (w + 28 * cs), catY = h * 0.35f;
        int bob = ((int) (t * 6)) % 2;
        // Estela: 6 franjas que ondulan
        for (float sx = catX; sx > -cs; sx -= cs * 2) {
            int wave = ((int) ((catX - sx) / (cs * 2)) + (int) (t * 6)) % 2;
            for (int k = 0; k < 6; k++) sq(c, sx - cs * 2, catY + (k * 1 + wave) * cs, cs * 2, RAINBOW[k]);
        }
        // Cuerpo (tostada rosa) y cabeza gris
        float by = catY - cs + bob * cs;
        p.setColor(0xFF3A2A1E); c.drawRect(catX - cs, by - cs, catX + 10 * cs, by + 8 * cs, p);
        p.setColor(0xFFF5C9A0); c.drawRect(catX, by, catX + 9 * cs, by + 7 * cs, p);
        p.setColor(0xFFFF8FCB); c.drawRect(catX + cs, by + cs, catX + 8 * cs, by + 6 * cs, p);
        float hx = catX + 7 * cs, hy = by + 2 * cs;
        p.setColor(0xFF3A3A3A); c.drawRect(hx - cs, hy - cs, hx + 7 * cs, hy + 6 * cs, p);
        p.setColor(0xFF9E9E9E); c.drawRect(hx, hy, hx + 6 * cs, hy + 5 * cs, p);
        sq(c, hx, hy - cs, cs, 0xFF9E9E9E); sq(c, hx + 5 * cs, hy - cs, cs, 0xFF9E9E9E);
        sq(c, hx + cs, hy + cs, cs, 0xFF111111); sq(c, hx + 4 * cs, hy + cs, cs, 0xFF111111);
        sq(c, hx + 0.5f * cs, hy + 3 * cs, cs, 0xFFFF9EB0); sq(c, hx + 4.5f * cs, hy + 3 * cs, cs, 0xFFFF9EB0);
    }

    /** Columnas de caracteres verdes que caen. */
    private void matrix(Canvas c, int w, int h, float dt) {
        float size = 14 * d;
        int cols = Math.min(N, (int) (w / size) + 1);
        p.setTextSize(size);
        for (int i = 0; i < cols; i++) {
            y[i] += dt * 0.25f * sp[i];
            if (y[i] > 1.4f) { y[i] = -0.2f; sp[i] = 0.4f + rnd.nextFloat(); }
            float X = i * size, head = y[i] * h;
            for (int k = 0; k < 14; k++) {
                float Y = head - k * size;
                if (Y < -size || Y > h + size) continue;
                int a = k == 0 ? 230 : (int) (170 * (1 - k / 14f));
                p.setColor(k == 0 ? Art.fade(0xFFCFFFD8, a) : Art.fade(0xFF2BD96B, a));
                char ch = GLYPHS.charAt((int) Math.abs((i * 31 + k * 17 + (int) (head / size)) % GLYPHS.length()));
                c.drawText(String.valueOf(ch), X, Y, p);
            }
        }
    }

    /** Estrellas que salen disparadas desde el centro. */
    private void hyperspace(Canvas c, int w, int h, float px, float dt) {
        float cx = w / 2f, cy = h / 2f, maxR = (float) Math.hypot(cx, cy);
        p.setStrokeWidth(px * 0.6f);
        for (int i = 0; i < N; i++) {
            y[i] += dt * 0.35f * sp[i] * (0.2f + y[i]);
            if (y[i] > 1) { y[i] = 0.02f; ph[i] = rnd.nextFloat() * 6.28f; }
            float r1 = y[i] * maxR, r0 = Math.max(0, r1 - maxR * 0.08f * y[i] * sp[i]);
            float cs = (float) Math.cos(ph[i]), sn = (float) Math.sin(ph[i]);
            p.setColor(Art.fade(i % 5 == 0 ? th.accent : th.fg, (int) (60 + 195 * y[i])));
            c.drawLine(cx + cs * r0, cy + sn * r0, cx + cs * r1, cy + sn * r1, p);
        }
    }

    /** Burbujas, luciérnagas, corazones y hojas. */
    private void floaters(Canvas c, int w, int h, float px, double t, float dt) {
        String[] heart = {".11.11.", "1111111", "1111111", ".11111.", "..111..", "...1..."};
        for (int i = 0; i < N / 2; i++) {
            if (kind == 11) { y[i] += dt * 0.06f * sp[i]; x[i] += (float) Math.sin(t * 1.3 + ph[i]) * dt * 0.05f; }
            else if (kind == 8) { x[i] += (float) Math.sin(t * 0.7 + ph[i]) * dt * 0.03f; y[i] += (float) Math.cos(t * 0.5 + ph[i] * 2) * dt * 0.02f; }
            else { y[i] -= dt * (kind == 7 ? 0.06f : 0.05f) * sp[i]; x[i] += (float) Math.sin(t * 1.5 + ph[i]) * dt * 0.01f; }
            wrap(i);
            float X = snap(x[i] * w, px), Y = snap(y[i] * h, px);
            if (kind == 7) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(px * 0.5f);
                p.setColor(Art.fade(th.accent, 120));
                c.drawCircle(X, Y, px * (2 + sp[i] * 2.5f), p);
                p.setStyle(Paint.Style.FILL);
                sq(c, X - px * sp[i], Y - px * (1 + sp[i]), px * 0.6f, Art.fade(Color.WHITE, 140));
            } else if (kind == 8) {
                float a = (float) Math.max(0, Math.sin(t * 1.8 * sp[i] + ph[i]));
                sq(c, X - px, Y - px, px * 3, Art.fade(0xFFE6FF6B, (int) (50 * a)));
                sq(c, X, Y, px, Art.fade(0xFFF4FF9E, (int) (60 + 195 * a)));
            } else if (kind == 10) {
                float k = px * 0.6f * (0.7f + sp[i] * 0.4f);
                p.setColor(Art.fade(i % 3 == 0 ? th.accent : 0xFFFF6F91, 150));
                for (int yy = 0; yy < 6; yy++) for (int xx = 0; xx < 7; xx++)
                    if (heart[yy].charAt(xx) == '1') c.drawRect(X + xx * k, Y + yy * k, X + (xx + 1) * k, Y + (yy + 1) * k, p);
            } else {
                int[] leaf = {0xFFE07A1F, 0xFFC0392B, 0xFFF2B134, 0xFF8B5A2B};
                c.save();
                c.rotate((float) (Math.sin(t * 2 + ph[i]) * 35), X, Y);
                sq(c, X - px, Y - px, px * 2, leaf[i % 4]);
                sq(c, X + px, Y, px, leaf[(i + 1) % 4]);
                c.restore();
            }
        }
    }

    /** Explosiones de partículas en puntos al azar. */
    private void fireworks(Canvas c, int w, int h, float px, double t) {
        for (int b = 0; b < 4; b++) {
            double period = 2.2 + b * 0.37;
            double local = (t + b * 0.9) % period;
            int seed = (int) ((t + b * 0.9) / period) * 13 + b;
            Random r = new Random(seed);
            float cx = w * (0.15f + 0.7f * r.nextFloat()), cy = h * (0.12f + 0.45f * r.nextFloat());
            int col = new int[]{th.accent, th.low, th.mid, th.high, 0xFFFFFFFF}[r.nextInt(5)];
            float prog = (float) (local / 1.4);
            if (prog > 1) continue;
            int a = (int) (255 * (1 - prog));
            for (int k = 0; k < 16; k++) {
                double ang = Math.PI * 2 * k / 16;
                float rad = prog * (60 + 30 * r.nextFloat()) * d;
                float X = snap(cx + (float) Math.cos(ang) * rad, px), Y = snap(cy + (float) Math.sin(ang) * rad + prog * prog * 30 * d, px);
                sq(c, X, Y, px, Art.fade(col, a));
            }
        }
    }
}
