package com.matias.claudeusage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Mapa de calor estilo GitHub: días de la semana × horas, con cuadraditos pixel. */
class HeatmapView extends View {
    private static final String[] DAYS = {"L", "M", "X", "J", "V", "S", "D"};
    private float[][] data = new float[7][24];
    private Theme th = Theme.cur;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    HeatmapView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    void set(float[][] data, Theme th) { this.data = data; this.th = th; invalidate(); }

    @Override
    protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws);
        float cell = (w - 52 * d) / 24f;
        setMeasuredDimension(w, (int) (cell * 7 + 60 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        float l = 36 * d, cell = (getWidth() - l - 16 * d) / 24f, t = 28 * d, gap = Math.max(1, d);
        float max = 0;
        for (float[] r : data) for (float v : r) max = Math.max(max, v);
        p.setTypeface(Theme.font(false));
        p.setColor(th.fg);
        p.setTextSize(13 * d);
        c.drawText(L.t("Cuándo usás Claude · 4 semanas"), l, 16 * d, p);
        p.setTextSize(10 * d);
        for (int dIdx = 0; dIdx < 7; dIdx++) {
            p.setColor(th.dim);
            c.drawText(DAYS[dIdx], 14 * d, t + dIdx * cell + cell * 0.75f, p);
            for (int h = 0; h < 24; h++) {
                float v = max > 0 ? data[dIdx][h] / max : 0;
                int col = v <= 0 ? th.track : Art.fade(th.accent, (int) (70 + 185 * v));
                p.setColor(col);
                float x = l + h * cell, y = t + dIdx * cell;
                c.drawRect(x, y, x + cell - gap, y + cell - gap, p);
            }
        }
        p.setColor(th.dim);
        for (int h = 0; h < 24; h += 6) c.drawText(h + "h", l + h * cell, t + 7 * cell + 14 * d, p);
        if (max <= 0) {
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(L.t("Se completa a medida que usás Claude"), getWidth() / 2f, t + 3.6f * cell, p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }
}
