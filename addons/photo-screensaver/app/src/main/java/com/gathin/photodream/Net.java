package com.gathin.photodream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Plain HTTPS GETs with a user agent (Wikimedia and NASA ask for one) and a size limit. */
final class Net {
    static final String AGENT = "CleanYourTV-PhotoScreensaver/1.0 (https://github.com/Gathin23/cleanyourtv)";
    private static final int TIMEOUT_MS = 30_000;

    private Net() {}

    private static HttpURLConnection open(String url, String method) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("not https: " + url);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", AGENT);
        c.setRequestProperty("Accept-Encoding", "identity");
        return c;
    }

    /** A text response up to 4 MB. */
    static String text(String url) throws IOException {
        HttpURLConnection c = open(url, "GET");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode() + " " + url);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 4 * 1024 * 1024) throw new IOException("response too large");
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    /** The Content-Length a HEAD answers, or -1 when it does not say. */
    static long length(String url) {
        try {
            HttpURLConnection c = open(url, "HEAD");
            try {
                return c.getResponseCode() == 200 ? c.getContentLengthLong() : -1;
            } finally {
                c.disconnect();
            }
        } catch (IOException e) {
            return -1;
        }
    }

    /** Saves the response to file, giving up (and deleting it) past maxBytes. */
    static void download(String url, File file, long maxBytes) throws IOException {
        HttpURLConnection c = open(url, "GET");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode() + " " + url);
            long declared = c.getContentLengthLong();
            if (declared > maxBytes) throw new IOException("too large: " + declared);
            long total = 0;
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(file)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > maxBytes) throw new IOException("too large");
                    out.write(buf, 0, n);
                }
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            throw e;
        } finally {
            c.disconnect();
        }
    }
}
