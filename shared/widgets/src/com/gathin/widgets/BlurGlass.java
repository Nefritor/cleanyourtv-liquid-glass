package com.gathin.widgets;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

/**
 * A card's background: what is behind it, blurred, with the glass on top. The blurred picture is a small snapshot of the
 * screen's backdrop (the app provides it every second or so), stretched back up with filtering, which blurs it without
 * any per-frame work; the card's own place in the window picks the part to show.
 */
final class BlurGlass extends Drawable {
    private final View card;
    private final Widgets.Backdrop backdrop;
    private final GradientDrawable glass;
    private final float radius;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private final RectF bounds = new RectF();
    private final Rect src = new Rect();
    private final int[] where = new int[2];

    BlurGlass(View card, Widgets.Backdrop backdrop, GradientDrawable glass, float radius) {
        this.card = card;
        this.backdrop = backdrop;
        this.glass = glass;
        this.radius = radius;
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        Bitmap snap = backdrop.snapshot();
        if (snap != null && !snap.isRecycled() && backdrop.screenWidth() > 0 && backdrop.screenHeight() > 0) {
            card.getLocationInWindow(where);
            float sx = snap.getWidth() / (float) backdrop.screenWidth();
            float sy = snap.getHeight() / (float) backdrop.screenHeight();
            src.set(Math.max(0, Math.round(where[0] * sx)), Math.max(0, Math.round(where[1] * sy)),
                    Math.min(snap.getWidth(), Math.round((where[0] + b.width()) * sx)),
                    Math.min(snap.getHeight(), Math.round((where[1] + b.height()) * sy)));
            if (src.width() > 0 && src.height() > 0) {
                bounds.set(b);
                clip.reset();
                clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clip);
                canvas.drawBitmap(snap, src, b, paint);
                canvas.restore();
            }
        }
        glass.setBounds(b);
        glass.draw(canvas);
    }

    @Override public void setAlpha(int alpha) {}

    @Override public void setColorFilter(ColorFilter colorFilter) {}

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
