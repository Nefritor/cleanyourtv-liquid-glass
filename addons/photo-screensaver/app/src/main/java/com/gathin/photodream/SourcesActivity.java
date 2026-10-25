package com.gathin.photodream;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The picture sources, one tick each, grouped by provider, for the remote's D-pad. Ticking starts a download in
 * the background (4K pictures only, 400 MB at most); "Update now" runs it while this page is open.
 */
public class SourcesActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> selected = new HashSet<>();
    private TextView status;
    private TextView updateNow;
    private boolean syncing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selected.addAll(Feed.selected(this));
        Glass.Page page = new Glass.Page(this);
        page.add(Glass.title(this, getString(R.string.sources_title)));
        status = Glass.note(this, "");
        page.add(status);

        updateNow = Glass.button(this, getString(R.string.update_now));
        updateNow.setOnClickListener(v -> updateNow());
        page.add(updateNow);

        Map<String, List<Feed>> byProvider = new LinkedHashMap<>();
        for (Feed feed : Feed.all(this)) {
            List<Feed> group = byProvider.get(feed.provider);
            if (group == null) {
                group = new java.util.ArrayList<>();
                byProvider.put(feed.provider, group);
            }
            group.add(feed);
        }
        for (Map.Entry<String, List<Feed>> group : byProvider.entrySet()) {
            addGroup(page, group.getKey(), group.getValue());
        }

        setContentView(page.root);
        updateStatus(null);
        updateNow.requestFocus();
    }

    private void addGroup(Glass.Page page, String provider, List<Feed> feeds) {
        page.add(Glass.section(this, Feed.providerTitle(provider)));

        // Tick all / untick all share one row, so the long list below starts right after it.
        LinearLayout bulk = new LinearLayout(this);
        bulk.setOrientation(LinearLayout.HORIZONTAL);
        TextView all = Glass.button(this, getString(R.string.select_all));
        TextView none = Glass.button(this, getString(R.string.select_none));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, Glass.dp(this, 46), 1f);
        half.topMargin = Glass.dp(this, 6);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, Glass.dp(this, 46), 1f);
        half2.topMargin = Glass.dp(this, 6);
        half2.leftMargin = Glass.dp(this, 10);
        bulk.addView(all, half);
        bulk.addView(none, half2);
        page.add(bulk);

        java.util.List<Glass.Toggle> boxes = new java.util.ArrayList<>();
        for (Feed feed : feeds) {
            Glass.Toggle box = Glass.toggle(this, feed.title, selected.contains(feed.id), on -> {
                if (on) selected.add(feed.id); else selected.remove(feed.id);
                save();
            });
            box.setLayoutParams(Glass.compactParams(this));
            boxes.add(box);
            page.add(box);
        }
        all.setOnClickListener(v -> bulkSet(feeds, boxes, true));
        none.setOnClickListener(v -> bulkSet(feeds, boxes, false));
    }

    private void bulkSet(List<Feed> feeds, List<Glass.Toggle> boxes, boolean on) {
        for (int i = 0; i < boxes.size(); i++) {
            boxes.get(i).setChecked(on);
            if (on) selected.add(feeds.get(i).id); else selected.remove(feeds.get(i).id);
        }
        save();
    }

    /** Saves the ticks and asks for a download soon; the schedule keeps it topped up afterwards. */
    private void save() {
        Feed.select(this, selected);
        SyncService.schedule(this);
        handler.removeCallbacks(requestSync);
        handler.postDelayed(requestSync, 4000);   // after the owner has finished ticking
        updateStatus(null);
    }

    private final Runnable requestSync = () -> SyncService.runSoon(getApplicationContext());

    private void updateNow() {
        if (syncing) return;
        syncing = true;
        updateNow.setEnabled(false);
        updateNow.setAlpha(0.45f);
        handler.removeCallbacks(requestSync);
        android.content.Context app = getApplicationContext();
        new Thread(() -> {
            int added = Online.sync(app, (files, bytes, note) -> handler.post(() -> updateStatus(note)));
            handler.post(() -> {
                syncing = false;
                updateNow.setEnabled(true);
                updateNow.setAlpha(1f);
                updateStatus(null);
                status.append("  +" + added);
            });
        }, "online-sync-ui").start();
    }

    private void updateStatus(String loading) {
        int files = Online.files(this).size();
        long mb = Online.bytes(this) / (1024 * 1024);
        String text = getString(R.string.sources_status, selected.size(), files, mb, Online.CAP_BYTES / (1024 * 1024));
        if (loading != null) text += "\n" + getString(R.string.downloading, loading);
        status.setText(text);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

}
