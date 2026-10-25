package com.gathin.photodream;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.gathin.widgets.WidgetSettings;

import java.io.File;
import java.util.List;

/**
 * The cards page of the screensaver's settings: the shared editor on the screensaver's own look (a stored picture and
 * the header as the slideshow draws it), so the cards stand where they will stand.
 */
public class WidgetsActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(WidgetSettings.build(this, SaverWidgets.surface(this), background()));
    }

    private View background() {
        FrameLayout back = new FrameLayout(this);
        back.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF10131F, 0xFF152A38, 0xFF132B2A}));
        List<File> files = Online.files(this);
        if (!files.isEmpty()) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4;
            Bitmap bitmap = BitmapFactory.decodeFile(files.get(Math.abs((int) (System.currentTimeMillis() / 86400000L)) % files.size()).getPath(), o);
            if (bitmap != null) {
                ImageView picture = new ImageView(this);
                picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
                picture.setImageBitmap(bitmap);
                back.addView(picture, new FrameLayout.LayoutParams(-1, -1));
            }
        }
        View overlay = LayoutInflater.from(this).inflate(R.layout.overlay, back, false);
        for (int id : new int[] {R.id.caption, R.id.empty, R.id.weather}) {
            View v = overlay.findViewById(id);
            if (v != null) v.setVisibility(View.GONE);
        }
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        ((TextView) overlay.findViewById(R.id.greeting)).setText(hour >= 5 && hour < 12 ? R.string.greeting_morning
                : hour >= 12 && hour < 18 ? R.string.greeting_day
                : hour >= 18 && hour < 23 ? R.string.greeting_evening : R.string.greeting_night);
        back.addView(overlay);
        return back;
    }
}
