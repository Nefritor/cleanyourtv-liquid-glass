package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.Random;

/**
 * A night sky behind Home (the "Stars" background): a deep blue gradient and a few hundred stars that twinkle
 * slowly. Plain circles, no bitmaps, and about 15 frames a second only while Home is on screen.
 */
final class StarsView extends View {
    private static final int FRAME_MS = 66;
    private static final int COUNT = 220;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] x = new float[COUNT], y = new float[COUNT], radius = new float[COUNT];
    private final float[] period = new float[COUNT], phase = new float[COUNT];
    private final boolean still;
    private Shader sky;
    private long t0;
    private boolean running;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    StarsView(Context ctx, boolean still) {
        super(ctx);
        this.still = still;
        float d = ctx.getResources().getDisplayMetrics().density;
        Random r = new Random(7);
        for (int i = 0; i < COUNT; i++) {
            x[i] = r.nextFloat();
            y[i] = r.nextFloat();
            // Mostly small stars, a few bright ones.
            radius[i] = (r.nextFloat() < 0.08f ? 2.1f + r.nextFloat() : 0.7f + r.nextFloat() * 0.9f) * d;
            period[i] = 3f + r.nextFloat() * 7f;
            phase[i] = r.nextFloat() * 6.283f;
        }
        setWillNotDraw(false);
    }

    static StarsView attach(Context ctx, FrameLayout root, boolean reducedMotion) {
        if (!"stars".equals(Config.backdrop(ctx))) return null;
        StarsView v = new StarsView(ctx, reducedMotion);
        root.addView(v, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return v;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) start(); else stop();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getWindowVisibility() == VISIBLE) start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void start() {
        if (running) return;
        running = true;
        if (t0 == 0) t0 = System.nanoTime();
        invalidate();
        if (!still) postDelayed(tick, FRAME_MS);
    }

    private void stop() {
        running = false;
        removeCallbacks(tick);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        sky = new LinearGradient(0, 0, w * 0.4f, h, new int[] {0xFF03050C, 0xFF0B1330, 0xFF14234A}, new float[] {0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        paint.setShader(sky);
        paint.setAlpha(255);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        double t = still ? 0 : (System.nanoTime() - t0) / 1e9;
        for (int i = 0; i < COUNT; i++) {
            float twinkle = 0.5f + 0.5f * (float) Math.sin(t * 6.283 / period[i] + phase[i]);
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha((int) (255 * (0.3f + 0.7f * twinkle)));
            canvas.drawCircle(x[i] * w, y[i] * h, radius[i] * (0.85f + 0.3f * twinkle), paint);
        }
    }
}
