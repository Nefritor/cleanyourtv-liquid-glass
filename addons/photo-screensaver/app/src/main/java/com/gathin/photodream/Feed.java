package com.gathin.photodream;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One picture source the owner can tick: a Wikimedia Commons category or a NASA search, from res/raw/feeds.txt. */
final class Feed {
    static final String COMMONS = "commons";
    static final String NASA = "nasa";
    static final String MET = "met";

    final String provider;
    final String id;
    final String title;
    final String query;

    private Feed(String provider, String id, String title, String query) {
        this.provider = provider;
        this.id = id;
        this.title = title;
        this.query = query;
    }

    /** The provider's heading in the picker. */
    static String providerTitle(String provider) {
        return COMMONS.equals(provider) ? "Wikimedia Commons" : NASA.equals(provider) ? "NASA"
                : MET.equals(provider) ? "The Metropolitan Museum of Art (CC0)" : provider;
    }

    static List<Feed> all(Context context) {
        List<Feed> out = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                context.getResources().openRawResource(R.raw.feeds), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("[|]", 4);
                if (p.length == 4) out.add(new Feed(p[0], p[1], p[2], p[3]));
            }
        } catch (Exception ignored) {
            // A missing catalog leaves the picker empty rather than breaking the screensaver.
        }
        return out;
    }

    /** The ticked feeds' ids (SharedPreferences "options", key sources). */
    static Set<String> selected(Context context) {
        return new HashSet<>(Options.preferences(context).getStringSet(Options.SOURCES, Collections.emptySet()));
    }

    static void select(Context context, Set<String> ids) {
        Options.preferences(context).edit().putStringSet(Options.SOURCES, new HashSet<>(ids)).apply();
    }
}
