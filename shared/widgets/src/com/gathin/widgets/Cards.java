package com.gathin.widgets;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Draws each card on a panel in the style of the header's pills. Every card is built for the size it stands at: a 2 x 1
 * card shows the essentials, a bigger one shows more (the month, six cities, the hours ahead) rather than the same thing
 * stretched. The content sits in the middle of the room under the caption, so cards of one size line up with each other.
 */
final class Cards {
    private static final int WHITE = 0xF2FFFFFF, DIM = 0x99FFFFFF, FAINT = 0x33FFFFFF;
    /** Saturdays and Sundays in the calendar and the date card. */
    private static final int WEEKEND = 0xFFFF9C9C;
    private static final int PAD_H = 18, PAD_TOP = 10, PAD_BOTTOM = 10;

    /** An option the owner can change on a card (its page, opened with a long OK). */
    static final class Param {
        final String key;
        final String ru, en;
        final String[] optionsRu, optionsEn;

        Param(String key, String ru, String en, String[] optionsRu, String[] optionsEn) {
            this.key = key;
            this.ru = ru;
            this.en = en;
            this.optionsRu = optionsRu;
            this.optionsEn = optionsEn;
        }

        String label() {
            return ru() ? ru : en;
        }

        String[] options() {
            return ru() ? optionsRu : optionsEn;
        }
    }

    private static final String[][] CITY_SETS_RU = {
            {"Лондон", "Нью-Йорк", "Токио", "Москва", "Дубай", "Сидней"},
            {"Москва", "Берлин", "Стамбул", "Дубай", "Пекин", "Токио"},
            {"Париж", "Лос-Анджелес", "Сингапур", "Рио-де-Жанейро", "Лондон", "Сидней"},
            {"Калининград", "Москва", "Екатеринбург", "Омск", "Новосибирск", "Владивосток"},
            {"Киев", "Варшава", "Рим", "Мадрид", "Лиссабон", "Рейкьявик"}};
    private static final String[][] CITY_SETS_EN = {
            {"London", "New York", "Tokyo", "Moscow", "Dubai", "Sydney"},
            {"Moscow", "Berlin", "Istanbul", "Dubai", "Beijing", "Tokyo"},
            {"Paris", "Los Angeles", "Singapore", "Rio", "London", "Sydney"},
            {"Kaliningrad", "Moscow", "Yekaterinburg", "Omsk", "Novosibirsk", "Vladivostok"},
            {"Kyiv", "Warsaw", "Rome", "Madrid", "Lisbon", "Reykjavik"}};
    private static final String[][] CITY_ZONES = {
            {"Europe/London", "America/New_York", "Asia/Tokyo", "Europe/Moscow", "Asia/Dubai", "Australia/Sydney"},
            {"Europe/Moscow", "Europe/Berlin", "Europe/Istanbul", "Asia/Dubai", "Asia/Shanghai", "Asia/Tokyo"},
            {"Europe/Paris", "America/Los_Angeles", "Asia/Singapore", "America/Sao_Paulo", "Europe/London", "Australia/Sydney"},
            {"Europe/Kaliningrad", "Europe/Moscow", "Asia/Yekaterinburg", "Asia/Omsk", "Asia/Novosibirsk", "Asia/Vladivostok"},
            {"Europe/Kyiv", "Europe/Warsaw", "Europe/Rome", "Europe/Madrid", "Europe/Lisbon", "Atlantic/Reykjavik"}};

    /** The things the countdown can count to: name (ru, en), month and day (0 for the moving ones), and the kind. */
    private static final Object[][] TARGETS = {
            {"Новый год", "New Year", 0, 0, "ny"}, {"Выходные", "The weekend", 0, 0, "weekend"},
            {"8 марта", "8 March", Calendar.MARCH, 8, ""}, {"1 мая", "1 May", Calendar.MAY, 1, ""},
            {"9 мая", "9 May", Calendar.MAY, 9, ""}, {"Лето", "Summer", Calendar.JUNE, 1, ""},
            {"12 июня", "12 June", Calendar.JUNE, 12, ""}, {"1 сентября", "1 September", Calendar.SEPTEMBER, 1, ""}};

    private Cards() {}

    private static boolean ru() {
        return Widgets.isRussian();
    }

    static String t(String russian, String english) {
        return ru() ? russian : english;
    }

    /** The options the owner can change on a card, or none. */
    static Param[] params(String id) {
        if ("world".equals(id)) {
            String[] ru = new String[CITY_SETS_RU.length], en = new String[CITY_SETS_EN.length];
            for (int i = 0; i < ru.length; i++) {
                ru[i] = TextUtils.join(", ", java.util.Arrays.copyOf(CITY_SETS_RU[i], 3)) + "…";
                en[i] = TextUtils.join(", ", java.util.Arrays.copyOf(CITY_SETS_EN[i], 3)) + "…";
            }
            return new Param[] {new Param("set", "Города", "Cities", ru, en)};
        }
        if ("countdown".equals(id)) {
            String[] ru = new String[TARGETS.length], en = new String[TARGETS.length];
            for (int i = 0; i < ru.length; i++) {
                ru[i] = (String) TARGETS[i][0];
                en[i] = (String) TARGETS[i][1];
            }
            Param[] out = new Param[4];
            for (int i = 0; i < 4; i++) out[i] = new Param("t" + i, "Цель " + (i + 1), "Target " + (i + 1), ru, en);
            return out;
        }
        return new Param[0];
    }

    static int paramDefault(String id, String key) {
        if ("countdown".equals(id)) return key.equals("t0") ? 0 : key.equals("t1") ? 1 : key.equals("t2") ? 3 : 5;
        return 0;
    }

    static View build(Context c, Widgets.Surface s, String id, Widgets.Placement p) {
        View v = make(c, s, id, p);
        if (v != null) fit(c, v, p);
        return v;
    }

    /** Nothing a card shows may be cut off: when the content is taller than the room, its text shrinks until it fits. */
    private static void fit(Context c, View root, Widgets.Placement p) {
        Object tag = root.getTag();
        if (!(tag instanceof ViewGroup)) return;
        ViewGroup body = (ViewGroup) tag;
        float d = c.getResources().getDisplayMetrics().density;
        int w = Math.round(Widgets.widthDp(p) * d), h = Math.round(Widgets.heightDp(p) * d);
        for (int i = 0; i < 10; i++) {
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            int content = 0;
            for (int k = 0; k < body.getChildCount(); k++) {
                View child = body.getChildAt(k);
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                // As tall as it wants to be, not as tall as the room lets it be.
                int room = Math.max(0, body.getMeasuredWidth() - lp.leftMargin - lp.rightMargin);
                child.measure(View.MeasureSpec.makeMeasureSpec(room, lp.width == ViewGroup.LayoutParams.MATCH_PARENT ? View.MeasureSpec.EXACTLY : View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                content += child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            }
            if (content <= body.getMeasuredHeight()) return;
            if (!shrink(body, 0.93f, 9 * d)) return;
        }
    }

    private static boolean shrink(View v, float factor, float minPx) {
        boolean any = false;
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            float to = t.getTextSize() * factor;
            if (to >= minPx) {
                t.setTextSize(TypedValue.COMPLEX_UNIT_PX, to);
                any = true;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) any |= shrink(g.getChildAt(i), factor, minPx);
        }
        return any;
    }

    private static View make(Context c, Widgets.Surface s, String id, Widgets.Placement p) {
        switch (id) {
            case "weather": return weather(c, s, p);
            case "forecast": return forecast(c, s, p);
            case "moon": return moon(c, s, p);
            case "sun": return sun(c, s, p);
            case "world": return world(c, s, p);
            case "progress": return progress(c, s, p);
            case "calendar": return calendar(c, s, p);
            case "proverb": return proverb(c, s, p);
            case "rates": return rates(c, s, p);
            case "history": return history(c, s, p);
            case "system": return system(c, s, p);
            case "countdown": return countdown(c, s, p);
            case "clock": return clock(c, s, p);
            case "week": return week(c, s, p);
            case "holidays": return holidays(c, s, p);
            case "fact": return fact(c, s, p);
            case "quote": return quote(c, s, p);
            case "word": return word(c, s, p);
            case "date": return date(c, s, p);
            case "season": return season(c, s, p);
            case "phases": return phases(c, s, p);
            case "tip": return tip(c, s, p);
            case "dinner": return dinner(c, s, p);
            case "movie": return movie(c, s, p);
            default: return null;
        }
    }

    // ---- building blocks -------------------------------------------------------------------------

    private static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    /** A card: the panel, its caption, and a body that centres whatever is put in it in the room left. */
    private static final class Card {
        final LinearLayout root;
        final LinearLayout body;
        final int innerDp;

        Card(Context c, Widgets.Surface s, String caption, Widgets.Placement p) {
            root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(Math.round(dp(c, PAD_H)), Math.round(dp(c, PAD_TOP)), Math.round(dp(c, PAD_H)), Math.round(dp(c, PAD_BOTTOM)));
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {s.glassTop, s.glassBottom});
            bg.setCornerRadius(dp(c, 21));
            bg.setStroke(Math.round(dp(c, 1)), s.glassEdge);
            root.setBackground(s.backdrop == null ? bg : new BlurGlass(root, s.backdrop, bg, dp(c, 21)));
            TextView cap = text(c, s.medium, caption, 12, DIM);
            cap.setAllCaps(true);
            cap.setLetterSpacing(0.14f);
            cap.setSingleLine(true);
            root.addView(cap);
            body = new LinearLayout(c);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setGravity(Gravity.TOP);
            // The same gap under the caption on every card, whatever the content.
            LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            bodyLp.topMargin = Math.round(dp(c, 7));
            root.addView(body, bodyLp);
            root.setTag(body);
            innerDp = Widgets.widthDp(p) - 2 * PAD_H;
        }
    }

    private static TextView text(Context c, Typeface face, CharSequence value, float sp, int color) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        if (face != null) v.setTypeface(face);
        v.setIncludeFontPadding(false);
        return v;
    }

    private static TextView add(Context c, LinearLayout parent, Typeface face, CharSequence value, float sp, int color, float topDp) {
        TextView v = text(c, face, value, sp, color);
        parent.addView(v, wrap(c, topDp));
        return v;
    }

    private static LinearLayout.LayoutParams wrap(Context c, float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(dp(c, topDp));
        return lp;
    }

    private static LinearLayout.LayoutParams full(Context c, float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(dp(c, topDp));
        return lp;
    }

    private static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private static View spacer(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    private static boolean big(Widgets.Placement p) {
        return p.h >= 2;
    }

    private static void noData(Context c, Widgets.Surface s, Card card) {
        add(c, card.body, s.light, t("Нет данных", "No data yet"), 18, DIM, 0);
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    // ---- cards -----------------------------------------------------------------------------------

    private static View weather(Context c, Widgets.Surface s, Widgets.Placement p) {
        Data.Weather w = Data.cachedWeather(c);
        Card card = new Card(c, s, t("Погода", "Weather"), p);
        if (w == null) {
            noData(c, s, card);
            return card.root;
        }
        String wind = t("Ветер ", "Wind ") + Math.round(w.wind) + t(" м/с", " m/s");
        String hum = t("Влажность ", "Humidity ") + w.humidity + "%";
        LinearLayout top = row(c);
        top.addView(text(c, s.light, w.temp + "°  " + Data.symbol(w.symbolCode), big(p) ? 38 : 30, WHITE));
        if (p.w >= 3) {
            top.addView(spacer(c));
            LinearLayout info = new LinearLayout(c);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setGravity(Gravity.END);
            info.addView(text(c, s.regular, wind, 13, DIM));
            add(c, info, s.regular, hum, 13, DIM, 3);
            top.addView(info);
            card.body.addView(top, full(c, 0));
        } else {
            card.body.addView(top);
            add(c, card.body, s.regular, wind + "   " + hum, 13, DIM, 4);
        }
        if (big(p) && !w.hours.isEmpty()) {
            View line = new View(c);
            line.setBackgroundColor(FAINT);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(dp(c, 1))));
            ll.topMargin = Math.round(dp(c, 10));
            card.body.addView(line, ll);
            card.body.addView(hoursRow(c, s, w, card.innerDp, 12, 17, 14), full(c, 8));
        }
        return card.root;
    }

    /** The hours ahead as even columns, as many as fit in the width. */
    private static View hoursRow(Context c, Widgets.Surface s, Data.Weather w, int widthDp, float hourSp, float symSp, float tempSp) {
        int n = Math.min(w.hours.size(), Math.max(3, widthDp / 40));
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < n; i++) {
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(text(c, s.regular, two(w.hours.get(i)[0]), hourSp, DIM));
            TextView sym = text(c, s.regular, Data.symbol(w.hourCodes.get(i)), symSp, WHITE);
            sym.setPadding(0, Math.round(dp(c, 3)), 0, Math.round(dp(c, 1)));
            col.addView(sym);
            col.addView(text(c, s.regular, w.hours.get(i)[1] + "°", tempSp, WHITE));
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        row.setLayoutParams(full(c, 0));
        return row;
    }

    private static View forecast(Context c, Widgets.Surface s, Widgets.Placement p) {
        Data.Weather w = Data.cachedWeather(c);
        Card card = new Card(c, s, t("Ближайшие часы", "Next hours"), p);
        if (w == null || w.hours.isEmpty()) {
            noData(c, s, card);
            return card.root;
        }
        boolean b = big(p);
        card.body.addView(hoursRow(c, s, w, card.innerDp, b ? 14 : 12, b ? 26 : 18, b ? 18 : 15));
        if (b) {
            add(c, card.body, s.regular, t("Ветер ", "Wind ") + Math.round(w.wind) + t(" м/с", " m/s") + "   " + t("Влажность ", "Humidity ")
                    + w.humidity + "%", 13, DIM, 14);
        }
        return card.root;
    }

    /** The moon disc: dim, with the lit part drawn from the phase. */
    private static final class MoonView extends View {
        private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final double age;
        private final int sizePx;

        MoonView(Context c, double age, int sizeDp) {
            super(c);
            this.age = age;
            this.sizePx = Math.round(dp(c, sizeDp));
            dim.setColor(0x33FFFFFF);
            lit.setColor(0xFFF4F1E4);
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(sizePx, sizePx);
        }

        @Override protected void onDraw(Canvas canvas) {
            float r = getWidth() / 2f;
            canvas.drawCircle(r, r, r, dim);
            boolean waxing = age < 0.5;
            double cos = Math.cos(2 * Math.PI * age);
            float a = (float) (r * Math.abs(cos));
            canvas.save();
            // The half that is lit at all: right while waxing, left while waning.
            canvas.clipRect(waxing ? r : 0, 0, waxing ? 2 * r : r, 2 * r);
            canvas.drawCircle(r, r, r, lit);
            if (cos > 0) {   // a crescent: the terminator's ellipse takes the lit half back
                oval.set(r - a, 0, r + a, 2 * r);
                canvas.drawOval(oval, dim);
            }
            canvas.restore();
            if (cos < 0) {   // a gibbous moon: the ellipse adds the other half's lit part
                oval.set(r - a, 0, r + a, 2 * r);
                canvas.drawOval(oval, lit);
            }
        }
    }

    private static View moon(Context c, Widgets.Surface s, Widgets.Placement p) {
        double age = Astro.moonAge(System.currentTimeMillis());
        int pct = (int) Math.round(Astro.illumination(age) * 100);
        String[] ruNames = {"Новолуние", "Растущий серп", "Первая четверть", "Растущая Луна", "Полнолуние", "Убывающая Луна",
                "Последняя четверть", "Убывающий серп"};
        String[] enNames = {"New moon", "Waxing crescent", "First quarter", "Waxing gibbous", "Full moon", "Waning gibbous",
                "Last quarter", "Waning crescent"};
        int idx = Astro.phaseIndex(age);
        Card card = new Card(c, s, t("Луна", "Moon"), p);
        boolean b = big(p);
        LinearLayout row = row(c);
        row.addView(new MoonView(c, age, b ? 84 : 46));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Math.round(dp(c, 14)), 0, 0, 0);
        col.addView(text(c, s.regular, ru() ? ruNames[idx] : enNames[idx], b ? 18 : 16, WHITE));
        add(c, col, s.regular, t("Освещена на ", "Lit ") + pct + "%", 13, DIM, 3);
        if (b || p.w >= 3) {
            double synodic = 29.530588853;
            int toFull = (int) Math.round(((0.5 - age + 1) % 1.0) * synodic);
            int toNew = (int) Math.round(((1 - age) % 1.0) * synodic);
            add(c, col, s.regular, t("Полнолуние через ", "Full moon in ") + toFull + t(" дн.", " d"), 13, DIM, b ? 10 : 3);
            add(c, col, s.regular, t("Новолуние через ", "New moon in ") + toNew + t(" дн.", " d"), 13, DIM, 3);
        }
        row.addView(col);
        card.body.addView(row);
        return card.root;
    }

    private static View sun(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Солнце", "Sun"), p);
        double[] where = Data.location(c);
        long[] times = where == null ? null : Astro.sunTimes(where[0], where[1], System.currentTimeMillis(), TimeZone.getDefault());
        if (times == null) {
            add(c, card.body, s.light, where == null ? t("Нет места", "No location") : t("Полярный день или ночь", "Polar day or night"), 16, DIM, 0);
            return card.root;
        }
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.getDefault());
        long len = (times[1] - times[0]) / 60000L;
        boolean b = big(p);
        LinearLayout top = row(c);
        top.addView(text(c, s.light, "↑ " + f.format(new Date(times[0])), b ? 30 : 24, WHITE));
        top.addView(spacer(c));
        top.addView(text(c, s.light, "↓ " + f.format(new Date(times[1])), b ? 30 : 24, WHITE));
        card.body.addView(top, full(c, 0));
        add(c, card.body, s.regular, t("День ", "Day ") + len / 60 + t(" ч ", " h ") + len % 60 + t(" мин", " min"), 13, DIM, 4);
        if (b) {
            long now = System.currentTimeMillis();
            float frac = Math.max(0f, Math.min(1f, (now - times[0]) / (float) (times[1] - times[0])));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(dp(c, card.innerDp)), Math.round(dp(c, 5)));
            lp.topMargin = Math.round(dp(c, 14));
            card.body.addView(new BarView(c, frac, Math.round(dp(c, card.innerDp))), lp);
            long noon = (times[0] + times[1]) / 2;
            add(c, card.body, s.regular, t("Полдень ", "Noon ") + f.format(new Date(noon)), 13, DIM, 8);
        }
        return card.root;
    }

    private static View world(Context c, Widgets.Surface s, Widgets.Placement p) {
        int set = Math.max(0, Math.min(CITY_SETS_RU.length - 1, Widgets.param(c, "world", "set", 0)));
        Card card = new Card(c, s, t("Мировые часы", "World clocks"), p);
        int count = big(p) ? 6 : 3;
        boolean twoColumns = count > 3 && p.w >= 3;
        LinearLayout holder = new LinearLayout(c);
        holder.setOrientation(twoColumns ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout[] cols = twoColumns ? new LinearLayout[] {new LinearLayout(c), new LinearLayout(c)} : new LinearLayout[] {holder};
        for (LinearLayout col : cols) col.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < count; i++) {
            LinearLayout col = twoColumns ? cols[i / 3] : holder;
            LinearLayout r = row(c);
            int nameWidth = twoColumns ? (card.innerDp / 2 - 52) : Math.min(120, card.innerDp - 62);
            TextView name = text(c, s.regular, ru() ? CITY_SETS_RU[set][i] : CITY_SETS_EN[set][i], 14, DIM);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(name, new LinearLayout.LayoutParams(Math.round(dp(c, nameWidth)), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextClock clock = new TextClock(c);
            clock.setTimeZone(CITY_ZONES[set][i]);
            clock.setFormat24Hour("HH:mm");
            clock.setFormat12Hour("HH:mm");
            clock.setTextColor(WHITE);
            clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            clock.setTypeface(s.light);
            clock.setIncludeFontPadding(false);
            r.addView(clock);
            col.addView(r, wrap(c, i % 3 == 0 ? 0 : (big(p) ? 7 : 4)));
        }
        if (twoColumns) {
            holder.addView(cols[0], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            holder.addView(cols[1], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        card.body.addView(holder, full(c, 0));
        return card.root;
    }

    /** A bar from 0 to 1. */
    private static final class BarView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float value;
        private final int widthPx;

        BarView(Context c, float value, int widthPx) {
            super(c);
            this.widthPx = widthPx;
            this.value = Math.max(0f, Math.min(1f, value));
            track.setColor(FAINT);
            fill.setColor(0xFFFFFFFF);
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(widthPx, Math.round(dp(getContext(), 5)));
        }

        @Override protected void onDraw(Canvas canvas) {
            float r = getHeight() / 2f;
            rect.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(rect, r, r, track);
            rect.set(0, 0, Math.max(getHeight(), getWidth() * value), getHeight());
            canvas.drawRoundRect(rect, r, r, fill);
        }
    }

    private static View progress(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Прогресс", "Progress"), p);
        Calendar now = Calendar.getInstance();
        float day = (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)) / 1440f;
        int dow = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday = 0
        float week = (dow + day) / 7f;
        float month = (now.get(Calendar.DAY_OF_MONTH) - 1 + day) / now.getActualMaximum(Calendar.DAY_OF_MONTH);
        float year = (now.get(Calendar.DAY_OF_YEAR) - 1 + day) / now.getActualMaximum(Calendar.DAY_OF_YEAR);
        String[] names = {t("День", "Day"), t("Неделя", "Week"), t("Месяц", "Month"), t("Год", "Year")};
        float[] values = {day, week, month, year};
        boolean b = big(p);
        int labelDp = 56, pctDp = 38;
        int barDp = Math.max(50, card.innerDp - labelDp - pctDp);
        for (int i = 0; i < 4; i++) {
            LinearLayout r = row(c);
            r.addView(text(c, s.regular, names[i], b ? 13 : 11, DIM), new LinearLayout.LayoutParams(Math.round(dp(c, labelDp)),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            r.addView(new BarView(c, values[i], Math.round(dp(c, barDp))));
            TextView pct = text(c, s.regular, Math.round(values[i] * 100) + "%", b ? 13 : 11, WHITE);
            pct.setGravity(Gravity.END);
            r.addView(pct, new LinearLayout.LayoutParams(Math.round(dp(c, pctDp)), ViewGroup.LayoutParams.WRAP_CONTENT));
            card.body.addView(r, full(c, i == 0 ? 0 : (b ? 14 : 4)));
        }
        return card.root;
    }

    private static View calendar(Context c, Widgets.Surface s, Widgets.Placement p) {
        Calendar now = Calendar.getInstance();
        SimpleDateFormat month = new SimpleDateFormat("LLLL yyyy", Locale.getDefault());
        Card card = new Card(c, s, month.format(now.getTime()), p);
        String[] days = ru() ? new String[] {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"}
                : new String[] {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
        int today = now.get(Calendar.DAY_OF_MONTH);
        boolean b = big(p);
        // The seven columns share the inner width; a month on a tall card uses smaller discs so six weeks fit.
        int cell = card.innerDp / 7;
        int size = Math.min(cell - 2, b ? 24 : 28);
        LinearLayout head = new LinearLayout(c);
        for (int di = 0; di < days.length; di++) {
            String d = days[di];
            TextView v = text(c, s.medium, d, 11, di >= 5 ? 0xB3FF9C9C : DIM);
            v.setGravity(Gravity.CENTER);
            head.addView(v, new LinearLayout.LayoutParams(Math.round(dp(c, cell)), ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        card.body.addView(head, wrap(c, 0));
        Calendar start = (Calendar) now.clone();
        if (b) {
            start.set(Calendar.DAY_OF_MONTH, 1);
            start.add(Calendar.DAY_OF_MONTH, -((start.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        } else {
            start.add(Calendar.DAY_OF_MONTH, -((now.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        }
        int weeks = 1;
        if (b) {
            Calendar end = (Calendar) now.clone();
            end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
            long span = (end.getTimeInMillis() - start.getTimeInMillis()) / 86400000L + 1;
            weeks = (int) Math.min(6, Math.ceil(span / 7.0));
        }
        for (int w = 0; w < weeks; w++) {
            LinearLayout weekRow = new LinearLayout(c);
            for (int i = 0; i < 7; i++) {
                int d = start.get(Calendar.DAY_OF_MONTH);
                boolean inMonth = start.get(Calendar.MONTH) == now.get(Calendar.MONTH);
                boolean isToday = d == today && inMonth;
                boolean weekend = i >= 5;
                TextView v = text(c, isToday ? s.medium : s.regular, String.valueOf(d), b ? 13 : 15,
                        isToday ? 0xFF101418 : weekend ? (inMonth || !b ? 0xFFFF9C9C : 0x55FF9C9C) : (inMonth || !b ? WHITE : 0x55FFFFFF));
                v.setGravity(Gravity.CENTER);
                if (isToday) {
                    GradientDrawable dot = new GradientDrawable();
                    dot.setShape(GradientDrawable.OVAL);
                    dot.setColor(0xFFFFFFFF);
                    v.setBackground(dot);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(dp(c, size)), Math.round(dp(c, size)));
                lp.setMargins(Math.round(dp(c, (cell - size) / 2f)), Math.round(dp(c, 1)), Math.round(dp(c, (cell - size) / 2f)), 0);
                weekRow.addView(v, lp);
                start.add(Calendar.DAY_OF_MONTH, 1);
            }
            card.body.addView(weekRow, wrap(c, w == 0 ? 4 : 0));
        }
        return card.root;
    }

    private static final String[] PROVERBS_RU = {
            "Тише едешь — дальше будешь.", "Семь раз отмерь, один раз отрежь.", "Без труда не вытащишь и рыбку из пруда.",
            "Утро вечера мудренее.", "Не имей сто рублей, а имей сто друзей.", "Что посеешь, то и пожнёшь.",
            "Глаза боятся, а руки делают.", "Терпение и труд всё перетрут.", "Мал золотник, да дорог.",
            "Под лежачий камень вода не течёт.", "Один в поле не воин.", "Новое — это хорошо забытое старое.",
            "Дело мастера боится.", "Не боги горшки обжигают.",
            "Повторение — мать учения.", "Волков бояться — в лес не ходить.", "Лучше поздно, чем никогда.",
            "Куй железо, пока горячо.", "Не всё то золото, что блестит.", "Ум хорошо, а два лучше.",
            "Старый друг лучше новых двух.", "Любишь кататься — люби и саночки возить.", "Всякому овощу своё время.",
            "Готовь сани летом, а телегу зимой.", "Сделал дело — гуляй смело.", "Кто рано встаёт, тому Бог подаёт.",
            "Дорога осилится идущим.", "Хорошее начало — половина дела.", "Век живи — век учись.",
            "Лучше один раз увидеть, чем сто раз услышать.", "Тот не ошибается, кто ничего не делает.",
            "На ошибках учатся.", "Бережёного Бог бережёт.", "Не откладывай на завтра то, что можно сделать сегодня.",
            "Всё хорошо, что хорошо кончается.", "Дальше положишь — ближе возьмёшь.", "Лиха беда начало.",
            "Горе не беда, а голова нужна.", "В гостях хорошо, а дома лучше.", "Хлеб — всему голова."};
    private static final String[] PROVERBS_EN = {
            "A journey of a thousand miles begins with a single step.", "Measure twice, cut once.",
            "No pain, no gain.", "Sleep on it: the morning is wiser than the evening.", "A friend in need is a friend indeed.",
            "You reap what you sow.", "Practice makes perfect.", "Slow and steady wins the race.",
            "Good things come to those who wait.", "Better late than never.", "Strike while the iron is hot.",
            "Two heads are better than one.", "Don't count your chickens before they hatch.", "Every cloud has a silver lining.",
            "The best time to plant a tree was twenty years ago. The second best time is now.",
            "Actions speak louder than words.", "It is always darkest before the dawn.", "Rome was not built in a day.",
            "Fall seven times, stand up eight.", "Well begun is half done."};

    private static View proverb(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Пословица дня", "Proverb of the day"), p);
        String[] list = ru() ? PROVERBS_RU : PROVERBS_EN;
        Calendar now = Calendar.getInstance();
        int pick = (now.get(Calendar.YEAR) * 366 + now.get(Calendar.DAY_OF_YEAR)) % list.length;
        boolean b = big(p);
        TextView v = add(c, card.body, s.light, list[pick], b ? (p.w >= 3 ? 26 : 22) : (p.w >= 3 ? 18 : 16), WHITE, 0);
        v.setMaxLines(b ? 6 : 3);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setLineSpacing(dp(c, 2), 1f);
        return card.root;
    }

    private static View rates(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Курсы ЦБ РФ", "Central bank rates"), p);
        List<Data.Rate> list = Data.rates(c, false);
        if (list.isEmpty()) {
            noData(c, s, card);
            return card.root;
        }
        int count = Math.min(list.size(), big(p) ? 6 : 3);
        boolean two = count > 3 && p.w >= 3;
        LinearLayout holder = new LinearLayout(c);
        LinearLayout[] cols = two ? new LinearLayout[] {new LinearLayout(c), new LinearLayout(c)} : new LinearLayout[] {new LinearLayout(c)};
        for (LinearLayout col : cols) col.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < count; i++) {
            Data.Rate r = list.get(i);
            LinearLayout line = row(c);
            line.addView(text(c, s.medium, r.code, 13, DIM), new LinearLayout.LayoutParams(Math.round(dp(c, 42)), ViewGroup.LayoutParams.WRAP_CONTENT));
            String value = r.value >= 1 ? String.format(Locale.getDefault(), "%.2f", r.value) : String.format(Locale.getDefault(), "%.3f", r.value);
            line.addView(text(c, s.light, value + " ₽", 17, WHITE));
            double d = r.value - r.previous;
            TextView delta = text(c, s.regular, (d >= 0 ? "▲ " : "▼ ") + String.format(Locale.getDefault(), r.value >= 1 ? "%.2f" : "%.3f", Math.abs(d)), 12,
                    d >= 0 ? 0xFF7FE0A5 : 0xFFFF8F8F);
            delta.setPadding(Math.round(dp(c, 8)), 0, 0, 0);
            if (!(p.w == 2 && count > 3)) line.addView(delta);
            LinearLayout col = two ? cols[i / 3] : cols[0];
            col.addView(line, wrap(c, i % 3 == 0 ? 0 : (big(p) ? 8 : 3)));
        }
        for (LinearLayout col : cols) {
            holder.addView(col, two ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        card.body.addView(holder, full(c, 0));
        return card.root;
    }

    private static View history(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("В этот день", "On this day"), p);
        String text = Data.history(c, false);
        boolean b = big(p);
        TextView v = add(c, card.body, s.light, text.isEmpty() ? t("Нет данных", "No data yet") : text, b ? (p.w >= 3 ? 19 : 16) : 14,
                text.isEmpty() ? DIM : WHITE, 0);
        v.setMaxLines(b ? 9 : 3);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setLineSpacing(dp(c, 2), 1f);
        return card.root;
    }

    private static View system(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Состояние ТВ", "TV status"), p);
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        ActivityManager am = c.getSystemService(ActivityManager.class);
        if (am != null) am.getMemoryInfo(mem);
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        double freeGb = fs.getAvailableBytes() / 1073741824.0;
        long up = SystemClock.elapsedRealtime() / 60000L;
        String[] allKeys = {t("Память", "Memory"), t("Хранилище", "Storage"), t("Работает", "Up"), t("Система", "System"), t("Устройство", "Device")};
        String[] allVals = {Math.round(mem.availMem / 1048576.0) + t(" МБ свободно", " MB free"),
                String.format(Locale.getDefault(), "%.1f", freeGb) + t(" ГБ свободно", " GB free"),
                (up >= 1440 ? up / 1440 + t(" д ", " d ") : "") + (up / 60) % 24 + t(" ч ", " h ") + up % 60 + t(" мин", " min"),
                "Android " + Build.VERSION.RELEASE, Build.MODEL};
        int rows = big(p) ? 5 : 3;
        for (int i = 0; i < rows; i++) {
            LinearLayout line = row(c);
            line.addView(text(c, s.regular, allKeys[i], 13, DIM), new LinearLayout.LayoutParams(Math.round(dp(c, 84)), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView v = text(c, s.regular, allVals[i], 13, WHITE);
            v.setSingleLine(true);
            v.setEllipsize(TextUtils.TruncateAt.END);
            line.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            card.body.addView(line, full(c, i == 0 ? 0 : (big(p) ? 9 : 4)));
        }
        return card.root;
    }

    private static int daysTo(int target) {
        Calendar now = Calendar.getInstance();
        Object[] t = TARGETS[Math.max(0, Math.min(TARGETS.length - 1, target))];
        Calendar when = Calendar.getInstance();
        when.set(Calendar.HOUR_OF_DAY, 0);
        when.set(Calendar.MINUTE, 0);
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);
        Calendar today = (Calendar) when.clone();
        String kind = (String) t[4];
        if (kind.equals("ny")) {
            when.set(now.get(Calendar.YEAR) + 1, Calendar.JANUARY, 1);
        } else if (kind.equals("weekend")) {
            int dow = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            when.add(Calendar.DAY_OF_MONTH, dow >= 5 ? 0 : 5 - dow);
        } else {
            when.set(now.get(Calendar.YEAR), (Integer) t[2], (Integer) t[3]);
            if (when.before(today)) when.add(Calendar.YEAR, 1);
        }
        return (int) Math.round((when.getTimeInMillis() - today.getTimeInMillis()) / 86400000.0);
    }

    private static View countdown(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Отсчёт", "Countdown"), p);
        boolean b = big(p);
        int shown = b ? 4 : 2;
        LinearLayout grid = new LinearLayout(c);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line = null;
        for (int i = 0; i < shown; i++) {
            if (i % 2 == 0) {
                line = row(c);
                line.setGravity(Gravity.TOP);
                grid.addView(line, full(c, i > 0 ? 14 : 0));
            }
            int target = Widgets.param(c, "countdown", "t" + i, paramDefault("countdown", "t" + i));
            int days = daysTo(target);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(c, s.light, days == 0 ? t("Сегодня", "Today") : days + t(" дн.", " days"), b ? 24 : 26, WHITE));
            Object[] tt = TARGETS[Math.max(0, Math.min(TARGETS.length - 1, target))];
            TextView label = text(c, s.regular, t("до: ", "to: ") + (ru() ? tt[0] : tt[1]), 12, DIM);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(0, Math.round(dp(c, 2)), 0, 0);
            col.addView(label);
            line.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        card.body.addView(grid, full(c, 0));
        return card.root;
    }

    // ---- more cards ------------------------------------------------------------------------------

    private static int dayPick(int n) {
        Calendar now = Calendar.getInstance();
        return (now.get(Calendar.YEAR) * 366 + now.get(Calendar.DAY_OF_YEAR)) % n;
    }

    private static View clock(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Часы", "Clock"), p);
        Date now = new Date();
        boolean b = big(p);
        add(c, card.body, s.light, new java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), b ? 64 : 42, WHITE, 0);
        String date = new java.text.SimpleDateFormat(b ? "EEEE, d MMMM" : "EEE, d MMM", Locale.getDefault()).format(now);
        add(c, card.body, s.regular, date.substring(0, 1).toUpperCase() + date.substring(1), b ? 16 : 13, DIM, b ? 6 : 2);
        return card.root;
    }

    private static View week(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Неделя года", "Week of the year"), p);
        Calendar now = Calendar.getInstance();
        now.setMinimalDaysInFirstWeek(4);
        now.setFirstDayOfWeek(Calendar.MONDAY);
        int days = now.getActualMaximum(Calendar.DAY_OF_YEAR);
        int doy = now.get(Calendar.DAY_OF_YEAR);
        int quarter = now.get(Calendar.MONTH) / 3 + 1;
        boolean b = big(p);
        add(c, card.body, s.light, String.valueOf(now.get(Calendar.WEEK_OF_YEAR)), b ? 44 : 32, WHITE, 0);
        add(c, card.body, s.regular, t("День ", "Day ") + doy + t(" из ", " of ") + days, 13, DIM, 4);
        if (b) {
            add(c, card.body, s.regular, quarter + t(" квартал", ". quarter"), 13, DIM, 4);
            add(c, card.body, s.regular, t("До конца года: ", "Left this year: ") + (days - doy) + t(" дн.", " days"), 13, DIM, 4);
        }
        return card.root;
    }

    /** {month (1-12), day, Russian name, English name} of the public holidays. */
    private static final Object[][] HOLIDAYS = {
            {1, 1, "Новый год", "New Year"}, {1, 7, "Рождество", "Christmas (Orthodox)"},
            {2, 23, "День защитника Отечества", "Defender of the Fatherland Day"},
            {3, 8, "Международный женский день", "International Women's Day"},
            {5, 1, "Праздник Весны и Труда", "Spring and Labour Day"}, {5, 9, "День Победы", "Victory Day"},
            {6, 12, "День России", "Russia Day"}, {11, 4, "День народного единства", "Unity Day"}};

    private static View holidays(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Ближайшие праздники", "Next holidays"), p);
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        int n = HOLIDAYS.length;
        final long[] when = new long[n];
        for (int i = 0; i < n; i++) {
            Calendar h = (Calendar) today.clone();
            h.set(today.get(Calendar.YEAR), (Integer) HOLIDAYS[i][0] - 1, (Integer) HOLIDAYS[i][1]);
            if (h.before(today)) h.add(Calendar.YEAR, 1);
            when[i] = h.getTimeInMillis();
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (x, y) -> Long.compare(when[x], when[y]));
        int rows = big(p) ? 5 : 2;
        for (int i = 0; i < rows; i++) {
            int k = order[i];
            int days = (int) Math.round((when[k] - today.getTimeInMillis()) / 86400000.0);
            LinearLayout line = row(c);
            TextView name = text(c, s.regular, ru() ? (String) HOLIDAYS[k][2] : (String) HOLIDAYS[k][3], 13, WHITE);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView d = text(c, s.light, days == 0 ? t("сегодня", "today") : days + t(" дн.", " d"), 15, days <= 7 ? WHITE : DIM);
            d.setPadding(Math.round(dp(c, 10)), 0, 0, 0);
            line.addView(d);
            card.body.addView(line, full(c, i == 0 ? 0 : (big(p) ? 10 : 6)));
        }
        return card.root;
    }

    private static final String[] FACTS_RU = {
            "У осьминога три сердца и голубая кровь.", "Мёд практически не портится: его находили в гробницах возрастом в тысячи лет.",
            "На Венере сутки длиннее года.", "Банан — это ягода, а клубника — нет.",
            "Сердце креветки находится в голове.", "Молния нагревает воздух сильнее, чем поверхность Солнца.",
            "Эйфелева башня летом выше примерно на 15 см из-за расширения металла.", "У жирафа и человека одинаковое число шейных позвонков — семь.",
            "Свет от Солнца идёт до Земли около восьми минут.", "Самая короткая война длилась меньше часа: Англо-занзибарская 1896 года.",
            "Горячая вода иногда замерзает быстрее холодной — это эффект Мпембы.",
            "Спагетти — это множественное число слова «спагетто».", "Крокодилы не умеют высовывать язык.",
            "Луна удаляется от Земли примерно на 3,8 см в год.", "Ленивцы спускаются на землю примерно раз в неделю.",
            "Отпечаток носа у собаки уникален, как отпечаток пальца человека.", "Слово «робот» придумал Карел Чапек.",
            "На Марсе закаты синие.", "Пчела может посетить тысячи цветков за день."};
    private static final String[] FACTS_EN = {
            "An octopus has three hearts and blue blood.", "Honey barely spoils: it has been found in tombs thousands of years old.",
            "A day on Venus is longer than its year.", "A banana is a berry; a strawberry is not.",
            "A shrimp's heart is in its head.", "Lightning heats the air hotter than the surface of the Sun.",
            "The Eiffel Tower is about 15 cm taller in summer.", "A giraffe and a human both have seven neck vertebrae.",
            "Sunlight takes about eight minutes to reach Earth.", "The shortest war lasted under an hour: Anglo-Zanzibar, 1896.",
            "Hot water can freeze faster than cold: the Mpemba effect.",
            "The Moon drifts away from Earth about 3.8 cm a year.", "Sloths come down to the ground about once a week.",
            "A dog's nose print is as unique as a human fingerprint.", "The word \"robot\" was coined by Karel Capek.",
            "Sunsets on Mars are blue.", "Crocodiles cannot stick out their tongues.",
            "A honeybee can visit thousands of flowers in a day."};

    private static View fact(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Факт дня", "Fact of the day"), p);
        String[] list = ru() ? FACTS_RU : FACTS_EN;
        boolean b = big(p);
        TextView v = add(c, card.body, s.light, list[dayPick(list.length)], b ? (p.w >= 3 ? 24 : 20) : (p.w >= 3 ? 16 : 14), WHITE, 0);
        v.setMaxLines(b ? 7 : 3);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setLineSpacing(dp(c, 2), 1f);
        return card.root;
    }

    private static final String[][] QUOTES_RU = {
            {"Всё течёт, всё изменяется.", "Гераклит"}, {"Я знаю, что ничего не знаю.", "Сократ"},
            {"Красота спасёт мир.", "Ф. Достоевский"}, {"Знание — сила.", "Ф. Бэкон"},
            {"Лучшее — враг хорошего.", "Вольтер"}, {"Мыслю, следовательно, существую.", "Р. Декарт"},
            {"Воображение важнее знания.", "А. Эйнштейн"}, {"Зри в корень!", "К. Прутков"},
            {"Путь в тысячу ли начинается с первого шага.", "Лао-цзы"},
            {"В человеке всё должно быть прекрасно.", "А. Чехов"},
            {"Единственный способ делать великие дела — любить то, что делаешь.", "С. Джобс"},
            {"Нельзя дважды войти в одну и ту же реку.", "Гераклит"}};
    private static final String[][] QUOTES_EN = {
            {"Everything flows, nothing stays.", "Heraclitus"}, {"I know that I know nothing.", "Socrates"},
            {"Beauty will save the world.", "F. Dostoevsky"}, {"Knowledge is power.", "F. Bacon"},
            {"The perfect is the enemy of the good.", "Voltaire"}, {"I think, therefore I am.", "R. Descartes"},
            {"Imagination is more important than knowledge.", "A. Einstein"}, {"Look to the root.", "K. Prutkov"},
            {"A journey of a thousand miles begins with a single step.", "Lao Tzu"},
            {"Everything about a person should be beautiful.", "A. Chekhov"},
            {"The only way to do great work is to love what you do.", "S. Jobs"},
            {"No man ever steps in the same river twice.", "Heraclitus"}};

    private static View quote(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Цитата дня", "Quote of the day"), p);
        String[][] list = ru() ? QUOTES_RU : QUOTES_EN;
        String[] q = list[dayPick(list.length)];
        boolean b = big(p);
        TextView v = add(c, card.body, s.light, q[0], b ? (p.w >= 3 ? 24 : 20) : (p.w >= 3 ? 17 : 14), WHITE, 0);
        v.setMaxLines(b ? 6 : 3);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setLineSpacing(dp(c, 2), 1f);
        add(c, card.body, s.regular, "— " + q[1], 12, DIM, b ? 10 : 4);
        return card.root;
    }

    private static final String[][] WORDS_RU = {
            {"Эфемерный", "недолговечный, существующий очень короткое время"}, {"Умиротворение", "состояние полного покоя"},
            {"Рачительный", "бережливый и заботливый"}, {"Лаконичный", "краткий, немногословный"},
            {"Дотошный", "внимательный до мелочей"}, {"Пытливый", "любознательный, стремящийся узнать"},
            {"Амбивалентный", "двойственный, содержащий противоположности"}, {"Эклектика", "соединение разнородных элементов"},
            {"Ностальгия", "тоска по прошлому"}, {"Нюанс", "тонкий оттенок, едва заметное различие"},
            {"Консенсус", "общее согласие по спорному вопросу"}, {"Оптимум", "наиболее благоприятное сочетание условий"}};
    private static final String[][] WORDS_EN = {
            {"Ephemeral", "lasting for a very short time"}, {"Serendipity", "a happy discovery made by chance"},
            {"Petrichor", "the smell of rain on dry ground"}, {"Eloquent", "fluent and persuasive in speech"},
            {"Resilient", "able to recover quickly"}, {"Ubiquitous", "found everywhere"},
            {"Mellifluous", "sweet and smooth to hear"}, {"Luminous", "giving off light"},
            {"Halcyon", "calm and peaceful, of a happy past"}, {"Nuance", "a subtle difference"},
            {"Ambivalent", "having mixed feelings"}, {"Consensus", "general agreement"}};

    private static View word(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Слово дня", "Word of the day"), p);
        String[][] list = ru() ? WORDS_RU : WORDS_EN;
        String[] w = list[dayPick(list.length)];
        boolean b = big(p);
        TextView head = add(c, card.body, s.light, w[0], b ? 34 : 26, WHITE, 0);
        head.setSingleLine(true);
        TextView v = add(c, card.body, s.regular, w[1], b ? 16 : 13, DIM, b ? 8 : 3);
        v.setMaxLines(b ? 4 : 2);
        v.setEllipsize(TextUtils.TruncateAt.END);
        return card.root;
    }

    // ---- even more cards -------------------------------------------------------------------------

    private static View date(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, new java.text.SimpleDateFormat("LLLL", Locale.getDefault()).format(new Date()), p);
        Calendar now = Calendar.getInstance();
        boolean b = big(p);
        add(c, card.body, s.light, String.valueOf(now.get(Calendar.DAY_OF_MONTH)), b ? 76 : 44, WHITE, 0);
        String day = new java.text.SimpleDateFormat("EEEE", Locale.getDefault()).format(now.getTime());
        int weekend = now.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ? WEEKEND : DIM;
        add(c, card.body, s.regular, day.substring(0, 1).toUpperCase() + day.substring(1), b ? 18 : 14, weekend, b ? 4 : 0);
        return card.root;
    }

    private static View season(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Время года", "Season"), p);
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        // Meteorological seasons: they start on the 1st of March, June, September and December.
        int[] starts = {Calendar.DECEMBER, Calendar.MARCH, Calendar.JUNE, Calendar.SEPTEMBER};
        String[] ru = {"Зима", "Весна", "Лето", "Осень"}, en = {"Winter", "Spring", "Summer", "Autumn"};
        int m = now.get(Calendar.MONTH);
        int idx = m == Calendar.DECEMBER || m <= Calendar.FEBRUARY ? 0 : m <= Calendar.MAY ? 1 : m <= Calendar.AUGUST ? 2 : 3;
        Calendar from = Calendar.getInstance();
        from.set(m == Calendar.JANUARY || m == Calendar.FEBRUARY ? year - 1 : year, starts[idx], 1, 0, 0, 0);
        Calendar to = (Calendar) from.clone();
        to.add(Calendar.MONTH, 3);
        long total = to.getTimeInMillis() - from.getTimeInMillis();
        long gone = now.getTimeInMillis() - from.getTimeInMillis();
        int daysLeft = (int) Math.max(0, Math.round((to.getTimeInMillis() - now.getTimeInMillis()) / 86400000.0));
        boolean b = big(p);
        add(c, card.body, s.light, ru() ? ru[idx] : en[idx], b ? 38 : 28, WHITE, 0);
        add(c, card.body, s.regular, t("До ", "Until ") + (ru() ? new String[] {"весны", "лета", "осени", "зимы"}[idx] : en[(idx + 1) % 4].toLowerCase(Locale.getDefault()))
                + ": " + daysLeft + t(" дн.", " days"), 13, DIM, 4);
        if (b) {
            int h = Math.round(dp(c, 6));
            GradientDrawable bar = new GradientDrawable();
            bar.setCornerRadius(dp(c, 3));
            bar.setColor(FAINT);
            GradientDrawable fillBg = new GradientDrawable();
            fillBg.setCornerRadius(dp(c, 3));
            fillBg.setColor(WHITE);
            View track = new View(c);
            track.setBackground(bar);
            View fill = new View(c);
            fill.setBackground(fillBg);
            int w = Math.round(dp(c, card.innerDp) * Math.max(0.02f, Math.min(1f, gone / (float) total)));
            android.widget.FrameLayout stack = new android.widget.FrameLayout(c);
            stack.addView(track, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
            stack.addView(fill, new android.widget.FrameLayout.LayoutParams(w, h));
            card.body.addView(stack, full(c, 14));
            add(c, card.body, s.regular, Math.round(100f * gone / total) + "%", 12, DIM, 6);
        }
        return card.root;
    }

    private static final double[] PHASE_AGES = {0, 7.3826, 14.7653, 22.1479};

    private static View phases(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Фазы Луны", "Moon phases"), p);
        String[] names = ru() ? new String[] {"Новолуние", "Первая четверть", "Полнолуние", "Последняя четверть"}
                : new String[] {"New moon", "First quarter", "Full moon", "Last quarter"};
        long now = System.currentTimeMillis();
        double age = Astro.moonAge(now);
        final long[] when = new long[4];
        Integer[] order = new Integer[4];
        for (int i = 0; i < 4; i++) {
            double ahead = PHASE_AGES[i] - age;
            while (ahead < 0) ahead += 29.530588853;
            when[i] = now + Math.round(ahead * 86400000.0);
            order[i] = i;
        }
        java.util.Arrays.sort(order, (x, y) -> Long.compare(when[x], when[y]));
        int rows = big(p) ? 4 : 2;
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d MMM", Locale.getDefault());
        for (int i = 0; i < rows; i++) {
            int k = order[i];
            LinearLayout line = row(c);
            TextView name = text(c, s.regular, names[k], 13, WHITE);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView d = text(c, s.light, f.format(new Date(when[k])), 15, DIM);
            d.setPadding(Math.round(dp(c, 10)), 0, 0, 0);
            line.addView(d);
            card.body.addView(line, full(c, i == 0 ? 0 : (big(p) ? 10 : 6)));
        }
        return card.root;
    }

    private static final String[] TIPS_RU = {
            "Выпейте стакан воды.", "Встаньте и потянитесь минуту.", "Отведите взгляд от экрана и посмотрите вдаль 20 секунд.",
            "Сделайте пять медленных глубоких вдохов.", "Проветрите комнату.", "Сделайте короткую прогулку после ужина.",
            "Разомните шею и плечи.", "Лягте сегодня на полчаса раньше.", "Позвоните близкому человеку.",
            "Выпрямите спину и расслабьте плечи.", "Выключите экраны за час до сна.", "Съешьте сегодня что-нибудь свежее."};
    private static final String[] TIPS_EN = {
            "Drink a glass of water.", "Stand up and stretch for a minute.", "Look into the distance for 20 seconds, away from the screen.",
            "Take five slow, deep breaths.", "Air the room.", "Take a short walk after dinner.",
            "Loosen your neck and shoulders.", "Go to bed half an hour earlier tonight.", "Call someone close to you.",
            "Straighten your back and drop your shoulders.", "Turn the screens off an hour before bed.", "Eat something fresh today."};

    private static View tip(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Совет дня", "Tip of the day"), p);
        String[] list = ru() ? TIPS_RU : TIPS_EN;
        boolean b = big(p);
        TextView v = add(c, card.body, s.light, list[dayPick(list.length)], b ? (p.w >= 3 ? 24 : 20) : (p.w >= 3 ? 17 : 15), WHITE, 0);
        v.setMaxLines(b ? 6 : 3);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setLineSpacing(dp(c, 2), 1f);
        return card.root;
    }

    private static final String[] DISHES_RU = {
            "Борщ со сметаной", "Паста с томатами и базиликом", "Плов с говядиной", "Гречка с грибами", "Овощное рагу",
            "Запечённая курица с картофелем", "Сырники со сметаной", "Омлет с зеленью", "Куриный суп с лапшой", "Пельмени с маслом",
            "Рыба на пару с овощами", "Блины с творогом", "Греческий салат", "Тушёная капуста с колбасками", "Рис с овощами и яйцом"};
    private static final String[] DISHES_EN = {
            "Borscht with sour cream", "Pasta with tomatoes and basil", "Beef pilaf", "Buckwheat with mushrooms", "Vegetable stew",
            "Roast chicken with potatoes", "Cottage-cheese pancakes", "Herb omelette", "Chicken noodle soup", "Dumplings with butter",
            "Steamed fish with vegetables", "Crepes with curd cheese", "Greek salad", "Braised cabbage with sausages", "Rice with vegetables and egg"};

    private static View dinner(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Что приготовить", "What to cook"), p);
        String[] list = ru() ? DISHES_RU : DISHES_EN;
        boolean b = big(p);
        TextView v = add(c, card.body, s.light, list[dayPick(list.length)], b ? (p.w >= 3 ? 30 : 24) : (p.w >= 3 ? 20 : 17), WHITE, 0);
        v.setMaxLines(b ? 5 : 3);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setLineSpacing(dp(c, 2), 1f);
        return card.root;
    }

    private static final String[][] FILMS_RU = {
            {"Бриллиантовая рука", "1968"}, {"Операция «Ы» и другие приключения Шурика", "1965"}, {"Ирония судьбы, или С лёгким паром!", "1975"},
            {"Москва слезам не верит", "1979"}, {"Иван Васильевич меняет профессию", "1973"}, {"Джентльмены удачи", "1971"},
            {"Кавказская пленница", "1966"}, {"Служебный роман", "1977"}, {"Мимино", "1977"}, {"Назад в будущее", "1985"},
            {"Побег из Шоушенка", "1994"}, {"Форрест Гамп", "1994"}, {"Начало", "2010"}, {"Интерстеллар", "2014"}, {"Леон", "1994"}};
    private static final String[][] FILMS_EN = {
            {"Back to the Future", "1985"}, {"The Shawshank Redemption", "1994"}, {"Forrest Gump", "1994"}, {"Inception", "2010"},
            {"Interstellar", "2014"}, {"Leon: The Professional", "1994"}, {"The Godfather", "1972"}, {"The Matrix", "1999"},
            {"Casablanca", "1942"}, {"Spirited Away", "2001"}, {"Amelie", "2001"}, {"Gladiator", "2000"},
            {"The Lion King", "1994"}, {"Jurassic Park", "1993"}, {"Groundhog Day", "1993"}};

    private static View movie(Context c, Widgets.Surface s, Widgets.Placement p) {
        Card card = new Card(c, s, t("Что посмотреть", "What to watch"), p);
        String[][] list = ru() ? FILMS_RU : FILMS_EN;
        String[] f = list[dayPick(list.length)];
        boolean b = big(p);
        TextView v = add(c, card.body, s.light, f[0], b ? (p.w >= 3 ? 28 : 22) : (p.w >= 3 ? 19 : 16), WHITE, 0);
        v.setMaxLines(b ? 4 : 2);
        v.setEllipsize(TextUtils.TruncateAt.END);
        add(c, card.body, s.regular, f[1], 13, DIM, b ? 8 : 3);
        return card.root;
    }
}
