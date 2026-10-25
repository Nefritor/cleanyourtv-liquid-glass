package com.gathin.photodream;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Inter on every text of a settings page (the screensaver's own overlay sets it in its layout). */
final class Fonts {
    private Fonts() {}

    static void apply(Context context, View root) {
        Typeface inter = context.getResources().getFont(R.font.inter_regular);
        walk(root, inter);
    }

    private static void walk(View view, Typeface face) {
        if (view instanceof TextView) ((TextView) view).setTypeface(face);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) walk(group.getChildAt(i), face);
        }
    }
}
