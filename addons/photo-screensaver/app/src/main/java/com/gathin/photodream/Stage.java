package com.gathin.photodream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Where the pictures are drawn: a SurfaceView whose buffer is the panel's real size (3840x2160), not the 1920x1080
 * the TV's UI layer runs at, so a 4K picture reaches the screen with all its pixels. A render thread draws the
 * incoming picture over the outgoing one with the slow zoom and drift (Ken Burns) and the crossfade between them.
 */
final class Stage extends SurfaceView implements SurfaceHolder.Callback {

    private static final long FRAME_MS = 33;   // about 30 frames a second: slow motion, and a 4K buffer is heavy

    /** One picture with its motion: scale and offset (in buffer pixels) from start to end over durationMs. */
    private static final class Layer {
        final Bitmap bitmap;
        /** A tiny, blurred copy of the picture (null: none); stretched back up it shows through the cards. */
        final Bitmap blur;
        final long start;
        final long durationMs;
        final float scaleFrom, scaleTo, dxFrom, dxTo, dyFrom, dyTo;

        Layer(Bitmap bitmap, Bitmap blur, long start, long durationMs, float scaleFrom, float scaleTo,
              float dxFrom, float dxTo, float dyFrom, float dyTo) {
            this.bitmap = bitmap;
            this.blur = blur;
            this.start = start;
            this.durationMs = Math.max(1, durationMs);
            this.scaleFrom = scaleFrom;
            this.scaleTo = scaleTo;
            this.dxFrom = dxFrom;
            this.dxTo = dxTo;
            this.dyFrom = dyFrom;
            this.dyTo = dyTo;
        }
    }

    private final Object lock = new Object();
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final int bufferW;
    private final int bufferH;
    private final long fadeMs;
    private Layer front;
    private Layer back;
    private Thread thread;
    private volatile boolean drawing;
    private final android.graphics.Path clip = new android.graphics.Path();
    private final RectF cardRect = new RectF();
    /** Where the cards stand, in buffer pixels, and their corner radius. */
    private RectF[] cards = new RectF[0];
    private float cardRadius;

    Stage(Context context, int bufferW, int bufferH, long fadeMs) {
        super(context);
        this.bufferW = bufferW;
        this.bufferH = bufferH;
        this.fadeMs = fadeMs;
        getHolder().setFixedSize(bufferW, bufferH);
        getHolder().addCallback(this);
    }

    /** Fades this picture in over the current one; the zoom and drift run for durationMs. */
    void show(Bitmap bitmap, Bitmap blur, float scaleFrom, float scaleTo, float dxFrom, float dxTo, float dyFrom, float dyTo,
              long durationMs) {
        Bitmap drop = null;
        synchronized (lock) {
            if (back != null) drop = back.bitmap;
            back = front;
            front = new Layer(bitmap, blur, SystemClock.uptimeMillis(), durationMs, scaleFrom, scaleTo, dxFrom, dxTo,
                    dyFrom, dyTo);
        }
        if (drop != null) drop.recycle();
    }

    /** The cards' places in window pixels; the picture is drawn blurred inside them. windowWidth scales them to the buffer. */
    void setBlurRects(java.util.List<RectF> rects, float radiusPx, int windowWidth) {
        float k = windowWidth > 0 ? bufferW / (float) windowWidth : 1f;
        RectF[] out = new RectF[rects.size()];
        for (int i = 0; i < out.length; i++) {
            RectF r = rects.get(i);
            out[i] = new RectF(r.left * k, r.top * k, r.right * k, r.bottom * k);
        }
        synchronized (lock) {
            cards = out;
            cardRadius = radiusPx * k;
        }
    }

    /** Lets go of every picture (a clip is about to play, or the screensaver stops). */
    void clear() {
        Bitmap a = null, b = null;
        synchronized (lock) {
            if (front != null) a = front.bitmap;
            if (back != null) b = back.bitmap;
            front = null;
            back = null;
        }
        if (a != null) a.recycle();
        if (b != null) b.recycle();
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        drawing = true;
        thread = new Thread(this::run, "stage");
        thread.start();
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        drawing = false;
        Thread t = thread;
        thread = null;
        if (t != null) {
            t.interrupt();
            try {
                t.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void run() {
        SurfaceHolder holder = getHolder();
        while (drawing) {
            long begin = SystemClock.uptimeMillis();
            Canvas canvas = null;
            try {
                canvas = holder.lockHardwareCanvas();
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK);
                    drawFrame(canvas, begin);
                }
            } catch (RuntimeException | OutOfMemoryError e) {
                // The surface went away mid-frame; the loop ends with it.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (RuntimeException ignored) {
                        // Same.
                    }
                }
            }
            long wait = FRAME_MS - (SystemClock.uptimeMillis() - begin);
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private void drawFrame(Canvas canvas, long now) {
        Bitmap finished = null;
        synchronized (lock) {
            if (front == null) return;
            float fade = Math.min(1f, (now - front.start) / (float) fadeMs);
            if (back != null) {
                if (fade >= 1f) {
                    finished = back.bitmap;
                    back = null;
                } else {
                    draw(canvas, back, now, 255, false);
                }
            }
            draw(canvas, front, now, Math.round(255 * fade), false);
            // The same pictures again, blurred, but only inside the cards' shapes.
            for (RectF r : cards) {
                clip.reset();
                clip.addRoundRect(r, cardRadius, cardRadius, android.graphics.Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clip);
                if (back != null) draw(canvas, back, now, 255, true);
                draw(canvas, front, now, Math.round(255 * fade), true);
                canvas.restore();
            }
        }
        if (finished != null) finished.recycle();
    }

    private void draw(Canvas canvas, Layer layer, long now, int alpha, boolean blurred) {
        if (alpha <= 0 || layer.bitmap.isRecycled()) return;
        if (blurred && (layer.blur == null || layer.blur.isRecycled())) return;
        float t = Math.max(0f, Math.min(1f, (now - layer.start) / (float) layer.durationMs));
        float zoom = layer.scaleFrom + (layer.scaleTo - layer.scaleFrom) * t;
        float dx = layer.dxFrom + (layer.dxTo - layer.dxFrom) * t;
        float dy = layer.dyFrom + (layer.dyTo - layer.dyFrom) * t;
        int w = layer.bitmap.getWidth(), h = layer.bitmap.getHeight();
        float cover = Math.max((float) bufferW / w, (float) bufferH / h);   // CENTER_CROP
        paint.setAlpha(alpha);
        // The blur behind a light card is taken down a little, so white text on it still reads over snow or sky.
        paint.setColorFilter(blurred ? new android.graphics.LightingColorFilter(0xFFC4C4C4, 0x000000) : null);
        canvas.save();
        canvas.translate(bufferW / 2f + dx, bufferH / 2f + dy);
        canvas.scale(cover * zoom, cover * zoom);
        canvas.translate(-w / 2f, -h / 2f);
        if (blurred) {
            cardRect.set(0, 0, w, h);
            canvas.drawBitmap(layer.blur, null, cardRect, paint);
        } else {
            canvas.drawBitmap(layer.bitmap, 0, 0, paint);
        }
        canvas.restore();
    }
}
