package com.matias.claudeusage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Botón redondo con chevrón grande que gira al abrir/cerrar un desplegable. */
class ChevronView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float angle;

    ChevronView(Context c, Theme th, boolean open) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        fill.setColor(Art.fade(th.accent, 55));
        line.setColor(th.accent);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(3 * d);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        angle = open ? 180 : 0;
    }

    void setOpen(boolean open) {
        animate().cancel();
        float target = open ? 180 : 0;
        if (Prefs.get(getContext()).getBoolean("anim", true)) {
            final float from = angle;
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(from, target);
            a.setDuration(180);
            a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(android.animation.ValueAnimator v) { angle = (Float) v.getAnimatedValue(); invalidate(); }
            });
            a.start();
        } else {
            angle = target;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2f;
        c.drawCircle(w / 2, h / 2, r, fill);
        c.save();
        c.rotate(angle, w / 2, h / 2);
        Path p = new Path();
        p.moveTo(w / 2 - r * 0.42f, h / 2 - r * 0.18f);
        p.lineTo(w / 2, h / 2 + r * 0.24f);
        p.lineTo(w / 2 + r * 0.42f, h / 2 - r * 0.18f);
        c.drawPath(p, line);
        c.restore();
    }
}
