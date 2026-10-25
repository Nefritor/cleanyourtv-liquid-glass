package com.gathin.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cards for Home and the screensaver: the weather, the moon, rates and so on. Each card stands on a grid of cells at the
 * owner's choice (a place and a size such as 2 x 1 or 2 x 2, kept per app in the preferences). This file is the list of
 * cards, the grid and the host that lays them out; Cards draws them and Data brings the network ones their numbers.
 */
public final class Widgets {
    /** One grid cell is 115 x 104 dp with a 10 dp gap, so a card 2 cells wide is 240 dp and 2 cells high is 218 dp. */
    public static final int CELL_W = 115, CELL_H = 104, GAP = 10;
    private static final String PREFS = "widgets";

    /** What is behind the cards, as a small snapshot the cards blur: the app provides it and keeps it fresh. */
    public interface Backdrop {
        /** The backdrop at a fraction of the screen's size, or null while there is none. */
        android.graphics.Bitmap snapshot();

        int screenWidth();

        int screenHeight();
    }

    /** Told where the cards stand (window pixels) each time they are laid out, so the screensaver can blur there. */
    public interface Listener {
        void onCards(List<RectF> rects, float radiusPx);
    }

    /** A card's place on the grid: the top left cell and the size in cells. */
    public static final class Placement {
        public int col, row, w, h;

        public Placement(int col, int row, int w, int h) {
            this.col = col;
            this.row = row;
            this.w = w;
            this.h = h;
        }

        public Placement copy() {
            return new Placement(col, row, w, h);
        }

        public boolean overlaps(Placement o) {
            return col < o.col + o.w && o.col < col + w && row < o.row + o.h && o.row < row + h;
        }

        @Override public String toString() {
            return col + "," + row + "," + w + "," + h;
        }

        static Placement parse(String s) {
            try {
                String[] p = s.split(",");
                return new Placement(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
            } catch (RuntimeException e) {
                return null;
            }
        }

        public String sizeLabel() {
            return w + "×" + h;
        }
    }

    /** One card the owner can put on the grid. */
    public static final class Spec {
        public final String id;
        public final String ru;
        public final String en;
        final boolean network;
        /** The sizes it can take, {w, h} in cells; the first is the one it starts with. */
        public final int[][] sizes;

        Spec(String id, String ru, String en, boolean network, int[]... sizes) {
            this.id = id;
            this.ru = ru;
            this.en = en;
            this.network = network;
            this.sizes = sizes;
        }

        public String title() {
            return isRussian() ? ru : en;
        }
    }

    /** What differs between the two places the cards live in. */
    public static final class Surface {
        public final Typeface light, regular, medium;
        /** The grid: how many cells, and where its top left corner stands on the screen, in dp. */
        public final int cols, rows, originX, originY;
        /** Cells the cards may not take (the clock, the buttons): {col, row, w, h}. */
        public final List<int[]> reserved = new ArrayList<>();
        /** Where each card starts when the owner has not chosen yet. */
        public final Map<String, Placement> defaults = new HashMap<>();
        /** The glass of the panels: top and bottom fill and the edge, as ARGB. Light by default, like the header's pills. */
        public int glassTop = 0x2EFFFFFF, glassBottom = 0x14FFFFFF, glassEdge = 0x38FFFFFF;
        /** True for Home (the editor's preview draws its header, name and dock), false for the screensaver (a photo). */
        public boolean home;
        /** Optional: the backdrop the cards blur. */
        public Backdrop backdrop;
        /** Called when the owner changes a choice, so the screen can rebuild. */
        public Runnable onChange;

        public Surface(int cols, int rows, int originX, int originY, Typeface light, Typeface regular, Typeface medium) {
            this.cols = cols;
            this.rows = rows;
            this.originX = originX;
            this.originY = originY;
            this.light = light;
            this.regular = regular;
            this.medium = medium;
        }

        public int cellsWidthDp() {
            return cols * CELL_W + (cols - 1) * GAP;
        }

        public int cellsHeightDp() {
            return rows * CELL_H + (rows - 1) * GAP;
        }
    }

    private static final List<Spec> ALL = new ArrayList<>();

    static {
        int[] a = {2, 1}, b = {3, 1}, c = {4, 1}, d = {2, 2}, e = {3, 2}, f = {4, 2};
        ALL.add(new Spec("weather", "Погода", "Weather", true, a, b, d, e));
        ALL.add(new Spec("forecast", "Прогноз по часам", "Hourly forecast", true, b, c, a, e));
        ALL.add(new Spec("moon", "Фаза Луны", "Moon phase", false, a, d, b));
        ALL.add(new Spec("sun", "Восход и закат", "Sunrise and sunset", false, a, d, b));
        ALL.add(new Spec("world", "Мировые часы", "World clocks", false, a, d, b, e));
        ALL.add(new Spec("progress", "Прогресс дня и года", "Day and year progress", false, a, d, b));
        ALL.add(new Spec("calendar", "Календарь", "Calendar", false, a, d, c, e, f));
        ALL.add(new Spec("proverb", "Пословица дня", "Proverb of the day", false, a, b, d, e));
        ALL.add(new Spec("rates", "Курсы ЦБ РФ", "Central bank rates", true, a, d, b, e));
        ALL.add(new Spec("history", "В этот день", "On this day", true, a, b, d, e, f));
        ALL.add(new Spec("system", "Состояние ТВ", "TV status", false, a, d, b));
        ALL.add(new Spec("countdown", "Отсчёт дней", "Countdown", false, a, d, b, e));
        ALL.add(new Spec("clock", "Часы", "Clock", false, a, d, b));
        ALL.add(new Spec("week", "Неделя и день года", "Week and day of the year", false, a, d, b));
        ALL.add(new Spec("holidays", "Ближайшие праздники", "Next holidays", false, a, d, b, e));
        ALL.add(new Spec("fact", "Факт дня", "Fact of the day", false, b, d, e, f));
        ALL.add(new Spec("quote", "Цитата дня", "Quote of the day", false, b, d, e, f));
        ALL.add(new Spec("word", "Слово дня", "Word of the day", false, a, d, b, e));
        ALL.add(new Spec("date", "Число месяца", "Date", false, a, d, b));
        ALL.add(new Spec("season", "Время года", "Season", false, a, d, b));
        ALL.add(new Spec("phases", "Фазы Луны", "Moon phases", false, a, d, b, e));
        ALL.add(new Spec("tip", "Совет дня", "Tip of the day", false, a, b, d, e));
        ALL.add(new Spec("dinner", "Что приготовить", "What to cook", false, a, b, d, e));
        ALL.add(new Spec("movie", "Что посмотреть", "What to watch", false, a, b, d, e));
    }

    /** One line on what a card shows, for the picker. */
    public static String description(String id) {
        boolean ru = isRussian();
        switch (id) {
            case "weather": return ru ? "Температура сейчас, ветер и влажность. Данные MET Norway." : "Temperature now, wind and humidity (MET Norway).";
            case "forecast": return ru ? "Погода на ближайшие часы: время, значок и температура." : "The next hours: time, symbol and temperature.";
            case "moon": return ru ? "Фаза Луны и сколько она освещена." : "The Moon's phase and how much of it is lit.";
            case "sun": return ru ? "Восход, закат и длина светового дня." : "Sunrise, sunset and the length of the day.";
            case "world": return ru ? "Время в городах мира. Набор городов выбирается." : "The time in cities around the world; the set is yours to pick.";
            case "progress": return ru ? "Сколько прошло дня, недели, месяца и года." : "How much of the day, week, month and year has passed.";
            case "calendar": return ru ? "Неделя или месяц, сегодняшний день и выходные." : "The week or the month, with today and the weekend marked.";
            case "proverb": return ru ? "Пословица на каждый день." : "A proverb for each day.";
            case "rates": return ru ? "Курсы валют Банка России и их изменение." : "Central bank exchange rates and their change.";
            case "history": return ru ? "Событие, случившееся в этот день в истории." : "Something that happened on this day in history.";
            case "system": return ru ? "Память, хранилище и время работы ТВ." : "The TV's memory, storage and uptime.";
            case "countdown": return ru ? "Дни до выбранных дат: Нового года, выходных, праздников." : "Days to dates you pick: New Year, the weekend, holidays.";
            case "clock": return ru ? "Крупные часы и дата." : "A large clock and the date.";
            case "week": return ru ? "Номер недели, день года, квартал." : "Week number, day of the year, quarter.";
            case "holidays": return ru ? "Ближайшие государственные праздники и дни до них." : "The next public holidays and the days to them.";
            case "fact": return ru ? "Интересный факт на каждый день." : "An interesting fact for each day.";
            case "quote": return ru ? "Цитата дня." : "A quote for each day.";
            case "word": return ru ? "Слово дня и его значение." : "A word for each day and what it means.";
            case "date": return ru ? "Число месяца крупно, как на листе календаря." : "The day of the month, large, like a calendar page.";
            case "season": return ru ? "Время года, сколько оно длится и когда следующее." : "The season, how far into it, and when the next begins.";
            case "phases": return ru ? "Даты ближайших новолуния, первой четверти, полнолуния и последней четверти." : "Dates of the next new moon, first quarter, full moon and last quarter.";
            case "tip": return ru ? "Небольшой совет для самочувствия на каждый день." : "A small tip for well-being, a new one each day.";
            case "dinner": return ru ? "Идея, что приготовить сегодня." : "An idea for what to cook today.";
            case "movie": return ru ? "Фильм, который стоит посмотреть." : "A film worth watching.";
            default: return "";
        }
    }

    private Widgets() {}

    public static List<Spec> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Spec spec(String id) {
        for (Spec s : ALL) if (s.id.equals(id)) return s;
        return null;
    }

    static boolean isRussian() {
        return "ru".equals(Locale.getDefault().getLanguage());
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- places ----------------------------------------------------------------------------------

    /** Where a card stands, or null while it is off. */
    public static Placement placement(Context c, Surface s, String id) {
        // Before the grid a card's place was a corner number (an int): that choice no longer means anything.
        Object raw = prefs(c).getAll().get("w_" + id);
        String stored = raw instanceof String ? (String) raw : null;
        if (stored == null || (!"off".equals(stored) && Placement.parse(stored) == null)) {
            Placement d = s.defaults.get(id);
            return d == null ? null : d.copy();
        }
        return "off".equals(stored) ? null : Placement.parse(stored);
    }

    public static void setPlacement(Context c, Surface s, String id, Placement p) {
        prefs(c).edit().putString("w_" + id, p == null ? "off" : p.toString()).apply();
        if (s.onChange != null) s.onChange.run();
    }

    /** Stores a card's place (null: off) without telling the screen; Arrange.commit tells it once for all. */
    static void setPlacementQuiet(Context c, String id, Placement p) {
        prefs(c).edit().putString("w_" + id, p == null ? "off" : p.toString()).apply();
    }

    /** True if the place is on the grid, clear of the reserved cells and of every other card (except {@code ignore}). */
    public static boolean fits(Context c, Surface s, Placement p, String ignore) {
        if (p.col < 0 || p.row < 0 || p.col + p.w > s.cols || p.row + p.h > s.rows) return false;
        for (int[] r : s.reserved) {
            if (p.overlaps(new Placement(r[0], r[1], r[2], r[3]))) return false;
        }
        for (Spec spec : ALL) {
            if (spec.id.equals(ignore)) continue;
            Placement o = placement(c, s, spec.id);
            if (o != null && p.overlaps(o)) return false;
        }
        return true;
    }

    /** The first free place for a card of this size, scanning row by row; null when there is none. */
    public static Placement firstFree(Context c, Surface s, String id, int w, int h) {
        for (int row = 0; row + h <= s.rows; row++) {
            for (int col = 0; col + w <= s.cols; col++) {
                Placement p = new Placement(col, row, w, h);
                if (fits(c, s, p, id)) return p;
            }
        }
        return null;
    }

    /** The next size the card can take at its place (growing right and down when the room is free), or its current one. */
    public static Placement nextSize(Context c, Surface s, Spec spec, Placement now) {
        int at = 0;
        for (int i = 0; i < spec.sizes.length; i++) {
            if (spec.sizes[i][0] == now.w && spec.sizes[i][1] == now.h) at = i;
        }
        for (int step = 1; step <= spec.sizes.length; step++) {
            int[] size = spec.sizes[(at + step) % spec.sizes.length];
            Placement p = new Placement(now.col, now.row, size[0], size[1]);
            if (fits(c, s, p, spec.id)) return p;
        }
        return now;
    }

    /** Every choice back to this surface's defaults. */
    public static void reset(Context c, Surface s) {
        prefs(c).edit().clear().apply();
        if (s.onChange != null) s.onChange.run();
    }

    // ---- per-card options ------------------------------------------------------------------------

    public static int param(Context c, String id, String key, int fallback) {
        return prefs(c).getInt("p_" + id + "_" + key, fallback);
    }

    public static void setParam(Context c, Surface s, String id, String key, int value) {
        prefs(c).edit().putInt("p_" + id + "_" + key, value).apply();
        if (s != null && s.onChange != null) s.onChange.run();
    }

    // ---- the host --------------------------------------------------------------------------------

    /**
     * The layer that holds the cards on the grid: rebuilt every minute and whenever the owner changes something, with the
     * network numbers fetched in the background.
     */
    public static final class Host {
        private final Context context;
        private final Surface surface;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final FrameLayout layer;
        private boolean running;
        private boolean fetching;
        private String highlight = "";
        private String lifted = "";
        private Listener listener;
        private boolean blurOff;
        /** While the editor works: the cards are drawn at these places instead of the stored ones, and glide there. */
        private Map<String, Placement> draft;
        private final Map<String, View> views = new HashMap<>();
        private final Runnable minute = new Runnable() {
            @Override public void run() {
                if (!running) return;
                rebuild();
                handler.postDelayed(this, 60_000L);
            }
        };

        public Host(Context context, Surface surface) {
            this.context = context;
            this.surface = surface;
            layer = new FrameLayout(context);
            layer.setClipChildren(false);
            layer.setClipToPadding(false);
            layer.setFocusable(false);
            layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }

        public View view() {
            return layer;
        }

        public void setListener(Listener l) {
            listener = l;
        }

        /** The editor draws plain glass: its blur would be sampled from the wrong place. */
        public Host withoutBlur() {
            blurOff = true;
            return this;
        }

        /** The editor outlines this card in white (\"\" for none). */
        public void setHighlight(String id) {
            highlight = id == null ? "" : id;
            rebuild();
        }

        /** The editor shows this card as picked up (a little larger, outlined). */
        public void setLifted(String id) {
            lifted = id == null ? "" : id;
        }

        /** The editor draws these places instead of the stored ones (null: back to the stored ones). */
        public void setDraft(Map<String, Placement> places) {
            draft = places;
            rebuild();
        }

        /** Shakes a card sideways: "there is no room for it". */
        public void shake(String id) {
            shakeView(views.get(id));
        }

        public static void shakeView(View v) {
            if (v == null) return;
            float d = v.getResources().getDisplayMetrics().density;
            android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofFloat(v, View.TRANSLATION_X,
                    0, -10 * d, 10 * d, -7 * d, 7 * d, -3 * d, 3 * d, 0);
            a.setDuration(340);
            a.start();
        }

        public void start() {
            running = true;
            layer.setVisibility(View.VISIBLE);
            handler.removeCallbacks(minute);
            rebuild();
            handler.postDelayed(minute, 60_000L);
            fetch();
        }

        public void stop() {
            running = false;
            handler.removeCallbacksAndMessages(null);
            layer.setVisibility(View.INVISIBLE);
        }

        /** Draws the cards again from what is known now (cache included); a card that changed place glides there. */
        public void rebuild() {
            float d = context.getResources().getDisplayMetrics().density;
            Map<String, float[]> before = new HashMap<>();
            for (Map.Entry<String, View> e : views.entrySet()) {
                View v = e.getValue();
                before.put(e.getKey(), new float[] {v.getLeft() + v.getTranslationX(), v.getTop() + v.getTranslationY()});
            }
            layer.removeAllViews();
            views.clear();
            for (Spec spec : ALL) {
                Placement p = draft != null ? draft.get(spec.id) : placement(context, surface, spec.id);
                if (p == null) continue;
                Backdrop kept = surface.backdrop;
                if (blurOff) surface.backdrop = null;
                View card = Cards.build(context, surface, spec.id, p);
                surface.backdrop = kept;
                if (card == null) continue;
                if (spec.id.equals(highlight)) {
                    GradientDrawable ring = new GradientDrawable();
                    ring.setCornerRadius(21 * d);
                    ring.setStroke(Math.round(3 * d), 0xFFFFFFFF);
                    card.setForeground(ring);
                }
                if (spec.id.equals(lifted)) {
                    card.setScaleX(1.04f);
                    card.setScaleY(1.04f);
                }
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(widthDp(p) * d), Math.round(heightDp(p) * d));
                lp.gravity = Gravity.TOP | Gravity.START;
                lp.leftMargin = Math.round((surface.originX + p.col * (CELL_W + GAP)) * d);
                lp.topMargin = Math.round((surface.originY + p.row * (CELL_H + GAP)) * d);
                layer.addView(card, lp);
                views.put(spec.id, card);
                if (draft != null) {
                    float[] was = before.get(spec.id);
                    if (was != null) {
                        float dx = was[0] - lp.leftMargin, dy = was[1] - lp.topMargin;
                        if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
                            card.setTranslationX(dx);
                            card.setTranslationY(dy);
                            card.animate().translationX(0).translationY(0).setDuration(230)
                                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
                        }
                    } else if (!before.isEmpty()) {
                        card.setAlpha(0f);
                        card.animate().alpha(1f).setDuration(200).start();
                    }
                }
            }
            if (listener != null) layer.post(this::reportCards);
            layer.invalidate();
        }

        /** Redraws the cards' blurred backgrounds (the app calls it after it refreshed the backdrop). */
        public void invalidateCards() {
            for (int i = 0; i < layer.getChildCount(); i++) layer.getChildAt(i).invalidate();
        }

        private void reportCards() {
            if (listener == null) return;
            List<RectF> rects = new ArrayList<>();
            int[] at = new int[2];
            for (int i = 0; i < layer.getChildCount(); i++) {
                View card = layer.getChildAt(i);
                card.getLocationInWindow(at);
                rects.add(new RectF(at[0], at[1], at[0] + card.getWidth(), at[1] + card.getHeight()));
            }
            listener.onCards(rects, 21 * context.getResources().getDisplayMetrics().density);
        }

        /** Asks the network for the numbers of the cards that are on and draws them when they arrive. */
        public void fetch() {
            if (fetching) return;
            boolean needed = false;
            for (Spec spec : ALL) needed |= (spec.network || spec.id.equals("sun")) && placement(context, surface, spec.id) != null;
            if (!needed) return;
            fetching = true;
            final Context app = context.getApplicationContext();
            new Thread(() -> {
                try {
                    boolean weather = placement(app, surface, "weather") != null || placement(app, surface, "forecast") != null;
                    if (weather && Data.weatherStale(app)) Data.refreshWeather(app);
                    if (placement(app, surface, "rates") != null) Data.rates(app, true);
                    if (placement(app, surface, "history") != null) Data.history(app, true);
                    if (placement(app, surface, "sun") != null) Data.location(app);
                } finally {
                    handler.post(() -> {
                        fetching = false;
                        if (running) rebuild();
                    });
                }
            }, "widget-data").start();
        }
    }

    public static int widthDp(Placement p) {
        return p.w * CELL_W + (p.w - 1) * GAP;
    }

    public static int heightDp(Placement p) {
        return p.h * CELL_H + (p.h - 1) * GAP;
    }
}
