package com.matias.claudeusage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Barra de tiempo: cuánto pasó de la ventana (5 h o 7 días). Solo redibuja si cambia lo que se ve. */
final class TimeBarView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private String label = "", right = "";
    private float frac = -1;
    private Theme th;

    TimeBarView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    void set(String label, String right, float frac, Theme t) {
        frac = Math.max(0, Math.min(1, frac));
        if (label.equals(this.label) && right.equals(this.right) && Math.abs(frac - this.frac) < 0.002f && t == th) return;
        this.label = label;
        this.right = right;
        this.frac = frac;
        th = t;
        setVisibility(VISIBLE);
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int) (36 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (th == null) return;
        float x0 = 24 * d, x1 = getWidth() - 24 * d, y = 14 * d;
        p.setTypeface(Theme.font(false));
        p.setTextSize(13 * d);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(th.dim);
        c.drawText(label, x0, y, p);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(th.accent);
        c.drawText(right, x1, y, p);
        float by = y + 8 * d, bh = 6 * d, rad = Theme.pixel ? 0 : bh / 2;
        p.setColor(th.track);
        c.drawRoundRect(new RectF(x0, by, x1, by + bh), rad, rad, p);
        if (frac > 0) {
            p.setColor(th.accent);
            c.drawRoundRect(new RectF(x0, by, Math.max(x0 + bh, x0 + (x1 - x0) * frac), by + bh), rad, rad, p);
        }
    }
}
