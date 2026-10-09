package com.matias.claudeusage;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Medidor principal: arco, batería, corazones o mascota. Animado y con pulso al límite. */
class GaugeView extends View {
    static final String[] METERS = {"Arco", "Batería retro", "Corazones", "Mascota", "Barra de vida (RPG)",
            "Barra de experiencia", "Bloques que caen", "Comecocos", "Anillos", "Monedas", "Velocímetro", "Combustible"};
    /** Medidores que muestran lo que te queda (como vida) en vez de lo usado. */
    private static boolean showsLeft(int m) { return m == 1 || m == 2 || m == 4 || m == 7 || m == 8 || m == 9 || m == 11; }

    private Usage u = new Usage();
    private Theme th = Theme.cur;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pix = new Paint();
    private final float d;
    private float shown = -1;      // % que se está mostrando (animado)
    private ValueAnimator anim;
    private boolean zen;

    GaugeView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        pix.setFilterBitmap(false);
    }

    void setZen(boolean z) { zen = z; requestLayout(); }

    void set(Usage nu, Theme t) {
        th = t;
        int target = nu.hasData() ? nu.pct : -1;
        boolean changed = target != u.pct || shown < 0;
        u = nu;
        if (!changed || target < 0) { if (target < 0) shown = -1; invalidate(); return; }
        if (!Prefs.get(getContext()).getBoolean("anim", true)) { shown = target; invalidate(); return; }
        if (anim != null) anim.cancel();
        // Al abrir cuenta desde 0; después anima solo el cambio
        anim = ValueAnimator.ofFloat(shown < 0 ? 0 : shown, target);
        anim.setDuration(shown < 0 ? 1100 : 500);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { shown = (Float) a.getAnimatedValue(); invalidate(); }
        });
        anim.start();
    }

    private float size(int w) { return Math.min(w - 32 * d, (zen ? 320 : 260) * d); }

    @Override
    protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws);
        setMeasuredDimension(w, (int) (size(w) + (zen ? 60 : 110) * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int meter = Prefs.get(getContext()).getInt("meter", 0);
        boolean animOn = Prefs.get(getContext()).getBoolean("anim", true);
        boolean pulseOn = animOn && Prefs.get(getContext()).getBoolean("pulse", true) && u.pct >= 90;
        boolean secs = Prefs.get(getContext()).getBoolean("seconds", true);
        int val = shown < 0 ? -1 : Math.round(shown);
        float s = size(w), sw = 20 * d, cx = w / 2f, cy = 8 * d + s / 2;
        int col = th.level(Math.max(0, val));
        if (pulseOn) {
            float ph = (SystemClock.uptimeMillis() % 1200) / 1200f;
            col = Art.fade(col, (int) (150 + 105 * Math.abs(Math.cos(ph * Math.PI))));
        }
        p.setTypeface(Theme.font(true));

        if (meter == 0) {
            RectF oval = new RectF(cx - s / 2 + sw / 2, cy - s / 2 + sw / 2, cx + s / 2 - sw / 2, cy + s / 2 - sw / 2);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Theme.pixel ? Paint.Cap.BUTT : Paint.Cap.ROUND);
            p.setStrokeWidth(sw);
            p.setColor(th.track);
            c.drawArc(oval, 135, 270, false, p);
            if (val > 0) { p.setColor(col); c.drawArc(oval, 135, 270f * val / 100, false, p); }
            if (pulseOn) {
                p.setStrokeWidth(sw * 0.35f);
                p.setColor(Art.fade(th.high, 60));
                RectF glow = new RectF(oval);
                glow.inset(-sw * 0.8f, -sw * 0.8f);
                c.drawArc(glow, 135, 270f * val / 100, false, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(th.fg);
            p.setTextSize((zen ? 84 : 64) * d);
            c.drawText(val >= 0 ? val + "%" : "–", cx, cy + 16 * d, p);
            p.setTypeface(Theme.font(false));
            p.setTextSize(15 * d);
            p.setColor(th.dim);
            c.drawText("de tu sesión", cx, cy + (zen ? 54 : 44) * d, p);
        } else {
            float box = meter == 3 ? s * 0.7f : s * 0.9f, my = meter == 3 ? cy - s * 0.12f : cy;
            if (meter == 3) {
                RectF r = new RectF(cx - box / 2, my - box / 2, cx + box / 2, my + box / 2);
                Mascots.draw(getContext(), c, r, th, val, SystemClock.uptimeMillis(), animOn, pix, -1);
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(th.fg);
                p.setTextSize(40 * d);
                c.drawText(val >= 0 ? val + "%" : "–", cx, my + box / 2 + 40 * d, p);
                if (animOn) Fps.next(this);
            } else if (meter >= 4) {
                extra(c, meter, val, cx, cy, s, animOn);
            } else {
                Bitmap b = meter == 1 ? Art.battery(th, val, 0) : Art.hearts(th, val, 0);
                float k = Math.min(box / b.getWidth(), box / b.getHeight());
                float bw = b.getWidth() * k, bh = b.getHeight() * k;
                pix.setAlpha(pulseOn ? Color2.alpha(col) : 255);
                c.drawBitmap(b, null, new RectF(cx - bw / 2, my - bh / 2, cx + bw / 2, my + bh / 2), pix);
            }
        }

        p.setTypeface(Theme.font(false));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(16 * d);
        p.setColor(th.fg);
        String left = u.reset > 0 ? (secs ? Usage.leftSec(u.reset) : Usage.left(u.reset)) : "";
        String r = !u.hasData() ? "Esperando datos…"
                : u.reset > 0 ? "Reinicia en " + left + " · " + Usage.clock(u.reset) : "Sin sesión activa";
        if (showsLeft(meter)) r = (u.hasData() ? "Te queda " + (100 - u.pct) + "% · " : "") + r;
        c.drawText(r, cx, cy + s / 2 + (meter == 0 ? -4 : 14) * d, p);

        if (!zen) {
            float x0 = 24 * d, x1 = w - 24 * d, y = cy + s / 2 + 44 * d;
            p.setTextAlign(Paint.Align.LEFT);
            p.setTypeface(Theme.font(true));
            p.setTextSize(16 * d);
            c.drawText("Semana " + (u.week < 0 ? "–" : u.week + "%"), x0, y, p);
            if (u.weekReset > 0) {
                p.setTypeface(Theme.font(false));
                p.setTextSize(14 * d);
                p.setTextAlign(Paint.Align.RIGHT);
                p.setColor(th.dim);
                c.drawText("reinicia " + Usage.dayClock(u.weekReset), x1, y, p);
            }
            float by = y + 14 * d, bh = 10 * d, rad = Theme.pixel ? 0 : bh / 2;
            p.setColor(th.track);
            c.drawRoundRect(new RectF(x0, by, x1, by + bh), rad, rad, p);
            if (u.week > 0) {
                p.setColor(th.level(u.week));
                c.drawRoundRect(new RectF(x0, by, Math.max(x0 + bh, x0 + (x1 - x0) * u.week / 100f), by + bh), rad, rad, p);
            }
        }
        if (pulseOn) Fps.next(this);
    }

    /** Medidores extra inspirados en juegos. Pixel = s/40. */
    private void extra(Canvas c, int m, int val, float cx, float cy, float s, boolean animOn) {
        long t = SystemClock.uptimeMillis();
        float u = s / 40f;
        int v = Math.max(0, val), left = 100 - v;
        Paint q = new Paint();
        q.setTypeface(Theme.font(true));
        q.setTextAlign(Paint.Align.CENTER);
        q.setAntiAlias(true);
        switch (m) {
            case 4: { // Barra de vida RPG: lo que te queda
                float x0 = cx - 17 * u, x1 = cx + 17 * u, y0 = cy - 3 * u, y1 = cy + 3 * u;
                q.setColor(th.fg); c.drawRect(x0 - u, y0 - u, x1 + u, y1 + u, q);
                q.setColor(th.bg); c.drawRect(x0, y0, x1, y1, q);
                q.setColor(th.level(v)); c.drawRect(x0, y0, x0 + (x1 - x0) * left / 100f, y1, q);
                q.setColor(Art.fade(Color.WHITE, 70)); c.drawRect(x0, y0, x0 + (x1 - x0) * left / 100f, y0 + u * 1.5f, q);
                q.setColor(th.fg); q.setTextSize(5 * u); q.setTextAlign(Paint.Align.LEFT);
                c.drawText("HP", x0, y0 - 2.5f * u, q);
                q.setTextAlign(Paint.Align.RIGHT);
                c.drawText(left + "/100", x1, y1 + 7 * u, q);
                break;
            }
            case 5: { // Barra de experiencia segmentada con el nivel arriba
                float x0 = cx - 18 * u, seg = 36 * u / 20f;
                for (int i = 0; i < 20; i++) {
                    q.setColor(i < v / 5 ? th.low : th.track);
                    c.drawRect(x0 + i * seg + u * 0.3f, cy, x0 + (i + 1) * seg - u * 0.3f, cy + 3 * u, q);
                }
                q.setTextSize(14 * u);
                q.setColor(Art.mix(th.low, Color.BLACK, 0.6f));
                c.drawText(String.valueOf(v), cx + u * 0.6f, cy - 3 * u + u * 0.6f, q);
                q.setColor(th.low);
                c.drawText(String.valueOf(v), cx, cy - 3 * u, q);
                break;
            }
            case 6: { // Bloques que caen: se apilan con el uso
                int cols = 10, rows = 16;
                float cell = s * 0.8f / rows, gx = cx - cols * cell / 2, gy = cy - rows * cell / 2;
                q.setColor(th.track); c.drawRect(gx - u * 0.5f, gy - u * 0.5f, gx + cols * cell + u * 0.5f, gy + rows * cell + u * 0.5f, q);
                q.setColor(th.bg); c.drawRect(gx, gy, gx + cols * cell, gy + rows * cell, q);
                int[] cols6 = {0xFF00F0F0, 0xFFF0F000, 0xFFA000F0, 0xFF00F000, 0xFFF00000, 0xFF0000F0, 0xFFF0A000};
                int filled = v * cols * rows / 100;
                for (int i = 0; i < filled; i++) {
                    int r = rows - 1 - i / cols, col = i % cols;
                    q.setColor(cols6[(i / 4 + r) % 7]);
                    c.drawRect(gx + col * cell + 1, gy + r * cell + 1, gx + (col + 1) * cell - 1, gy + (r + 1) * cell - 1, q);
                }
                if (animOn && v < 100) {
                    int top = rows - 1 - filled / cols;
                    int fy = (int) ((t / 220) % Math.max(1, top));
                    int fx = (int) ((t / 3000) % (cols - 2));
                    q.setColor(cols6[(int) ((t / 3000) % 7)]);
                    for (int k = 0; k < 3; k++) c.drawRect(gx + (fx + k) * cell + 1, gy + fy * cell + 1, gx + (fx + k + 1) * cell - 1, gy + (fy + 1) * cell - 1, q);
                    c.drawRect(gx + (fx + 1) * cell + 1, gy + Math.max(0, fy - 1) * cell + 1, gx + (fx + 2) * cell - 1, gy + Math.max(0, fy - 1) * cell + cell - 1, q);
                }
                break;
            }
            case 7: { // Comecocos: los puntos que quedan son lo que te queda
                float x0 = cx - 18 * u, step = 36 * u / 10f, py = cy;
                float pacX = x0 + step * (v / 10f);
                for (int i = 0; i < 10; i++) {
                    float dx = x0 + i * step + step / 2;
                    if (dx < pacX) continue;
                    q.setColor(th.fg);
                    float r = i == 9 ? u * 1.6f : u * 0.8f;
                    c.drawRect(dx - r, py - r, dx + r, py + r, q);
                }
                float mouth = animOn ? (float) (Math.abs(Math.sin(t / 120.0)) * 40) : 30;
                q.setColor(0xFFFFD300);
                c.drawArc(new android.graphics.RectF(pacX - 4 * u, py - 4 * u, pacX + 4 * u, py + 4 * u), mouth, 360 - 2 * mouth, true, q);
                break;
            }
            case 8: case 9: { // Anillos / monedas girando + contador de lo que te queda
                float spin = animOn ? (float) Math.abs(Math.cos(t / 260.0)) : 1f;
                float rx = 9 * u * Math.max(0.12f, spin), ry = 9 * u, ccy = cy - 4 * u;
                int gold = 0xFFF5C542, dark = 0xFFB8860B;
                if (m == 8) {
                    q.setStyle(Paint.Style.STROKE); q.setStrokeWidth(2.4f * u);
                    q.setColor(dark); c.drawOval(new android.graphics.RectF(cx - rx, ccy - ry, cx + rx, ccy + ry), q);
                    q.setStrokeWidth(1.4f * u); q.setColor(gold); c.drawOval(new android.graphics.RectF(cx - rx, ccy - ry, cx + rx, ccy + ry), q);
                    q.setStyle(Paint.Style.FILL);
                } else {
                    q.setColor(dark); c.drawOval(new android.graphics.RectF(cx - rx, ccy - ry, cx + rx, ccy + ry), q);
                    q.setColor(gold); c.drawOval(new android.graphics.RectF(cx - rx * 0.8f, ccy - ry * 0.85f, cx + rx * 0.8f, ccy + ry * 0.85f), q);
                    q.setColor(dark); c.drawRect(cx - rx * 0.12f, ccy - ry * 0.5f, cx + rx * 0.12f, ccy + ry * 0.5f, q);
                }
                q.setColor(th.fg); q.setTextSize(7 * u);
                c.drawText("× " + left, cx, cy + 13 * u, q);
                break;
            }
            case 10: case 11: { // Velocímetro (uso) y combustible (lo que queda)
                boolean fuel = m == 11;
                float r = 16 * u, ccy = cy + 6 * u;
                android.graphics.RectF arc = new android.graphics.RectF(cx - r, ccy - r, cx + r, ccy + r);
                q.setStyle(Paint.Style.STROKE); q.setStrokeWidth(2.2f * u);
                q.setColor(th.track); c.drawArc(arc, 180, 180, false, q);
                q.setColor(fuel ? th.high : th.high); c.drawArc(arc, fuel ? 180 : 342, 18, false, q);
                q.setStrokeWidth(0.6f * u); q.setColor(th.fg);
                for (int i = 0; i <= 10; i++) {
                    double a = Math.PI + Math.PI * i / 10;
                    float r0 = r - (i % 5 == 0 ? 4 : 2) * u;
                    c.drawLine(cx + (float) Math.cos(a) * r0, ccy + (float) Math.sin(a) * r0, cx + (float) Math.cos(a) * (r - u), ccy + (float) Math.sin(a) * (r - u), q);
                }
                q.setStyle(Paint.Style.FILL);
                float frac = (fuel ? left : v) / 100f;
                double a = Math.PI + Math.PI * frac;
                q.setStrokeWidth(1.2f * u); q.setColor(th.accent);
                c.drawLine(cx, ccy, cx + (float) Math.cos(a) * (r - 3 * u), ccy + (float) Math.sin(a) * (r - 3 * u), q);
                c.drawCircle(cx, ccy, 1.8f * u, q);
                q.setColor(th.fg); q.setTextSize(4.5f * u);
                c.drawText(fuel ? "E" : "0", cx - r + 2 * u, ccy + 5 * u, q);
                c.drawText(fuel ? "F" : "100", cx + r - 3 * u, ccy + 5 * u, q);
                q.setTextSize(9 * u);
                c.drawText(fuel ? left + "%" : v + "%", cx, ccy - 5 * u, q);
                break;
            }
        }
        if (animOn && (m == 6 || m == 7 || m == 8 || m == 9)) Fps.next(this);
    }

    /** Alpha de un color (evita importar Color solo para esto). */
    private static final class Color2 { static int alpha(int c) { return c >>> 24; } }
}
