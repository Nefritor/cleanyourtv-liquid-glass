package com.gathin.photodream;

import android.content.Context;
import android.content.SharedPreferences;

/** The switches in SettingsActivity. All off by default: then it is the plain full-screen slideshow. */
final class Options {
    static final String CLOCK = "clock";
    static final String DATES = "photo_dates";
    static final String SKY = "sky";
    static final String PRINTS = "prints";
    static final String WEATHER = "weather";
    /** The picture's author and licence, small at the bottom right (online pictures). */
    static final String CREDIT = "credit";
    /** The ticked picture sources (a string set of Feed ids), see SourcesActivity. */
    static final String SOURCES = "sources";

    final boolean clock;
    final boolean dates;
    final boolean sky;
    final boolean prints;
    final boolean weather;
    final boolean credit;

    private Options(SharedPreferences p) {
        clock = p.getBoolean(CLOCK, false);
        dates = p.getBoolean(DATES, false);
        sky = p.getBoolean(SKY, false);
        prints = p.getBoolean(PRINTS, false);
        weather = p.getBoolean(WEATHER, false);
        credit = p.getBoolean(CREDIT, false);
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("options", Context.MODE_PRIVATE);
    }

    static Options load(Context context) {
        return new Options(preferences(context));
    }
}
