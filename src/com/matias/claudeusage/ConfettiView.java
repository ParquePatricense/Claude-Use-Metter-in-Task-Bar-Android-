package com.matias.claudeusage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/** Confeti pixelado para festejar el reinicio de la sesión. No intercepta toques. */
class ConfettiView extends View {
    private static final int N = 90;
    private static final long DURATION = 2800;
    private final float[] x = new float[N], y = new float[N], vx = new float[N], vy = new float[N], size = new float[N];
    private final int[] color = new int[N];
    private final Paint p = new Paint();
    private long start = -1;
    private final float d;

    ConfettiView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
    }

    void burst(Theme t) {
        Random r = new Random();
        int[] cols = {t.accent, t.low, t.mid, t.high, t.fg};
        for (int i = 0; i < N; i++) {
            x[i] = getWidth() / 2f + (r.nextFloat() - 0.5f) * getWidth() * 0.3f;
            y[i] = getHeight() * 0.3f;
            vx[i] = (r.nextFloat() - 0.5f) * 900 * d / 2.6f;
            vy[i] = -(300 + r.nextFloat() * 700) * d / 2.6f;
            size[i] = (3 + r.nextInt(4)) * d;
            color[i] = cols[r.nextInt(cols.length)];
        }
        start = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (start < 0) return;
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        if (t * 1000 > DURATION) { start = -1; return; }
        float g = 1400 * d / 2.6f;
        int alpha = (int) (255 * Math.min(1f, (DURATION / 1000f - t) / 0.8f));
        for (int i = 0; i < N; i++) {
            float px = x[i] + vx[i] * t, py = y[i] + vy[i] * t + g * t * t / 2;
            // Posiciones en grilla para que se vea pixelado
            float s = size[i];
            px = Math.round(px / s) * s;
            py = Math.round(py / s) * s;
            p.setColor(Art.fade(color[i], Math.max(0, alpha)));
            c.drawRect(px, py, px + s, py + s, p);
        }
        Fps.next(this);
    }
}
