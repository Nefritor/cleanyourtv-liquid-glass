package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Behind Home, the focused app's own pictures (AppArt) taking turns: each fades in over the last with a slow push-in.
 * Two image views and at most two decoded pictures in memory; nothing runs while Home is out of sight.
 */
final class AppArtView extends FrameLayout {
    private static final long HOLD_MS = 9000, FADE_MS = 1400;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final ImageView[] views = new ImageView[2];
    private final float maxAlpha;
    private final int width;
    private List<File> files = new ArrayList<>();
    private String pkg;
    private int next;
    private int front;
    private int generation;
    private boolean running;

    AppArtView(Context context, float maxAlpha) {
        super(context);
        this.maxAlpha = maxAlpha;
        this.width = Math.min(1280, context.getResources().getDisplayMetrics().widthPixels);
        for (int i = 0; i < 2; i++) {
            ImageView v = new ImageView(context);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setAlpha(0f);
            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(v, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            views[i] = v;
        }
        setVisibility(GONE);
    }

    /** The focused tile's app, or null; a different app starts its own turn-taking, the same one carries on. */
    void show(String packageName) {
        if (packageName != null && packageName.equals(pkg)) return;
        pkg = packageName;
        generation++;
        handler.removeCallbacksAndMessages(null);
        List<File> found = pkg == null ? new ArrayList<File>() : AppArt.files(getContext(), pkg);
        Collections.shuffle(found);
        files = found;
        next = 0;
        if (files.isEmpty()) {
            fadeOutAll();
            return;
        }
        setVisibility(VISIBLE);
        if (running) advance();
    }

    void start() {
        running = true;
        if (!files.isEmpty()) advance();
    }

    void stop() {
        running = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
        for (ImageView v : views) {
            v.animate().cancel();
            v.setAlpha(0f);
            v.setImageDrawable(null);
        }
    }

    private void fadeOutAll() {
        for (ImageView v : views) v.animate().alpha(0f).setDuration(500).withEndAction(() -> v.setImageDrawable(null)).start();
    }

    private void advance() {
        if (!running || files.isEmpty()) return;
        final int gen = generation;
        final File file = files.get(next++ % files.size());
        decoder.execute(() -> {
            BitmapFactory.Options b = new BitmapFactory.Options();
            b.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), b);
            int sample = 1;
            while (b.outWidth / (sample * 2) >= width) sample *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            final Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), o);
            handler.post(() -> {
                if (gen != generation || !running || bitmap == null) return;
                crossfade(bitmap);
                if (files.size() > 1) handler.postDelayed(this::advance, HOLD_MS);
            });
        });
    }

    private void crossfade(Bitmap bitmap) {
        ImageView incoming = views[1 - front];
        ImageView outgoing = views[front];
        front = 1 - front;
        incoming.animate().cancel();
        incoming.setImageBitmap(bitmap);
        incoming.setScaleX(1f);
        incoming.setScaleY(1f);
        incoming.setAlpha(0f);
        android.animation.ObjectAnimator.ofFloat(incoming, View.ALPHA, 0f, maxAlpha).setDuration(FADE_MS).start();
        incoming.animate().scaleX(1.06f).scaleY(1.06f).setDuration(HOLD_MS + 2 * FADE_MS).start();
        outgoing.animate().alpha(0f).setDuration(FADE_MS).start();
    }
}
