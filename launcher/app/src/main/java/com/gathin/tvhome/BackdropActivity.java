package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The background picker: Home's aurora, the focused app's pictures, the theme's wallpaper, a night sky and a few
 * colour schemes of the aurora. One tile each with a still preview; OK picks it and Home rebuilds with it.
 */
public final class BackdropActivity extends Activity {
    private static final class Style {
        final String id, ru, en;

        Style(String id, String ru, String en) {
            this.id = id;
            this.ru = ru;
            this.en = en;
        }
    }

    private SharedPreferences choices;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        final float d = getResources().getDisplayMetrics().density;
        boolean ru = Locale.getDefault().getLanguage().equals("ru");
        List<Style> styles = new ArrayList<>();
        styles.add(new Style("aurora", "Аврора", "Aurora"));
        styles.add(new Style("appart", "Карточки приложений", "App pictures"));
        Config config = Config.load(this);
        if (config.wallpaperEnabled && !config.wallpapers.isEmpty()) styles.add(new Style("wallpaper", "Обои темы", "Theme wallpaper"));
        styles.add(new Style("stars", "Звёздное небо", "Night sky"));
        styles.add(new Style("sunset", "Закат", "Sunset"));
        styles.add(new Style("ocean", "Океан", "Ocean"));
        styles.add(new Style("forest", "Лес", "Forest"));
        styles.add(new Style("violet", "Фиолет", "Violet"));
        styles.add(new Style("ember", "Угли", "Embers"));
        styles.add(new Style("graphite", "Графит", "Graphite"));
        String current = Config.backdrop(this);
        if (current == null) current = Config.appArtEnabled(this) ? "appart" : "aurora";

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundResource(R.drawable.settings_bg);
        scroll.setClipToPadding(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Math.round(96 * d), Math.round(40 * d), Math.round(96 * d), Math.round(48 * d));
        scroll.addView(page, new ViewGroup.LayoutParams(-1, -2));
        TextView title = new TextView(this);
        title.setText(ru ? "Фон главного экрана" : "Home background");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 38);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(getResources().getFont(R.font.inter_light));
        title.setLetterSpacing(-0.02f);
        page.addView(title);

        final int columns = 4;
        final int gap = Math.round(16 * d);
        int tileW = Math.round((960 - 2 * 96 - (columns - 1) * 16) / (float) columns * d);
        int tileH = Math.round(tileW * 0.62f);
        LinearLayout row = null;
        View first = null, selected = null;
        for (int i = 0; i < styles.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.topMargin = Math.round(i == 0 ? 26 * d : 18 * d);
                page.addView(row, rl);
            }
            final Style style = styles.get(i);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(tileW, -2);
            if (i % columns != 0) cl.leftMargin = gap;
            row.addView(cell, cl);

            FrameLayout tile = new FrameLayout(this);
            tile.setFocusable(true);
            tile.setClickable(true);
            tile.setClipToOutline(true);
            final float radius = 20 * d;
            tile.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                }
            });
            tile.addView(preview(style.id, d), new FrameLayout.LayoutParams(-1, -1));
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(radius);
            ring.setStroke(Math.round(3 * d), 0xFFFFFFFF);
            GradientDrawable chosen = new GradientDrawable();
            chosen.setCornerRadius(radius);
            chosen.setStroke(Math.round(2 * d), 0xB370D9A0);
            StateListDrawable frame = new StateListDrawable();
            frame.addState(new int[] {android.R.attr.state_focused}, ring);
            frame.addState(new int[] {}, style.id.equals(current) ? chosen : new GradientDrawable());
            tile.setForeground(frame);
            cell.addView(tile, new LinearLayout.LayoutParams(tileW, tileH));

            TextView name = new TextView(this);
            name.setText((ru ? style.ru : style.en) + (style.id.equals(current) ? "  ✓" : ""));
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            name.setTextColor(style.id.equals(current) ? 0xFF70D9A0 : 0xF2FFFFFF);
            name.setTypeface(getResources().getFont(R.font.inter_regular));
            name.setSingleLine(true);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
            nl.topMargin = Math.round(8 * d);
            nl.leftMargin = Math.round(4 * d);
            cell.addView(name, nl);

            tile.setOnClickListener(v -> choose(style.id));
            if (first == null) first = tile;
            if (style.id.equals(current)) selected = tile;
        }
        setContentView(scroll);
        (selected != null ? selected : first).requestFocus();
    }

    /** A still picture of what the style looks like. */
    private View preview(String id, float d) {
        Context c = this;
        int[] scheme = AuroraView.scheme(id);
        FrameLayout box = new FrameLayout(c);
        box.setBackgroundColor(0xFF05060A);
        if (scheme != null) {
            box.addView(AuroraView.preview(c, scheme), new FrameLayout.LayoutParams(-1, -1));
        } else if ("aurora".equals(id) || "appart".equals(id)) {
            box.addView(AuroraView.preview(c, AuroraView.themeColors(c)), new FrameLayout.LayoutParams(-1, -1));
            if ("appart".equals(id)) {
                // The app's picture and the dock tiles, in small.
                GradientDrawable art = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0x66FFFFFF, 0x1AFFFFFF});
                art.setCornerRadius(10 * d);
                View card = new View(c);
                card.setBackground(art);
                FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(Math.round(70 * d), Math.round(44 * d));
                al.gravity = Gravity.TOP | Gravity.END;
                al.setMargins(0, Math.round(10 * d), Math.round(10 * d), 0);
                box.addView(card, al);
            }
        } else if ("stars".equals(id)) {
            box.addView(new StarsView(c, true), new FrameLayout.LayoutParams(-1, -1));
        } else if ("wallpaper".equals(id)) {
            Config config = Config.load(c);
            if (!config.wallpapers.isEmpty()) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 8;
                Bitmap b = BitmapFactory.decodeFile(config.wallpapers.get(0).getPath(), o);
                if (b != null) {
                    ImageView image = new ImageView(c);
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    image.setImageBitmap(b);
                    box.addView(image, new FrameLayout.LayoutParams(-1, -1));
                }
            }
        }
        // A dock band at the bottom, so every tile reads as a Home screen.
        GradientDrawable band = new GradientDrawable();
        band.setColor(0x33FFFFFF);
        band.setCornerRadius(8 * d);
        View dock = new View(c);
        dock.setBackground(band);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(-1, Math.round(16 * d));
        dl.gravity = Gravity.BOTTOM;
        dl.setMargins(Math.round(12 * d), 0, Math.round(12 * d), Math.round(10 * d));
        box.addView(dock, dl);
        return box;
    }

    private void choose(String id) {
        choices.edit()
                .putString(Config.BACKDROP_KEY, id)
                .putBoolean("app_art", "appart".equals(id))
                .putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1)
                .apply();
        setResult(RESULT_OK);
        finish();
    }
}
