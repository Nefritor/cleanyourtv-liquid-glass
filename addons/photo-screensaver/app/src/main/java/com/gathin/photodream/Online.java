package com.gathin.photodream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The downloaded pictures: files/online/*.jpg (3840 px or less wide, 4K only), a credits.json naming each one's author
 * and licence, and a size cap. sync() keeps the ticked feeds filled and drops what is no longer ticked.
 */
final class Online {
    private static final String TAG = "Online";
    static final String FOLDER = "online";
    static final long CAP_BYTES = 400L * 1024 * 1024;
    private static final long TIME_LIMIT_MS = 15L * 60 * 1000;
    private static final long MAX_DOWNLOAD = 40L * 1024 * 1024;
    private static final long MAX_SOURCE_PIXELS = 80_000_000L;
    private static final String CREDITS = "credits.json";

    interface Progress {
        void on(int files, long bytes, String note);
    }

    /** One sync at a time; held for minutes, so nothing that draws may wait on it. */
    private static final Object SYNC_LOCK = new Object();
    /** Short: reading and writing credits.json. */
    private static final Object CREDITS_LOCK = new Object();

    private Online() {}

    static File dir(Context context) {
        File base = context.getExternalFilesDir(null);
        return base == null ? null : new File(base, FOLDER);
    }

    /** The cached pictures, unordered. */
    static List<File> files(Context context) {
        List<File> out = new ArrayList<>();
        File[] all = dir(context) == null ? null : dir(context).listFiles();
        if (all != null) for (File f : all) if (f.isFile() && f.getName().endsWith(".jpg") && f.length() > 0) out.add(f);
        return out;
    }

    static long bytes(Context context) {
        long total = 0;
        for (File f : files(context)) total += f.length();
        return total;
    }

    /** The author and licence line for a cached picture, or "" for anything else. */
    static String credit(Context context, File file) {
        File parent = file.getParentFile();
        if (parent == null || !FOLDER.equals(parent.getName())) return "";
        JSONObject entry = readCredits(context).optJSONObject(file.getName());
        return entry == null ? "" : entry.optString("t", "");
    }

    private static JSONObject readCredits(Context context) {
        synchronized (CREDITS_LOCK) {
            return readCreditsLocked(context);
        }
    }

    private static JSONObject readCreditsLocked(Context context) {
        try {
            File f = new File(dir(context), CREDITS);
            if (f.isFile()) return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // Start over with an empty list; the pictures stay.
        }
        return new JSONObject();
    }

    private static void writeCredits(Context context, JSONObject credits) {
        try {
            byte[] bytes;
            synchronized (CREDITS_LOCK) {
                bytes = credits.toString().getBytes(StandardCharsets.UTF_8);
            }
            File tmp = new File(dir(context), CREDITS + ".tmp");
            Files.write(tmp.toPath(), bytes);
            synchronized (CREDITS_LOCK) {
                Files.move(tmp.toPath(), new File(dir(context), CREDITS).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            Log.w(TAG, "credits not saved", e);
        }
    }

    /** Fills the cache from the ticked feeds; returns the number of new pictures. Blocking: call off the main thread. */
    static int sync(Context context, Progress progress) {
        synchronized (SYNC_LOCK) {
            File dir = dir(context);
            if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) return 0;
            Set<String> selected = Feed.selected(context);
            List<Feed> feeds = new ArrayList<>();
            for (Feed f : Feed.all(context)) if (selected.contains(f.id)) feeds.add(f);
            JSONObject credits = readCredits(context);
            dropUnselected(context, credits, selected);
            if (feeds.isEmpty()) {
                writeCredits(context, credits);
                return 0;
            }
            Random random = new Random();
            Map<String, Integer> have = new HashMap<>();
            for (File f : files(context)) {
                JSONObject c = credits.optJSONObject(f.getName());
                String id = c == null ? "" : c.optString("f", "");
                have.put(id, count(have, id) + 1);
            }
            Map<String, Deque<Providers.Candidate>> queues = new HashMap<>();
            Map<String, Integer> refills = new HashMap<>();
            Map<String, Boolean> dry = new HashMap<>();
            long start = System.currentTimeMillis();
            int added = 0;
            while (System.currentTimeMillis() - start < TIME_LIMIT_MS) {
                Feed feed = leastFilled(feeds, have, dry, random);
                if (feed == null) break;
                if (bytes(context) >= CAP_BYTES * 0.95 && !makeRoom(context, credits, have, feed.id)) break;
                Deque<Providers.Candidate> q = queues.get(feed.id);
                if (q == null) {
                    q = new ArrayDeque<>();
                    queues.put(feed.id, q);
                }
                if (q.isEmpty()) {
                    int n = refills.containsKey(feed.id) ? refills.get(feed.id) : 0;
                    if (n >= 3) { dry.put(feed.id, true); continue; }
                    refills.put(feed.id, n + 1);
                    try {
                        q.addAll(Providers.fetch(feed, random, 6));
                    } catch (IOException e) {
                        Log.w(TAG, feed.id + ": " + e.getMessage());
                    }
                    if (q.isEmpty()) { dry.put(feed.id, true); continue; }
                }
                Providers.Candidate c = q.poll();
                String name = c.id + ".jpg";
                if (new File(dir, name).exists()) continue;
                if (fetchOne(dir, c, name)) {
                    try {
                        credits.put(name, new JSONObject().put("t", c.credit).put("f", feed.id).put("at", System.currentTimeMillis()));
                    } catch (org.json.JSONException ignored) { }
                    writeCredits(context, credits);
                    have.put(feed.id, count(have, feed.id) + 1);
                    added++;
                    if (progress != null) progress.on(files(context).size(), bytes(context), feed.title);
                }
            }
            return added;
        }
    }

    private static Feed leastFilled(List<Feed> feeds, Map<String, Integer> have, Map<String, Boolean> dry, Random random) {
        List<Feed> live = new ArrayList<>();
        for (Feed f : feeds) if (!dry.containsKey(f.id)) live.add(f);
        if (live.isEmpty()) return null;
        Collections.shuffle(live, random);
        Feed best = live.get(0);
        for (Feed f : live) if (count(have, f.id) < count(have, best.id)) best = f;
        return best;
    }

    private static int count(Map<String, Integer> have, String id) {
        Integer n = have.get(id);
        return n == null ? 0 : n;
    }

    /** Deletes the oldest picture of a better-stocked feed; false when no feed has more than the one that wants room. */
    private static boolean makeRoom(Context context, JSONObject credits, Map<String, Integer> have, String wanting) {
        File victim = null;
        long oldest = Long.MAX_VALUE;
        String victimFeed = null;
        for (File f : files(context)) {
            JSONObject c = credits.optJSONObject(f.getName());
            String id = c == null ? "" : c.optString("f", "");
            if (id.equals(wanting) || count(have, id) <= count(have, wanting) + 1) continue;
            long at = c == null ? 0 : c.optLong("at", 0);
            if (at < oldest) {
                oldest = at;
                victim = f;
                victimFeed = id;
            }
        }
        if (victim == null) return false;
        //noinspection ResultOfMethodCallIgnored
        victim.delete();
        credits.remove(victim.getName());
        have.put(victimFeed, count(have, victimFeed) - 1);
        return true;
    }

    private static void dropUnselected(Context context, JSONObject credits, Set<String> selected) {
        for (File f : files(context)) {
            JSONObject c = credits.optJSONObject(f.getName());
            String id = c == null ? "" : c.optString("f", "");
            if (!selected.contains(id)) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                credits.remove(f.getName());
            }
        }
    }

    /** Downloads one candidate, keeps it only when it really is 4K and landscape, scaled to 3840 wide at most. */
    private static boolean fetchOne(File dir, Providers.Candidate c, String name) {
        File tmp = new File(dir, c.id + ".part");
        File out = new File(dir, name);
        try {
            Net.download(c.url, tmp, MAX_DOWNLOAD);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(tmp.getPath(), bounds);
            int w = bounds.outWidth, h = bounds.outHeight;
            if (w < Providers.MIN_WIDTH || h <= 0 || (long) w * h > MAX_SOURCE_PIXELS) return false;
            double ratio = (double) w / h;
            if (ratio < 1.3 || ratio > 2.4) return false;
            if (w == Providers.MIN_WIDTH && tmp.length() <= Media.IMAGE_MAX) {
                return tmp.renameTo(out);
            }
            int sample = 1;
            while (w / (sample * 2) >= Providers.MIN_WIDTH) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap decoded = BitmapFactory.decodeFile(tmp.getPath(), opts);
            if (decoded == null) return false;
            Bitmap scaled = decoded.getWidth() > Providers.MIN_WIDTH
                    ? Bitmap.createScaledBitmap(decoded, Providers.MIN_WIDTH,
                            Math.round(decoded.getHeight() * (float) Providers.MIN_WIDTH / decoded.getWidth()), true)
                    : decoded;
            try (FileOutputStream os = new FileOutputStream(out)) {
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, os);
            }
            if (scaled != decoded) scaled.recycle();
            decoded.recycle();
            if (out.length() > Media.IMAGE_MAX) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
                return false;
            }
            return true;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            Log.w(TAG, c.id + ": " + e.getMessage());
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }
}
