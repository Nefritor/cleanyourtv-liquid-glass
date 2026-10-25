package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;

import com.gathin.widgets.WidgetSettings;

/**
 * The cards editor. It is translucent and started from Home itself, so the real Home shows beneath it (without its own
 * cards, which the editor draws) and the cards stand exactly where they will.
 */
public final class WidgetSettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(WidgetSettings.build(this, MainActivity.widgetSurface(this), null));
    }
}
