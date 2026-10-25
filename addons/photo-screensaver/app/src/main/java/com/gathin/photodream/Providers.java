package com.gathin.photodream;

import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Finding pictures: one request pattern per provider, each answering a few candidates that pass the cheap checks
 * (size and shape from the provider's own data where it has them). Online.sync downloads and checks the rest.
 */
final class Providers {
    /** A picture worth downloading: a file name stem, where to get it, and the line shown on screen. */
    static final class Candidate {
        final String id;
        final String url;
        final String credit;

        Candidate(String id, String url, String credit) {
            this.id = id;
            this.url = url;
            this.credit = credit;
        }
    }

    static final int MIN_WIDTH = 3840;
    private static final String COMMONS_API = "https://commons.wikimedia.org/w/api.php?format=json&";

    private Providers() {}

    static List<Candidate> fetch(Feed feed, Random random, int want) throws IOException {
        try {
            if (Feed.COMMONS.equals(feed.provider)) return commons(feed, random, want);
            if (Feed.NASA.equals(feed.provider)) return nasa(feed, random, want);
            if (Feed.MET.equals(feed.provider)) return met(feed, random, want);
        } catch (org.json.JSONException e) {
            throw new IOException("bad answer from " + feed.provider, e);
        }
        return new ArrayList<>();
    }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }

    /** Pictures of a Commons category (a featured-pictures set): at least 3840 wide, landscape, a 3840 px JPEG ready to fetch. */
    private static List<Candidate> commons(Feed feed, Random random, int want) throws IOException, org.json.JSONException {
        JSONObject members = new JSONObject(Net.text(COMMONS_API + "action=query&list=categorymembers&cmtype=file&cmlimit=500&cmtitle="
                + enc("Category:" + feed.query)));
        JSONArray list = members.getJSONObject("query").getJSONArray("categorymembers");
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) titles.add(list.getJSONObject(i).getString("title"));
        Collections.shuffle(titles, random);
        List<Candidate> out = new ArrayList<>();
        for (int from = 0; from < titles.size() && out.size() < want; from += 20) {
            StringBuilder joined = new StringBuilder();
            for (String t : titles.subList(from, Math.min(titles.size(), from + 20))) {
                if (joined.length() > 0) joined.append('|');
                joined.append(t);
            }
            JSONObject info = new JSONObject(Net.text(COMMONS_API + "action=query&prop=imageinfo&iiprop=url|size|mime|extmetadata"
                    + "&iiurlwidth=" + MIN_WIDTH + "&iiextmetadatafilter=LicenseShortName|Artist&titles=" + enc(joined.toString())));
            JSONObject pages = info.getJSONObject("query").getJSONObject("pages");
            for (Iterator<String> it = pages.keys(); it.hasNext(); ) {
                JSONObject page = pages.getJSONObject(it.next());
                JSONArray ii = page.optJSONArray("imageinfo");
                if (ii == null || ii.length() == 0) continue;
                JSONObject f = ii.getJSONObject(0);
                int w = f.optInt("width"), h = f.optInt("height");
                String thumb = f.optString("thumburl", "");
                if (!"image/jpeg".equals(f.optString("mime")) || w < MIN_WIDTH || h <= 0 || thumb.isEmpty()) continue;
                double ratio = (double) w / h;
                if (ratio < 1.3 || ratio > 2.4) continue;
                JSONObject meta = f.optJSONObject("extmetadata");
                String license = meta == null || meta.optJSONObject("LicenseShortName") == null ? ""
                        : meta.getJSONObject("LicenseShortName").optString("value", "");
                String artist = meta == null || meta.optJSONObject("Artist") == null ? ""
                        : plain(meta.getJSONObject("Artist").optString("value", ""));
                StringBuilder credit = new StringBuilder();
                if (!artist.isEmpty()) credit.append(artist).append("  ·  ");
                if (!license.isEmpty()) credit.append(license).append("  ·  ");
                credit.append("Wikimedia Commons");
                out.add(new Candidate("c" + page.optLong("pageid"), thumb, credit.toString()));
                if (out.size() >= want) break;
            }
        }
        return out;
    }

    /** NASA Image and Video Library: a random page of the search, the original JPEG where it is a sensible size. */
    private static List<Candidate> nasa(Feed feed, Random random, int want) throws IOException, org.json.JSONException {
        String base = "https://images-api.nasa.gov/search?media_type=image&page_size=100&q=" + enc(feed.query);
        JSONObject first = new JSONObject(Net.text(base)).getJSONObject("collection");
        int hits = first.getJSONObject("metadata").optInt("total_hits", 0);
        int pages = Math.max(1, Math.min(100, (hits + 99) / 100));
        int page = 1 + random.nextInt(pages);
        JSONObject chosen = page == 1 ? first : new JSONObject(Net.text(base + "&page=" + page)).getJSONObject("collection");
        JSONArray items = chosen.getJSONArray("items");
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) order.add(i);
        Collections.shuffle(order, random);
        List<Candidate> out = new ArrayList<>();
        int looked = 0;
        for (int i : order) {
            if (out.size() >= want || looked++ >= want * 6) break;
            JSONObject item = items.getJSONObject(i);
            JSONObject data = item.getJSONArray("data").getJSONObject(0);
            String nasaId = data.optString("nasa_id", "");
            if (nasaId.isEmpty() || !nasaId.matches("[A-Za-z0-9_.-]+")) continue;
            String manifest = item.optString("href", "");
            if (!manifest.startsWith("http")) continue;
            if (manifest.startsWith("http://")) manifest = "https://" + manifest.substring(7);
            String url = null;
            try {
                JSONArray assets = new JSONArray(Net.text(manifest));
                for (int k = 0; k < assets.length(); k++) {
                    String a = assets.getString(k);
                    if (a.endsWith("~orig.jpg") || a.endsWith("~orig.jpeg")) { url = a; break; }
                }
            } catch (IOException | org.json.JSONException e) {
                continue;
            }
            if (url == null) continue;
            if (url.startsWith("http://")) url = "https://" + url.substring(7);
            long size = Net.length(url);
            if (size < 1_200_000L || size > 35_000_000L) continue;   // too small to be 4K, or too heavy
            String title = data.optString("title", "").trim();
            String by = data.optString("photographer", data.optString("secondary_creator", "")).trim();
            StringBuilder credit = new StringBuilder();
            if (!title.isEmpty()) credit.append(title.length() > 70 ? title.substring(0, 70) + "…" : title).append("  ·  ");
            if (!by.isEmpty()) credit.append(by).append("  ·  ");
            credit.append("NASA");
            out.add(new Candidate("n" + nasaId, url, credit.toString()));
        }
        return out;
    }

    /**
     * The Met's Open Access collection (CC0): a search for public-domain pictures (query "words;departmentId"), then
     * each object's primary image, which is the original scan. Size and shape are checked after the download.
     */
    private static List<Candidate> met(Feed feed, Random random, int want) throws IOException, org.json.JSONException {
        String[] parts = feed.query.split(";", 2);
        String url = "https://collectionapi.metmuseum.org/public/collection/v1.1/search?hasImages=true&isPublicDomain=true&q="
                + enc(parts[0]) + (parts.length > 1 && !parts[1].isEmpty() ? "&departmentId=" + enc(parts[1]) : "");
        JSONArray ids = new JSONObject(metText(url)).optJSONArray("objectIDs");
        List<Candidate> out = new ArrayList<>();
        if (ids == null || ids.length() == 0) return out;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < ids.length(); i++) order.add(ids.getInt(i));
        Collections.shuffle(order, random);
        int looked = 0;
        for (int id : order) {
            if (out.size() >= want || looked++ >= want * 8) break;
            JSONObject o;
            try {
                o = new JSONObject(metText("https://collectionapi.metmuseum.org/public/collection/v1/objects/" + id));
            } catch (IOException | org.json.JSONException e) {
                continue;
            }
            String image = o.optString("primaryImage", "");
            if (!o.optBoolean("isPublicDomain") || !image.startsWith("https://")) continue;
            long size = Net.length(image);
            if (size < 1_500_000L || size > 40_000_000L) continue;
            String artist = o.optString("artistDisplayName", "").trim();
            String title = o.optString("title", "").trim();
            StringBuilder credit = new StringBuilder();
            if (!artist.isEmpty()) credit.append(artist).append("  ·  ");
            if (!title.isEmpty()) credit.append(title.length() > 60 ? title.substring(0, 60) + "…" : title).append("  ·  ");
            credit.append("The Met, CC0");
            out.add(new Candidate("m" + id, image, credit.toString()));
        }
        return out;
    }

    /** The Met's API answers 403 to bursts: space the requests out and try a refused one again after a pause. */
    private static String metText(String url) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                Thread.sleep(300);
                return Net.text(url);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            } catch (IOException e) {
                if (attempt >= 2 || !String.valueOf(e.getMessage()).startsWith("HTTP 403")) throw e;
                try {
                    Thread.sleep(4000L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted");
                }
            }
        }
    }

    private static String plain(String html) {
        String s = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().replaceAll("\\s+", " ").trim();
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
