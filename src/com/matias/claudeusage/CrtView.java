package com.matias.claudeusage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** Filtro retro CRT: líneas de barrido, viñeta y un parpadeo suave. No intercepta toques. */
class CrtView extends View {
    private final Paint line = new Paint(), vig = new Paint(), flick = new Paint();
    private final float d;

    CrtView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        line.setColor(Color.argb(38, 0, 0, 0));
        setClickable(false);
        setFocusable(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        vig.setShader(new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.75f,
                new int[]{Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(110, 0, 0, 0)}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float step = 3 * d;
        for (float y = 0; y < h; y += step) c.drawRect(0, y, w, y + d, line);
        c.drawRect(0, 0, w, h, vig);
        if (Prefs.get(getContext()).getBoolean("anim", true)) {
            long t = SystemClock.uptimeMillis();
            // Banda de barrido que baja lento
            float by = (t % 6000) / 6000f * (h + 120 * d) - 60 * d;
            flick.setColor(Color.argb(14, 255, 255, 255));
            c.drawRect(0, by, w, by + 60 * d, flick);
            Fps.next(this);
        }
    }
}
