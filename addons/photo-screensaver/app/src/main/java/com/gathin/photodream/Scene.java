package com.gathin.photodream;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What the screensaver and the card's slideshow show: the pictures (full screen, or as prints), and on top what
 * the options switch on: the time and date, the weather, the sky behind it all. Both hosts call start and stop.
 */
final class Scene {

    private static final long MINUTE_MS = 60_000L;
    /** How far the bottom line may wander so it does not burn in. */
    private static final int NUDGE_DP = 20, NUDGE_UP_DP = 6;

    final FrameLayout root;
    private final Context context;
    private final Options options;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final SkyView sky;
    private final View bottomLine;
    private final TextView weather;
    private final Slides slides;
    private final Prints prints;
    private ExecutorService network;
    /** The cards the owner switched on (the widgets page), in their corners. */
    private final com.gathin.widgets.Widgets.Host cards;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (sky != null) sky.update();
            nudge();
            handler.postDelayed(this, MINUTE_MS);
        }
    };
    private final Runnable refreshWeather = this::refreshWeather;

    Scene(Context context) {
        this.context = context;
        options = Options.load(context);
        root = new FrameLayout(context);
        // The 4K surface sits behind the window, so a full-screen slideshow keeps the root see-through (the hosts
        // paint the window black); the prints draw ordinary views and want the black here.
        if (options.prints) root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);
        if (options.sky && options.prints) {
            sky = new SkyView(context);
            root.addView(sky, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            sky = null;
        }
        FrameLayout stage = new FrameLayout(context);
        root.addView(stage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View overlay = LayoutInflater.from(context).inflate(R.layout.overlay, root, false);
        root.addView(overlay);
        bottomLine = overlay.findViewById(R.id.bottomLine);
        weather = overlay.findViewById(R.id.weather);
        TextView caption = overlay.findViewById(R.id.caption);
        View empty = overlay.findViewById(R.id.empty);
        File dir = context.getExternalFilesDir(null);
        ((TextView) overlay.findViewById(R.id.emptyPath)).setText(
                dir == null ? "" : new File(dir, Media.PICTURES).getPath());

        int clockVisibility = options.clock ? View.VISIBLE : View.GONE;
        for (int id : new int[] {R.id.greeting, R.id.time, R.id.ampm, R.id.divider, R.id.dateBlock}) {
            overlay.findViewById(id).setVisibility(clockVisibility);
        }
        ((TextView) overlay.findViewById(R.id.greeting)).setText(greeting());
        weather.setVisibility(View.GONE);
        bottomLine.setVisibility(options.clock || options.weather ? View.VISIBLE : View.GONE);
        // In full-screen mode the pictures cover everything: a dark fade keeps the bottom line readable.
        overlay.findViewById(R.id.bottomFade).setVisibility(
                !options.prints && (options.clock || options.weather) ? View.VISIBLE : View.GONE);

        if (options.prints) {
            caption.setVisibility(View.GONE);
            prints = new Prints(context, stage, options.clock || options.weather ? bottomLine : null, empty,
                    options.dates);
            slides = null;
        } else {
            // Online pictures carry their author and licence, which must be shown whatever the dates option says.
            boolean credits = options.credit && !Feed.selected(context).isEmpty();
            slides = new Slides(context, stage, options.dates || credits ? caption : null, options.dates, credits, empty);
            prints = null;
            if (!options.dates && !credits) caption.setVisibility(View.GONE);
        }
        bottomLine.bringToFront();
        cards = new com.gathin.widgets.Widgets.Host(context, SaverWidgets.surface(context));
        if (slides != null) {
            cards.setListener((rects, radius) -> slides.setBlurRects(rects, radius, root.getWidth()));
        }
        root.addView(cards.view(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    void start() {
        if (running) return;
        running = true;
        if (slides != null) slides.start();
        if (prints != null) prints.start();
        handler.post(tick);
        cards.start();
        if (options.weather) {
            network = Executors.newSingleThreadExecutor();
            handler.post(refreshWeather);
        }
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        cards.stop();
        if (network != null) network.shutdownNow();
        network = null;
        if (slides != null) slides.stop();
        if (prints != null) prints.stop();
    }

    /** "Good evening" and so on, from the hour. */
    private String greeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return context.getString(hour >= 5 && hour < 12 ? R.string.greeting_morning
                : hour >= 12 && hour < 18 ? R.string.greeting_day
                : hour >= 18 && hour < 23 ? R.string.greeting_evening : R.string.greeting_night);
    }

    private void nudge() {
        if (bottomLine.getVisibility() != View.VISIBLE) return;
        float d = context.getResources().getDisplayMetrics().density;
        bottomLine.animate().translationX((random.nextFloat() * 2 - 1) * NUDGE_DP * d)
                .translationY(random.nextFloat() * -NUDGE_UP_DP * d).setDuration(1500).start();
    }

    /** The cached reading if it is fresh, else a new one; again every 30 minutes while the scene runs. */
    private void refreshWeather() {
        if (!running) return;
        android.util.Log.d("Weather", "refresh");
        handler.postDelayed(refreshWeather, Weather.REFRESH_MS);
        SharedPreferences cache = context.getSharedPreferences("weather", Context.MODE_PRIVATE);
        String cached = cache.getString("text", "");
        long age = System.currentTimeMillis() - cache.getLong("at", 0);
        if (!cached.isEmpty() && age >= 0 && age < Weather.REFRESH_MS) {
            showWeather(cached);
            return;
        }
        Weather.locate(context, location -> {
            android.util.Log.d("Weather", "location " + (location != null));
            ExecutorService n = network;
            if (location == null || n == null || !running) return;
            double lat = location.getLatitude(), lon = location.getLongitude();
            try {
                n.execute(() -> {
                    String text;
                    try {
                        text = Weather.now(lat, lon);
                    } catch (Exception e) {
                        android.util.Log.w("Weather", "fetch failed: " + e);
                        return;   // offline: keep what shows, try again in half an hour
                    }
                    cache.edit().putString("text", text).putLong("at", System.currentTimeMillis()).apply();
                    handler.post(() -> { if (running) showWeather(text); });
                });
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                // Stopped meanwhile.
            }
        });
    }

    private void showWeather(String text) {
        weather.setText(text);
        weather.setVisibility(View.VISIBLE);
    }
}
