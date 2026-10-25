package com.gathin.tvhome;

import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pictures apps publish for their own rows (the TV provider's preview programs: Twitch's live streams, YouTube's
 * recommended videos, RuTube's, LazyMedia's), kept in the cache folder so the focused tile's app can show them behind
 * Home (theme key app_art). Only the apps' recommendations are read: never "Continue watching", which is the owner's
 * viewing history. Needs READ_TV_LISTINGS (granted from a shell: pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS).
 */
final class AppArt {
    private static final String TAG = "AppArt";
    private static final String FOLDER = "appart";
    private static final long REFRESH_MS = 30L * 60 * 1000;
    private static final int PER_APP = 10;
    private static final int MAX_BYTES = 1_500_000;
    private static final Object LOCK = new Object();
    private static final java.util.concurrent.ScheduledExecutorService WATCHDOG = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

    private AppArt() {}

    static File dir(Context context, String pkg) {
        return new File(new File(context.getCacheDir(), FOLDER), pkg);
    }

    /** The cached pictures of an app, or none. */
    static List<File> files(Context context, String pkg) {
        List<File> out = new ArrayList<>();
        File[] all = pkg == null || !pkg.matches("[A-Za-z0-9_.]+") ? null : dir(context, pkg).listFiles();
        if (all != null) for (File f : all) if (f.isFile() && f.length() > 0 && f.getName().endsWith(".jpg")) out.add(f);
        Collections.sort(out);
        return out;
    }

    /** Fetches the pictures again unless that was done within the last 30 minutes. Blocking: call off the main thread. */
    static void sync(Context context) {
        synchronized (LOCK) {
            File marker = new File(context.getCacheDir(), FOLDER + "/.synced");
            if (marker.isFile() && System.currentTimeMillis() - marker.lastModified() < REFRESH_MS) return;
            Log.i(TAG, "sync starts, permission " + context.checkSelfPermission("android.permission.READ_TV_LISTINGS"));
            if (context.checkSelfPermission("android.permission.READ_TV_LISTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return;
            }
            Map<String, List<String>> urls = new HashMap<>();
            try (Cursor c = context.getContentResolver().query(TvContract.PreviewPrograms.CONTENT_URI, null, null, null, null)) {
                if (c == null) return;
                int pkg = c.getColumnIndex("package_name"), poster = c.getColumnIndex("poster_art_uri");
                while (c.moveToNext()) {
                    String p = c.getString(pkg), u = c.getString(poster);
                    if (p == null || u == null || !u.startsWith("https://") || !p.matches("[A-Za-z0-9_.]+")) continue;
                    List<String> list = urls.get(p);
                    if (list == null) {
                        list = new ArrayList<>();
                        urls.put(p, list);
                    }
                    list.add(u);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "provider: " + e);
                return;
            }
            Log.i(TAG, "provider rows for " + urls.size() + " apps");
            //noinspection ResultOfMethodCallIgnored
            marker.getParentFile().mkdirs();
            // One thread per app: a throttled CDN (YouTube's is, here) must not hold the others up.
            List<Thread> workers = new ArrayList<>();
            for (Map.Entry<String, List<String>> e : urls.entrySet()) {
                Thread t = new Thread(() -> refill(context, e.getKey(), e.getValue()), "app-art-" + e.getKey());
                t.start();
                workers.add(t);
            }
            for (Thread t : workers) {
                try {
                    t.join(150_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            try {
                if (!marker.createNewFile()) //noinspection ResultOfMethodCallIgnored
                    marker.setLastModified(System.currentTimeMillis());
            } catch (IOException ignored) { }
        }
    }

    /** Replaces an app's pictures with up to PER_APP new ones (landscape, 480 px wide or more); keeps the old if none arrive. */
    private static void refill(Context context, String pkg, List<String> urls) {
        Collections.shuffle(urls);
        File dir = dir(context, pkg);
        File fresh = new File(dir.getParentFile(), pkg + ".new");
        deleteAll(fresh);
        //noinspection ResultOfMethodCallIgnored
        fresh.mkdirs();
        int saved = 0, failures = 0;
        Log.i(TAG, pkg + ": " + urls.size() + " urls");
        for (String url : urls) {
            if (saved >= PER_APP) break;
            File out = new File(fresh, Integer.toHexString(url.hashCode()) + ".jpg");
            if (out.exists()) continue;
            try {
                download(url, out);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(out.getPath(), o);
                if (o.outWidth < 480 || o.outWidth < o.outHeight * 1.3f) {
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                    continue;
                }
                saved++;
                Log.i(TAG, pkg + " got " + o.outWidth + "x" + o.outHeight);
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, pkg + " " + url + ": " + e);
                //noinspection ResultOfMethodCallIgnored
                out.delete();
                if (++failures >= 2 && saved == 0) break;   // this app's pictures do not come through
            }
        }
        Log.i(TAG, pkg + ": " + saved + " of " + urls.size() + " pictures kept");
        if (saved > 0) {
            deleteAll(dir);
            //noinspection ResultOfMethodCallIgnored
            fresh.renameTo(dir);
        } else {
            deleteAll(fresh);
        }
    }

    private static void download(String url, File out) throws IOException {
        final HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        // The connection retries other addresses by itself, which can take minutes: cut it off from outside.
        final java.util.concurrent.ScheduledFuture<?> cut = WATCHDOG.schedule(c::disconnect, 12, java.util.concurrent.TimeUnit.SECONDS);
        c.setConnectTimeout(5_000);
        c.setReadTimeout(7_000);   // a throttled CDN answers nothing: give up quickly
        c.setRequestProperty("User-Agent", "CleanYourTV-Home/1.0");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            if (c.getContentLengthLong() > MAX_BYTES) throw new IOException("too large");
            long total = 0;
            try (InputStream in = c.getInputStream(); FileOutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("too large");
                    os.write(buf, 0, n);
                }
            }
        } finally {
            cut.cancel(false);
            c.disconnect();
        }
    }

    private static void deleteAll(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteAll(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
