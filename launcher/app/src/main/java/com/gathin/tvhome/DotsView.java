package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * The dock position under the app's name: a row of still dots and one white accent dot standing on the current one.
 * Moving the focus stretches the accent from its dot out to the next one, and once it has arrived the tail follows,
 * narrowing back to a dot. Moving left is the mirror image. Everything is drawn here, so nothing is laid out per frame.
 */
public final class DotsView extends View {
    private static final long STRETCH_MS = 380;

    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float dot;
    private final float pitch;
    private int count;
    private int slot;
    /** The accent's left and right edges, in px from this view's left edge. */
    private float left, right;
    private ValueAnimator animator;

    public DotsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = context.getResources().getDisplayMetrics().density;
        dot = 6 * density;
        pitch = 16 * density;
        dim.setColor(0x4DFFFFFF);
        accent.setColor(0xFFFFFFFF);
    }

    /** How many dots, and which one is the accent; the accent is placed without moving. */
    void setDots(int count, int slot) {
        this.count = Math.max(0, count);
        this.slot = Math.max(0, Math.min(this.count - 1, slot));
        cancel();
        left = this.slot * pitch;
        right = left + dot;
        requestLayout();
        invalidate();
    }

    int count() {
        return count;
    }

    int slot() {
        return slot;
    }

    /** Moves the accent to another dot with the stretch-and-follow motion (or at once). */
    void moveTo(int target, boolean animate) {
        target = Math.max(0, Math.min(count - 1, target));
        if (target == slot) return;
        final int from = slot;
        final int to = target;
        slot = target;
        cancel();
        final float fromLeft = left, fromRight = right;
        final float toLeft = to * pitch, toRight = toLeft + dot;
        if (!animate) {
            left = toLeft;
            right = toRight;
            invalidate();
            return;
        }
        final boolean forward = to > from;
        final AccelerateDecelerateInterpolator ease = new AccelerateDecelerateInterpolator();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(STRETCH_MS);
        animator.addUpdateListener(a -> {
            float p = (float) a.getAnimatedValue();
            float lead = ease.getInterpolation(Math.min(1f, p * 2f));        // the edge that goes first
            float tail = ease.getInterpolation(Math.max(0f, p * 2f - 1f));   // then the one that stayed behind
            if (forward) {
                right = fromRight + (toRight - fromRight) * lead;
                left = fromLeft + (toLeft - fromLeft) * tail;
            } else {
                left = fromLeft + (toLeft - fromLeft) * lead;
                right = fromRight + (toRight - fromRight) * tail;
            }
            invalidate();
        });
        animator.start();
    }

    private void cancel() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = count == 0 ? 0 : Math.round((count - 1) * pitch + dot);
        setMeasuredDimension(w, Math.round(dot));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = dot / 2f;
        for (int i = 0; i < count; i++) {
            float x = i * pitch;
            rect.set(x, 0, x + dot, dot);
            canvas.drawRoundRect(rect, r, r, dim);
        }
        rect.set(left, 0, right, dot);
        canvas.drawRoundRect(rect, r, r, accent);
    }

    @Override
    protected void onDetachedFromWindow() {
        cancel();
        super.onDetachedFromWindow();
    }
}
