package com.gathin.photodream;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen mode: loops through the pictures sync.sh put in the app's pictures/ folder, in filename order,
 * each slowly zooming and drifting (Ken Burns) and crossfading into the next after seconds.txt (20 s by
 * default). Two screen-sized pictures at most, held as GPU bitmaps. MP4 clips play once, muted and cropped to
 * fill the screen, on the hardware decoder (one player at a time), then the loop moves on.
 */
final class Slides {

    private static final long FADE_MS = 1500L;
    /** How far a picture zooms in over its time on screen; it drifts at most half the extra width. */
    private static final float ZOOM = 1.12f;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    /** The month and year of the picture on screen, or null when the option is off. */
    private final TextView caption;
    /** Whether a local picture gets its month and year as the caption; online pictures always get their credit. */
    private final boolean dates;
    /** Whether an online picture's author and licence line is shown. */
    private final boolean credits;
    private final View empty;
    private ExecutorService decoder;
    private final Stage surface4k;
    private final TextureView video;
    private MediaPlayer player;
    private Surface surface;
    /** A clip waiting for the TextureView's surface. */
    private File pendingClip;
    /** Bumped for every clip so callbacks of a released player are ignored. */
    private int clipGeneration;
    private final List<File> images;
    private final SharedPreferences preferences;
    private final long interval;
    private final int width;
    private final int height;
    private boolean running;

    private final Runnable advance = this::showNext;

    Slides(Context context, FrameLayout stage, TextView caption, boolean dates, boolean credits, View empty) {
        this.context = context;
        this.dates = dates;
        this.credits = credits;
        this.caption = caption;
        this.empty = empty;
        video = new TextureView(context);
        video.setAlpha(0f);
        video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
                surface = new Surface(texture);
                File clip = pendingClip;
                pendingClip = null;
                if (clip != null && running) playVideo(clip);
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                stopVideo();
                if (surface != null) surface.release();
                surface = null;
                return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
        });
        stage.addView(video, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // The panel's real size, not the 1080p the UI layer runs at: the pictures are drawn at 4K when the panel is.
        int[] panel = panelSize(context);
        width = panel[0];
        height = panel[1];
        surface4k = new Stage(context, width, height, FADE_MS);
        stage.addView(surface4k, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        preferences = context.getSharedPreferences("photo_dream", Context.MODE_PRIVATE);
        File dir = context.getExternalFilesDir(null);
        interval = Media.seconds(Photos.read(dir == null ? null : new File(dir, Media.SECONDS))) * 1000L;
        images = dir == null ? new ArrayList<>() : Media.list(new File(dir, Media.PICTURES));
        if (!Feed.selected(context).isEmpty()) {
            images.addAll(Online.files(context));
            Collections.shuffle(images, random);
        }
        if (caption != null) caption.setAlpha(0f);
    }

    /** {width, height} of the display's current mode, at most 3840x2160. */
    private static int[] panelSize(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int w = metrics.widthPixels, h = metrics.heightPixels;
        try {
            android.view.Display display = context.getSystemService(android.hardware.display.DisplayManager.class)
                    .getDisplay(android.view.Display.DEFAULT_DISPLAY);
            android.view.Display.Mode mode = display.getMode();
            if (mode.getPhysicalWidth() >= w && mode.getPhysicalHeight() >= h) {
                w = mode.getPhysicalWidth();
                h = mode.getPhysicalHeight();
            }
        } catch (RuntimeException ignored) {
            // Keep the UI size.
        }
        return new int[] {Math.min(w, 3840), Math.min(h, 2160)};
    }

    void start() {
        running = true;
        empty.setVisibility(images.isEmpty() ? View.VISIBLE : View.GONE);
        if (decoder == null) decoder = Executors.newSingleThreadExecutor();
        showNext();
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (decoder != null) decoder.shutdownNow();
        decoder = null;
        surface4k.clear();
        pendingClip = null;
        stopVideo();
    }

    /** Decodes the next picture off the main thread, then fades it in over the current one. */
    private void showNext() {
        if (!running || images.isEmpty() || decoder == null) return;
        decoder.execute(() -> {
            Bitmap bitmap = null;
            Bitmap blur = null;
            File clip = null;
            String date = "";
            // Skip unreadable pictures, but stop after one round so a folder of broken files doesn't spin.
            for (int tries = 0; tries < images.size() && bitmap == null && clip == null; tries++) {
                File file = Media.next(images, preferences.getString("image", ""));
                preferences.edit().putString("image", file.getName()).apply();
                if (Media.video(file.getName())) {
                    clip = file;
                } else {
                    Bitmap[] blurOut = new Bitmap[1];
                    bitmap = decode(file, blurOut);
                    blur = blurOut[0];
                    if (bitmap != null && caption != null) {
                        String credit = Online.credit(context, file);
                        date = !credit.isEmpty() ? (credits ? credit : "") : dates ? Photos.dateOf(file) : "";
                    }
                }
            }
            Bitmap shown = bitmap;
            Bitmap shownBlur = blur;
            File playing = clip;
            String shownDate = date;
            handler.post(() -> {
                if (!running) {
                    if (shown != null) shown.recycle();
                    return;
                }
                if (playing != null) {
                    showCaption("");
                    playVideo(playing);   // moves on when the clip ends
                    return;
                }
                if (shown != null) {
                    crossfade(shown, shownBlur);
                    showCaption(shownDate);
                }
                // A single picture still drifts on: it fades into itself with a new zoom.
                handler.postDelayed(advance, interval);
            });
        });
    }

    private void showCaption(String date) {
        if (caption == null) return;
        caption.animate().cancel();
        if (date.isEmpty()) {
            caption.animate().alpha(0f).setDuration(FADE_MS / 2).start();
            return;
        }
        caption.animate().alpha(0f).setDuration(FADE_MS / 2).withEndAction(() -> {
            caption.setText(date);
            caption.animate().alpha(1f).setDuration(FADE_MS / 2).start();
        }).start();
    }

    private void crossfade(Bitmap bitmap, Bitmap blur) {
        // Start zoomed in or out at random and drift toward a random spot, over the whole slide.
        boolean zoomIn = random.nextBoolean();
        float from = zoomIn ? 1f : ZOOM;
        float to = zoomIn ? ZOOM : 1f;
        float dx = (random.nextFloat() - 0.5f) * width * (ZOOM - 1f);
        float dy = (random.nextFloat() - 0.5f) * height * (ZOOM - 1f);
        surface4k.show(bitmap, blur, from, to, zoomIn ? 0f : dx, zoomIn ? dx : 0f, zoomIn ? 0f : dy, zoomIn ? dy : 0f,
                interval + 2 * FADE_MS);
        boolean afterClip = player != null;
        if (afterClip) handler.postDelayed(this::stopVideo, FADE_MS + 100);
    }

    /** Fades the clip in over whatever shows now and plays it to the end (or loops a lone clip). */
    private void playVideo(File clip) {
        if (surface == null) {
            pendingClip = clip;
            return;
        }
        boolean clipShowing = player != null;
        stopVideo();
        int generation = ++clipGeneration;
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            mp.setDataSource(clip.getPath());
            mp.setSurface(surface);
            mp.setVolume(0f, 0f);
            mp.setLooping(images.size() == 1);
            mp.setOnVideoSizeChangedListener((p, w, h) -> cover(w, h));
            mp.setOnPreparedListener(p -> {
                if (generation != clipGeneration || !running) return;
                p.start();
                video.bringToFront();
                if (clipShowing) video.setAlpha(1f);
                else video.animate().alpha(1f).setDuration(FADE_MS).start();
                handler.postDelayed(() -> {
                    if (generation != clipGeneration) return;
                    surface4k.clear();
                }, FADE_MS + 100);
            });
            mp.setOnCompletionListener(p -> {
                if (generation == clipGeneration && running) showNext();
            });
            mp.setOnErrorListener((p, what, extra) -> {
                if (generation == clipGeneration && running) {
                    stopVideo();
                    handler.postDelayed(advance, 1000);
                }
                return true;
            });
            mp.prepareAsync();
        } catch (Exception failure) {
            stopVideo();
            handler.postDelayed(advance, 1000);
        }
    }

    /** Scales the clip to fill the screen, cropping the overflow evenly (like CENTER_CROP). */
    private void cover(int videoWidth, int videoHeight) {
        int w = video.getWidth(), h = video.getHeight();
        if (videoWidth <= 0 || videoHeight <= 0 || w <= 0 || h <= 0) return;
        float scale = Math.max((float) w / videoWidth, (float) h / videoHeight);
        Matrix matrix = new Matrix();
        matrix.setScale(videoWidth * scale / w, videoHeight * scale / h, w / 2f, h / 2f);
        video.setTransform(matrix);
    }

    private void stopVideo() {
        clipGeneration++;
        video.animate().cancel();
        video.setAlpha(0f);
        if (player != null) {
            try { player.stop(); } catch (IllegalStateException ignored) { }
            player.release();
            player = null;
        }
    }

    /** The cards' blur places (window pixels), handed to the 4K surface. */
    void setBlurRects(java.util.List<android.graphics.RectF> rects, float radiusPx, int windowWidth) {
        surface4k.setBlurRects(rects, radiusPx, windowWidth);
    }

    /** A 48-pixel-wide, softened copy of the picture: shown stretched up, it is the blur behind the cards. */
    private static Bitmap blurred(Bitmap source) {
        // A gentle blur: 200 px wide and one pass, so shapes behind a card stay recognisable but soft.
        int w = 200, h = Math.max(8, Math.round(200f * source.getHeight() / source.getWidth()));
        Bitmap mid = Bitmap.createScaledBitmap(source, Math.max(w * 2, 1), Math.max(h * 2, 1), true);
        Bitmap small = Bitmap.createScaledBitmap(mid, w, h, true);
        if (mid != source && mid != small) mid.recycle();
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        px = box(px, w, h);
        Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        small.recycle();
        return out;
    }

    private static int[] box(int[] src, int w, int h) {
        int[] out = new int[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0, n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = Math.max(0, Math.min(w - 1, x + dx)), yy = Math.max(0, Math.min(h - 1, y + dy));
                        int p = src[yy * w + xx];
                        r += (p >> 16) & 0xFF;
                        g += (p >> 8) & 0xFF;
                        b += p & 0xFF;
                        n++;
                    }
                }
                out[y * w + x] = 0xFF000000 | ((r / n) << 16) | ((g / n) << 8) | (b / n);
            }
        }
        return out;
    }

    private Bitmap decode(File file, Bitmap[] blurOut) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int w = options.outWidth, h = options.outHeight;
            if (w <= 0 || h <= 0) { android.util.Log.w("Slides", "no bounds: " + file.getName()); return null; }
            int sample = Media.sampleSize(w, h, width, height);
            if (!Media.accepted(w / sample, h / sample)) { android.util.Log.w("Slides", "rejected " + w + "x" + h + " sample " + sample + " screen " + width + "x" + height); return null; }
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = Media.coverScale(w / sample, h / sample, width, height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;   // 565 bands skies and fog on a 4K picture
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) { android.util.Log.w("Slides", "decodeFile null"); return null; }
            try {
                blurOut[0] = blurred(decoded);
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // No blur for this picture.
            }
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            android.util.Log.w("Slides", "decode failed: " + file.getName() + " " + failure);
            return null;
        }
    }
}
