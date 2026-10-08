package com.matias.claudeusage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Gráfico de sesión (naranja) y semana. Tocar/arrastrar muestra el valor exacto. */
class ChartView extends View {
    private List<History.Point> pts;
    private long span = History.DAY;
    private Theme theme = Theme.cur;
    private float selX = -1, prog = 1f;
    private boolean drawn;
    private final float d;

    ChartView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    void set(List<History.Point> pts, long span, Theme theme) {
        boolean animate = !drawn || span != this.span;
        this.pts = pts; this.span = span; this.theme = theme;
        drawn = true;
        if (animate && Prefs.get(getContext()).getBoolean("anim", true)) {
            // La línea se dibuja de izquierda a derecha
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(900);
            a.setInterpolator(new android.view.animation.DecelerateInterpolator());
            a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(android.animation.ValueAnimator v) { prog = (Float) v.getAnimatedValue(); invalidate(); }
            });
            a.start();
        } else {
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        setMeasuredDimension(MeasureSpec.getSize(ws), (int) (220 * d));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                selX = e.getX();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                postDelayed(new Runnable() { public void run() { selX = -1; invalidate(); } }, 2500);
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas c) { draw(c, getWidth(), getHeight(), d, pts, span, theme, selX, prog); }

    static Bitmap bitmap(int w, int h, float d, List<History.Point> pts, Theme th) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        draw(new Canvas(bmp), w, h, d, pts, History.DAY, th, -1, 1f);
        return bmp;
    }

    static void draw(Canvas c, int width, int height, float d, List<History.Point> pts, long span, Theme th, float selX, float prog) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Theme.font(false));
        float l = 40 * d, r = width - 16 * d, t = 30 * d, b = height - 24 * d;
        long now = System.currentTimeMillis(), start = now - span;
        boolean week = span > History.DAY;

        p.setTextSize(13 * d);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(th.fg);
        c.drawText(week ? "Últimos 7 días" : "Últimas 24 h", l, 14 * d, p);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(th.accent);
        c.drawText("● Sesión", r - 80 * d, 14 * d, p);
        p.setColor(th.dim);
        c.drawText("● Semana", r, 14 * d, p);

        p.setTextSize(11 * d);
        for (int v = 0; v <= 100; v += 50) {
            float y = b - (b - t) * v / 100f;
            p.setColor(th.track);
            c.drawRect(l, y, r, y + d, p);
            p.setColor(th.dim);
            p.setTextAlign(Paint.Align.RIGHT);
            c.drawText(v + "%", l - 6 * d, y + 4 * d, p);
        }
        p.setTextAlign(Paint.Align.CENTER);
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEE d", new Locale("es"));
        int steps = week ? 7 : 4;
        for (int i = steps; i >= 0; i--) {
            long ts = now - span * i / steps;
            float x = l + (r - l) * (ts - start) / (float) span;
            String lab = i == 0 ? "ahora" : week
                    ? Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(dayFmt) : Usage.clock(ts);
            c.drawText(lab, x, height - 6 * d, p);
        }

        if (pts == null || pts.isEmpty()) {
            p.setColor(th.dim);
            p.setTextSize(13 * d);
            c.drawText("Se va llenando solo a medida que usás Claude", (l + r) / 2, (t + b) / 2, p);
            return;
        }
        long gap = week ? 60 * 60 * 1000 : 20 * 60 * 1000;
        c.save();
        c.clipRect(0, 0, l + (r - l) * prog + 4 * d, height);
        line(c, p, pts, l, r, t, b, start, span, gap, false, th.dim, 1.5f * d);
        line(c, p, pts, l, r, t, b, start, span, gap, true, th.accent, 2.5f * d);
        c.restore();

        if (selX >= l && selX <= r) {
            long ts = start + (long) ((selX - l) / (r - l) * span);
            History.Point best = null;
            for (History.Point q : pts) if (best == null || Math.abs(q.ts - ts) < Math.abs(best.ts - ts)) best = q;
            if (best == null || Math.abs(best.ts - ts) > gap) return;
            float x = l + (r - l) * (best.ts - start) / (float) span;
            float y = b - (b - t) * best.pct / 100f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(th.dim);
            c.drawRect(x - d / 2, t, x + d / 2, b, p);
            p.setColor(th.accent);
            c.drawCircle(x, y, 5 * d, p);
            String when = Instant.ofEpochMilli(best.ts).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern(week ? "EEE d HH:mm" : "HH:mm", new Locale("es")));
            String txt = when + " · Sesión " + best.pct + "% · Semana " + best.week + "%";
            p.setTextSize(12 * d);
            float tw = p.measureText(txt) + 16 * d;
            float bx = Math.max(l, Math.min(r - tw, x - tw / 2));
            p.setColor(th.card);
            c.drawRoundRect(new RectF(bx, t - 4 * d, bx + tw, t + 18 * d), 8 * d, 8 * d, p);
            p.setColor(th.fg);
            p.setTextAlign(Paint.Align.LEFT);
            c.drawText(txt, bx + 8 * d, t + 11 * d, p);
        }
    }

    private static void line(Canvas c, Paint p, List<History.Point> pts, float l, float r, float t, float b,
                             long start, long span, long gapMs, boolean session, int color, float sw) {
        Path stroke = new Path(), fill = new Path();
        long prev = 0;
        float fx = 0, lx = 0;
        boolean open = false;
        for (History.Point q : pts) {
            if (q.ts < start) continue;
            float x = l + (r - l) * (q.ts - start) / (float) span;
            float y = b - (b - t) * (session ? q.pct : q.week) / 100f;
            if (!open || q.ts - prev > gapMs) {
                if (open) { fill.lineTo(lx, b); fill.lineTo(fx, b); fill.close(); }
                stroke.moveTo(x, y);
                fill.moveTo(x, b);
                fill.lineTo(x, y);
                fx = x;
                open = true;
            } else {
                stroke.lineTo(x, y);
                fill.lineTo(x, y);
            }
            lx = x;
            prev = q.ts;
        }
        if (open) { fill.lineTo(lx, b); fill.lineTo(fx, b); fill.close(); }
        if (session) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(50, Color.red(color), Color.green(color), Color.blue(color)));
            c.drawPath(fill, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        c.drawPath(stroke, p);
        p.setStyle(Paint.Style.FILL);
    }
}
