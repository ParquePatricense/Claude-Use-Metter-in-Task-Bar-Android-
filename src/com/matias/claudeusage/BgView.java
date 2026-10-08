package com.matias.claudeusage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/** Fondo animado pixel a 60 FPS: estrellas, lluvia o nieve. */
class BgView extends View {
    static final String[] KINDS = {"Ninguno", "Estrellas", "Lluvia", "Nieve"};
    private static final int N = 70;
    private final float[] x = new float[N], y = new float[N], sp = new float[N], ph = new float[N];
    private final Paint p = new Paint();
    private final float d;
    private int kind;
    private Theme th;
    private long last;

    BgView(Context c, int kind, Theme th) {
        super(c);
        this.kind = kind;
        this.th = th;
        d = c.getResources().getDisplayMetrics().density;
        Random r = new Random(7);
        for (int i = 0; i < N; i++) { x[i] = r.nextFloat(); y[i] = r.nextFloat(); sp[i] = 0.4f + r.nextFloat(); ph[i] = r.nextFloat() * 6.28f; }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (kind == 0) return;
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        int w = getWidth(), h = getHeight();
        float px = 3 * d;
        boolean anim = Prefs.get(getContext()).getBoolean("anim", true);
        for (int i = 0; i < N; i++) {
            if (anim) {
                if (kind == 1) x[i] -= dt * 0.004f * sp[i];
                else if (kind == 2) { y[i] += dt * 0.9f * sp[i]; x[i] -= dt * 0.08f * sp[i]; }
                else { y[i] += dt * 0.07f * sp[i]; x[i] += (float) Math.sin(now / 900.0 + ph[i]) * dt * 0.02f; }
                if (y[i] > 1) { y[i] -= 1; x[i] = (x[i] + 0.37f) % 1; }
                if (x[i] < 0) x[i] += 1;
                if (x[i] > 1) x[i] -= 1;
            }
            float X = Math.round(x[i] * w / px) * px, Y = Math.round(y[i] * h / px) * px;
            if (kind == 1) {
                int a = (int) (60 + 120 * Math.abs(Math.sin(now / 700.0 * sp[i] + ph[i])));
                p.setColor(Art.fade(th.fg, anim ? a : 120));
                float s = sp[i] > 1.1f ? px : px * 0.66f;
                c.drawRect(X, Y, X + s, Y + s, p);
            } else if (kind == 2) {
                p.setColor(Art.fade(th.accent, 70));
                c.drawRect(X, Y, X + px * 0.5f, Y + px * 3, p);
            } else {
                p.setColor(Art.fade(th.fg, 110));
                c.drawRect(X, Y, X + px, Y + px, p);
            }
        }
        if (anim) postInvalidateOnAnimation();
    }
}
