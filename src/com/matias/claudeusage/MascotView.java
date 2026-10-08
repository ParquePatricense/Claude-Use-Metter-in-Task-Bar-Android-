package com.matias.claudeusage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** Mascota pixel art animada a 60 FPS. Al tocarla salta, suena y tira corazones. */
class MascotView extends View {
    private static final long REACT_MS = 650;
    private int pct = -1;
    private Theme th = Theme.cur;
    private final Paint pix = new Paint(), p = new Paint();
    private final float d;
    private long reactAt = -1;

    MascotView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        pix.setFilterBitmap(false);
    }

    void set(int pct, Theme th) { this.pct = pct; this.th = th; }

    @Override
    protected void onMeasure(int ws, int hs) { setMeasuredDimension(MeasureSpec.getSize(ws), (int) (110 * d)); }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            reactAt = SystemClock.uptimeMillis();
            Sfx.play(getContext(), Sfx.TAP);
            if (Prefs.get(getContext()).getBoolean("haptic", true)) performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            invalidate();
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas c) {
        boolean anim = Prefs.get(getContext()).getBoolean("anim", true);
        long now = SystemClock.uptimeMillis();
        float react = reactAt >= 0 && now - reactAt < REACT_MS ? (now - reactAt) / (float) REACT_MS : -1;
        float h = getHeight() * 0.72f;
        RectF box = new RectF(getWidth() / 2f - h / 2, getHeight() * 0.22f, getWidth() / 2f + h / 2, getHeight() * 0.22f + h);
        Mascots.draw(getContext(), c, box, th, pct, now, anim, pix, react);
        if (react >= 0) {
            // Corazones pixel que suben
            float u = h / 16f;
            String[] heart = {".11.11.", "1111111", ".11111.", "..111..", "...1..."};
            for (int i = 0; i < 3; i++) {
                float hx = box.centerX() + (i - 1) * 6 * u, hy = box.top - react * 6 * u + (i % 2) * u;
                p.setColor(Art.fade(Color.parseColor("#F27BA0"), (int) (255 * (1 - react))));
                float k = u * 0.5f;
                for (int y = 0; y < heart.length; y++) for (int x = 0; x < 7; x++)
                    if (heart[y].charAt(x) == '1') c.drawRect(hx + x * k, hy + y * k, hx + (x + 1) * k, hy + (y + 1) * k, p);
            }
        }
        if (anim || react >= 0) postInvalidateOnAnimation();
    }
}
