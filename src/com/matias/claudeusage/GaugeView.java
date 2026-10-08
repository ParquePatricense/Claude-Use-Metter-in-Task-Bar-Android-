package com.matias.claudeusage;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Medidor principal: arco, batería, corazones o mascota. Animado y con pulso al límite. */
class GaugeView extends View {
    static final String[] METERS = {"Arco", "Batería retro", "Corazones", "Mascota"};

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
                if (animOn) postInvalidateOnAnimation();
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
        if (meter == 1 || meter == 2) r = (u.hasData() ? "Te queda " + (100 - u.pct) + "% · " : "") + r;
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
        if (pulseOn) postInvalidateOnAnimation();
    }

    /** Alpha de un color (evita importar Color solo para esto). */
    private static final class Color2 { static int alpha(int c) { return c >>> 24; } }
}
