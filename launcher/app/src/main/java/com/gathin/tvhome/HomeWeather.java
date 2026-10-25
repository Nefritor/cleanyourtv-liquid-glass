package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The weather line under the clock (theme key show_weather): "1°  ☾" from MET Norway for the TV's rough location (the
 * last position Android knows, rounded to about 1 km), refreshed every 30 minutes while Home is on screen. Nothing is
 * requested unless the theme asks for it.
 */
final class HomeWeather {
    private static final String TAG = "HomeWeather";
    private static final long REFRESH_MS = 30L * 60 * 1000;
    private static final String PREFS = "home_weather";

    private HomeWeather() {}

    /** The last reading ("" when there is none yet). */
    static String cached(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("text", "");
    }

    /** True when the reading is older than 30 minutes. */
    static boolean stale(Context context) {
        long at = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("at", 0);
        long age = System.currentTimeMillis() - at;
        return age < 0 || age >= REFRESH_MS;
    }

    /** Fetches a new reading and stores it; returns it, or "" when there is no location or no network. Blocking. */
    static String refresh(Context context) {
        try {
            if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return "";
            }
            LocationManager lm = context.getSystemService(LocationManager.class);
            Location best = null;
            if (lm != null) {
                for (String provider : lm.getProviders(true)) {
                    Location l = lm.getLastKnownLocation(provider);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                }
            }
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            double lat, lon;
            if (best != null) {
                lat = best.getLatitude();
                lon = best.getLongitude();
                prefs.edit().putFloat("lat", (float) lat).putFloat("lon", (float) lon).apply();
            } else if (prefs.contains("lat")) {
                lat = prefs.getFloat("lat", 0f);
                lon = prefs.getFloat("lon", 0f);
            } else {
                return "";
            }
            String text = fetch(lat, lon);
            prefs.edit().putString("text", text).putLong("at", System.currentTimeMillis()).apply();
            return text;
        } catch (IOException | org.json.JSONException | RuntimeException e) {
            Log.w(TAG, e.toString());
            return "";
        }
    }

    private static String fetch(double latitude, double longitude) throws IOException, org.json.JSONException {
        String url = String.format(Locale.ROOT,
                "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.2f&lon=%.2f", latitude, longitude);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6_000);
        c.setReadTimeout(8_000);
        c.setRequestProperty("User-Agent", "CleanYourTV-Home/1.0 (https://github.com/Gathin23/cleanyourtv)");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) != -1; ) {
                out.write(buf, 0, n);
                if (out.size() > 256 * 1024) throw new IOException("reply too large");
            }
            JSONObject first = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8))
                    .getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0).getJSONObject("data");
            int temp = (int) Math.round(first.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature"));
            JSONObject next = first.optJSONObject("next_1_hours");
            String code = next == null ? "" : next.getJSONObject("summary").optString("symbol_code", "");
            return temp + "°  " + symbol(code);
        } finally {
            c.disconnect();
        }
    }

    /** MET Norway's symbol_code ("partlycloudy_night", "lightrain", ...) as a plain symbol every TV font has. */
    static String symbol(String code) {
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
