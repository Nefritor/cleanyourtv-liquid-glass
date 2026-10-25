package com.gathin.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * What the widgets show that comes from the network: the weather (MET Norway), the central bank's rates and an event
 * of this day (Wikipedia). Each reading is cached as JSON in the app's own preferences with its time, so a widget
 * draws at once from the cache and the network is asked only when the cache is old. All calls block.
 */
public final class Data {
    private static final String TAG = "WidgetData";
    private static final String PREFS = "widget_data";
    static final long WEATHER_TTL = 30L * 60 * 1000;
    static final long RATES_TTL = 6L * 60 * 60 * 1000;
    static final long HISTORY_TTL = 12L * 60 * 60 * 1000;
    private static final String AGENT = "CleanYourTV-Widgets/1.0 (https://github.com/Gathin23/cleanyourtv)";

    private Data() {}

    /** The weather now, with the next hours. */
    public static final class Weather {
        public int temp;
        public double wind;
        public int humidity;
        public String symbolCode = "";
        public final List<int[]> hours = new ArrayList<>();      // {hour of day, temp}
        public final List<String> hourCodes = new ArrayList<>();
    }

    public static final class Rate {
        public String code;
        public double value;
        public double previous;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean fresh(Context c, String key, long ttl) {
        long age = System.currentTimeMillis() - prefs(c).getLong(key + "_at", 0);
        return age >= 0 && age < ttl && prefs(c).contains(key);
    }

    /** The last known place, {lat, lon}, or null: the TV's own coarse location, kept for the offline sun times. */
    public static double[] location(Context c) {
        SharedPreferences p = prefs(c);
        try {
            if (c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || c.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                LocationManager lm = c.getSystemService(LocationManager.class);
                Location best = null;
                if (lm != null) {
                    for (String provider : lm.getProviders(true)) {
                        Location l = lm.getLastKnownLocation(provider);
                        if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                    }
                }
                if (best != null) {
                    p.edit().putFloat("lat", (float) best.getLatitude()).putFloat("lon", (float) best.getLongitude()).apply();
                }
            }
        } catch (RuntimeException ignored) {
            // Use what was stored.
        }
        return p.contains("lat") ? new double[] {p.getFloat("lat", 0f), p.getFloat("lon", 0f)} : null;
    }

    /** The cached weather, or null. */
    public static Weather cachedWeather(Context c) {
        String json = prefs(c).getString("weather", null);
        if (json == null) return null;
        try {
            return parseWeather(new JSONObject(json));
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    public static boolean weatherStale(Context c) {
        return !fresh(c, "weather", WEATHER_TTL);
    }

    /** Fetches and stores the weather; returns it, or null when there is no place or no network. */
    public static Weather refreshWeather(Context c) {
        double[] where = location(c);
        if (where == null) return cachedWeather(c);
        try {
            String url = String.format(Locale.ROOT,
                    "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.2f&lon=%.2f", where[0], where[1]);
            JSONObject root = new JSONObject(get(url));
            JSONArray series = root.getJSONObject("properties").getJSONArray("timeseries");
            JSONObject keep = new JSONObject();
            JSONArray hours = new JSONArray();
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            for (int i = 0; i < series.length() && hours.length() < 6; i++) {
                JSONObject entry = series.getJSONObject(i);
                JSONObject data = entry.getJSONObject("data");
                JSONObject details = data.getJSONObject("instant").getJSONObject("details");
                JSONObject next = data.optJSONObject("next_1_hours");
                if (i == 0) {
                    keep.put("temp", details.getDouble("air_temperature"));
                    keep.put("wind", details.optDouble("wind_speed", 0));
                    keep.put("hum", details.optDouble("relative_humidity", 0));
                    keep.put("code", next == null ? "" : next.getJSONObject("summary").optString("symbol_code", ""));
                }
                if (i == 0 || i % 2 == 0) {   // every second entry; the series is hourly for the first day
                    long t = iso.parse(entry.getString("time")).getTime();
                    hours.put(new JSONObject().put("t", t).put("temp", details.getDouble("air_temperature"))
                            .put("code", next == null ? "" : next.getJSONObject("summary").optString("symbol_code", "")));
                }
            }
            keep.put("hours", hours);
            prefs(c).edit().putString("weather", keep.toString()).putLong("weather_at", System.currentTimeMillis()).apply();
            return parseWeather(keep);
        } catch (IOException | org.json.JSONException | java.text.ParseException | RuntimeException e) {
            Log.w(TAG, "weather: " + e);
            return cachedWeather(c);
        }
    }

    private static Weather parseWeather(JSONObject o) throws org.json.JSONException {
        Weather w = new Weather();
        w.temp = (int) Math.round(o.getDouble("temp"));
        w.wind = o.optDouble("wind", 0);
        w.humidity = (int) Math.round(o.optDouble("hum", 0));
        w.symbolCode = o.optString("code", "");
        JSONArray hours = o.optJSONArray("hours");
        Calendar cal = Calendar.getInstance();
        for (int i = 0; hours != null && i < hours.length(); i++) {
            JSONObject h = hours.getJSONObject(i);
            cal.setTimeInMillis(h.getLong("t"));
            w.hours.add(new int[] {cal.get(Calendar.HOUR_OF_DAY), (int) Math.round(h.getDouble("temp"))});
            w.hourCodes.add(h.optString("code", ""));
        }
        return w;
    }

    /** The central bank's USD, EUR, CNY, GBP, TRY and KZT from cbr-xml-daily.ru, or the cached ones. */
    public static List<Rate> rates(Context c, boolean network) {
        if (network && !fresh(c, "rates", RATES_TTL)) {
            try {
                JSONObject v = new JSONObject(get("https://www.cbr-xml-daily.ru/daily_json.js")).getJSONObject("Valute");
                JSONArray keep = new JSONArray();
                for (String code : new String[] {"USD", "EUR", "CNY", "GBP", "TRY", "KZT"}) {
                    JSONObject r = v.getJSONObject(code);
                    double nominal = Math.max(1, r.optDouble("Nominal", 1));
                    keep.put(new JSONObject().put("code", code).put("value", r.getDouble("Value") / nominal)
                            .put("prev", r.getDouble("Previous") / nominal));
                }
                prefs(c).edit().putString("rates", keep.toString()).putLong("rates_at", System.currentTimeMillis()).apply();
            } catch (IOException | org.json.JSONException | RuntimeException e) {
                Log.w(TAG, "rates: " + e);
            }
        }
        List<Rate> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("rates", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Rate r = new Rate();
                r.code = o.getString("code");
                r.value = o.getDouble("value");
                r.previous = o.getDouble("prev");
                out.add(r);
            }
        } catch (org.json.JSONException ignored) {
            // No rates yet.
        }
        return out;
    }

    /** "2003: text" for today from the Russian Wikipedia's selected events, one per day, or "". */
    public static String history(Context c, boolean network) {
        Calendar now = Calendar.getInstance();
        String day = now.get(Calendar.MONTH) + 1 + "-" + now.get(Calendar.DAY_OF_MONTH);
        SharedPreferences p = prefs(c);
        if (network && !(day.equals(p.getString("history_day", "")) && fresh(c, "history", HISTORY_TTL))) {
            try {
                String url = String.format(Locale.ROOT, "https://ru.wikipedia.org/api/rest_v1/feed/onthisday/selected/%02d/%02d",
                        now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));
                JSONArray events = new JSONObject(get(url)).getJSONArray("selected");
                String best = "";
                int seed = now.get(Calendar.DAY_OF_YEAR);
                for (int k = 0; k < events.length() && best.isEmpty(); k++) {
                    JSONObject e = events.getJSONObject((seed + k) % events.length());
                    String text = e.optString("text", "").trim();
                    if (text.length() >= 25 && text.length() <= 150) best = e.optInt("year", 0) + " — " + text;
                }
                if (!best.isEmpty()) {
                    p.edit().putString("history", best).putString("history_day", day).putLong("history_at", System.currentTimeMillis()).apply();
                }
            } catch (IOException | org.json.JSONException | RuntimeException e) {
                Log.w(TAG, "history: " + e);
            }
        }
        return day.equals(p.getString("history_day", "")) ? p.getString("history", "") : "";
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6_000);
        c.setReadTimeout(8_000);
        c.setRequestProperty("User-Agent", AGENT);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) != -1; ) {
                out.write(buf, 0, n);
                if (out.size() > 512 * 1024) throw new IOException("reply too large");
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    /** MET Norway's symbol_code ("partlycloudy_night", "lightrain", ...) as a plain symbol every TV font has. */
    public static String symbol(String code) {
        boolean night = code.endsWith("_night");
        String base = code.replaceAll("_(day|night|polartwilight)$", "");
        if (base.contains("thunder")) return "⚡";
        if (base.contains("snow") || base.contains("sleet")) return "❄";
        if (base.contains("rain") || base.contains("drizzle")) return "☂";
        if (base.equals("fog") || base.equals("cloudy")) return "☁";
        if (base.startsWith("partlycloudy") || base.startsWith("fair")) return "⛅";
        return night ? "☾" : "☀";
    }
}
