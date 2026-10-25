package com.gathin.photodream;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

/**
 * The options, one switch each, for the remote's D-pad. Reached from the TV's screensaver settings (the
 * screensaver's own settings entry) and by holding OK or pressing Menu in the card's slideshow. All off: the plain slideshow.
 */
public class SettingsActivity extends Activity {

    private static final int ASK_LOCATION = 1;

    private SharedPreferences preferences;
    private Glass.Toggle weatherSwitch;
    private Glass.Toggle skySwitch;
    private TextView weatherStatus;
    private TextView locationButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = Options.preferences(this);
        Glass.Page page = new Glass.Page(this);
        page.add(Glass.title(this, getString(R.string.settings_title)));

        page.add(Glass.section(this, getString(R.string.section_screen)));
        page.add(toggle(Options.CLOCK, R.string.option_clock, null));
        page.add(toggle(Options.DATES, R.string.option_dates, null));
        page.add(toggle(Options.CREDIT, R.string.option_credit, null));
        page.add(toggle(Options.PRINTS, R.string.option_prints, on -> skySwitch.setAvailable(on)));
        skySwitch = toggle(Options.SKY, R.string.option_sky, null);
        skySwitch.setAvailable(preferences.getBoolean(Options.PRINTS, false));
        page.add(skySwitch);

        page.add(Glass.section(this, getString(R.string.section_weather)));
        weatherSwitch = toggle(Options.WEATHER, R.string.option_weather, this::weatherChanged);
        page.add(weatherSwitch);
        weatherStatus = Glass.note(this, "");
        page.add(weatherStatus);
        locationButton = Glass.button(this, getString(R.string.open_location_settings));
        locationButton.setOnClickListener(v -> openLocationSettings());
        page.add(locationButton);

        page.add(Glass.section(this, getString(R.string.section_more)));
        TextView widgets = Glass.button(this, getString(R.string.widgets_button));
        widgets.setOnClickListener(v -> startActivity(new Intent(this, WidgetsActivity.class)));
        page.add(widgets);
        TextView sources = Glass.button(this, getString(R.string.sources_button));
        sources.setOnClickListener(v -> startActivity(new Intent(this, SourcesActivity.class)));
        page.add(sources);
        TextView preview = Glass.button(this, getString(R.string.preview));
        preview.setOnClickListener(v -> startActivity(new Intent(this, SlideshowActivity.class)));
        page.add(preview);

        setContentView(page.root);
        page.column.getChildAt(2).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWeatherStatus();   // back from the TV's location settings
    }

    private Glass.Toggle toggle(String key, int label, Glass.Changed changed) {
        return Glass.toggle(this, getString(label), preferences.getBoolean(key, false), value -> {
            preferences.edit().putBoolean(key, value).apply();
            if (changed != null) changed.on(value);
        });
    }

    private void weatherChanged(boolean on) {
        if (on && !Weather.permitted(this)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, ASK_LOCATION);
        }
        updateWeatherStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != ASK_LOCATION) return;
        if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
            weatherSwitch.setChecked(false);   // no location, no weather
            preferences.edit().putBoolean(Options.WEATHER, false).apply();
        }
        updateWeatherStatus();
    }

    private void updateWeatherStatus() {
        boolean on = preferences.getBoolean(Options.WEATHER, false);
        boolean locationOff = on && Weather.permitted(this) && !Weather.locationOn(this);
        int status = !on ? R.string.weather_info
                : !Weather.permitted(this) ? R.string.weather_no_permission
                : locationOff ? R.string.weather_location_off
                : R.string.weather_ok;
        weatherStatus.setText(status);
        locationButton.setVisibility(locationOff ? View.VISIBLE : View.GONE);
    }

    private void openLocationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

}
