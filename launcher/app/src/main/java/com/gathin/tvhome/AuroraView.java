package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A slowly shifting field of coloured light behind Home (theme keys aurora, aurora_colors, aurora_speed):
 * a few big radial gradients drift on long loops and blend with SCREEN. No bitmaps, so it costs no memory, and
 * it draws at about 30 frames a second only while Home is on screen.
 */
final class AuroraView extends View {
    private static final int FRAME_MS = 33;
    private static final int[] DEFAULT_COLORS = {0xFF2BD68C, 0xFF3C7CFF, 0xFF7A5CFF, 0xFF1FB8D6};

    private final int[] colors;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shader[] shaders;
    private final Matrix matrix = new Matrix();
    private final float speed;
    private final boolean still;
    private long t0;
    private boolean running;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    private AuroraView(Context ctx, int[] colors, float speed, boolean still) {
        super(ctx);
        this.colors = colors;
        this.speed = speed;
        this.still = still;
        shaders = new Shader[colors.length];
        for (int i = 0; i < colors.length; i++) {
            int c = colors[i] & 0x00FFFFFF;
            shaders[i] = new RadialGradient(0, 0, 1000f,
                    new int[]{0xB0000000 | c, 0x60000000 | c, c},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        }
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        setWillNotDraw(false);
    }

    /** The colour schemes the owner can pick besides the theme's own (Backdrop settings). */
    static int[] scheme(String name) {
        if (name == null) return null;
        switch (name) {
            case "sunset": return new int[] {0xFFFF7A59, 0xFFFFB347, 0xFFE0457B, 0xFF7A3CFF};
            case "ocean": return new int[] {0xFF1F6FEB, 0xFF14B8C6, 0xFF0B3A8C, 0xFF3DDBD0};
            case "forest": return new int[] {0xFF2BD66C, 0xFF0E8F5A, 0xFF9AD13A, 0xFF127A8C};
            case "violet": return new int[] {0xFF8A5CFF, 0xFFE04FD6, 0xFF4A3CFF, 0xFFFF6FB0};
            case "ember": return new int[] {0xFFFF4D2E, 0xFFFF9A1F, 0xFFB3142B, 0xFF5A1AA8};
            case "graphite": return new int[] {0xFF6E7681, 0xFF3E4650, 0xFF9AA4AF, 0xFF2A3038};
            default: return null;
        }
    }

    /** The aurora colours the theme sets (or the defaults). */
    static int[] themeColors(Context ctx) {
        JSONObject json = Config.overlay(ctx);
        JSONArray list = json == null ? null : json.optJSONArray("aurora_colors");
        if (list != null && list.length() > 0) {
            int[] parsed = new int[Math.min(6, list.length())];
            try {
                for (int i = 0; i < parsed.length; i++) parsed[i] = Color.parseColor(list.getString(i).trim()) | 0xFF000000;
                return parsed;
            } catch (Exception ignored) { }
        }
        return DEFAULT_COLORS;
    }

    /** A still picture of an aurora in these colours, for the background picker. */
    static View preview(Context ctx, int[] colors) {
        AuroraView v = new AuroraView(ctx, colors, 1f, true);
        v.t0 = 1;
        return v;
    }

    /**
     * Puts the aurora at the back of root; returns it, or null when off. The owner's choice (Backdrop settings) wins:
     * a colour scheme, the theme's own aurora (aurora, appart) or none (stars, wallpaper). Without a choice the theme decides.
     */
    static AuroraView attach(Context ctx, FrameLayout root, boolean reducedMotion) {
        JSONObject json = Config.overlay(ctx);
        String choice = Config.backdrop(ctx);
        int[] colors;
        if (choice == null) {
            if (json == null || !json.optBoolean("aurora", false)) return null;
            colors = themeColors(ctx);
        } else if (scheme(choice) != null) {
            colors = scheme(choice);
        } else if ("aurora".equals(choice) || "appart".equals(choice)) {
            colors = themeColors(ctx);
        } else {
            return null;
        }
        int pct = json == null ? 100 : json.optInt("aurora_speed", 100);
        float speed = Math.max(10, Math.min(400, pct)) / 100f;
        AuroraView v = new AuroraView(ctx, colors, speed, reducedMotion);
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
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        double t = still ? 0 : (System.nanoTime() - t0) / 1e9 * speed;
        int n = colors.length;
        for (int i = 0; i < n; i++) {
            // Each blob has its own slow period (about 50 to 110 s) and orbit, so the pattern rarely repeats.
            double a = t * (2 * Math.PI / (50 + 17 * i)) + i * 1.9;
            double b = t * (2 * Math.PI / (70 + 13 * i)) + i * 2.7;
            float cx = (float) (w * (0.5 + 0.42 * Math.sin(a)));
            float cy = (float) (h * (0.5 + 0.38 * Math.cos(b)));
            float r = Math.max(w, h) * (0.42f + 0.07f * (float) Math.sin(t * 0.07 + i));
            matrix.setScale(r / 1000f, r / 1000f);
            matrix.postTranslate(cx, cy);
            shaders[i].setLocalMatrix(matrix);
            paint.setShader(shaders[i]);
            canvas.drawCircle(cx, cy, r, paint);
        }
    }
}
