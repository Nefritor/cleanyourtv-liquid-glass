package com.gathin.photodream;

import android.content.Context;

import com.gathin.widgets.Widgets;

/** Where the cards (com.gathin.widgets) may stand on the screensaver: a grid of cells, clear of the clock. */
final class SaverWidgets {
    private SaverWidgets() {}

    static Widgets.Surface surface(Context c) {
        Widgets.Surface s = new Widgets.Surface(7, 4, 48, 44,
                c.getResources().getFont(R.font.inter_light), c.getResources().getFont(R.font.inter_regular),
                c.getResources().getFont(R.font.inter_medium));
        s.reserved.add(new int[] {0, 0, 3, 1});   // the clock
        s.defaults.put("moon", new Widgets.Placement(5, 0, 2, 1));
        s.defaults.put("sun", new Widgets.Placement(5, 1, 2, 1));
        return s;
    }
}
