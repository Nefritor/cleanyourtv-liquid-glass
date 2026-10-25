package com.gathin.photodream;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The pieces of the screensaver's settings pages, in the launcher's glass style: a dark backdrop, light glass rows. */
final class Glass {
    private Glass() {}

    interface Changed {
        void on(boolean value);
    }

    /** A page: the backdrop, and a centred column of rows inside a scroll view. */
    static final class Page {
        final FrameLayout root;
        final LinearLayout column;
        final ScrollView scroll;

        Page(Context c) {
            float d = c.getResources().getDisplayMetrics().density;
            root = new FrameLayout(c);
            GradientDrawable back = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[] {0xFF10131F, 0xFF152A38, 0xFF132B2A});
            root.setBackground(back);
            scroll = new ScrollView(c);
            scroll.setClipToPadding(false);
            scroll.setVerticalScrollBarEnabled(false);
            column = new LinearLayout(c);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setPadding(0, Math.round(36 * d), 0, Math.round(48 * d));
            scroll.addView(column, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(760 * d), ViewGroup.LayoutParams.MATCH_PARENT);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            root.addView(scroll, lp);
        }

        void add(View v) {
            column.addView(v);
        }
    }

    static TextView title(Context c, CharSequence text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        v.setTextColor(0xFFFFFFFF);
        v.setTypeface(font(c, R.font.inter_light));
        v.setLetterSpacing(-0.02f);
        v.setPadding(0, 0, 0, dp(c, 10));
        return v;
    }

    /** A line of quiet text under a title or between rows. */
    static TextView note(Context c, CharSequence text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        v.setTextColor(0x99FFFFFF);
        v.setTypeface(font(c, R.font.inter_regular));
        v.setLineSpacing(dp(c, 3), 1f);
        v.setPadding(dp(c, 6), dp(c, 4), dp(c, 6), dp(c, 8));
        return v;
    }

    /** A heading above a group of rows. */
    static TextView section(Context c, CharSequence text) {
        TextView v = new TextView(c);
        v.setText(text.toString().toUpperCase());
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setLetterSpacing(0.14f);
        v.setTextColor(0x99FFFFFF);
        v.setTypeface(font(c, R.font.inter_medium));
        v.setPadding(dp(c, 6), dp(c, 28), 0, dp(c, 2));
        return v;
    }

    /** A row that does something when OK is pressed. */
    static TextView button(Context c, CharSequence text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        v.setTextColor(0xFFFFFFFF);
        v.setTypeface(font(c, R.font.inter_regular));
        v.setSingleLine(true);
        v.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        v.setFocusable(true);
        v.setClickable(true);
        v.setBackground(rowBackground(c));
        v.setLayoutParams(rowParams(c));
        return v;
    }

    /** A row with a switch on its right. */
    static Toggle toggle(Context c, CharSequence text, boolean checked, Changed changed) {
        return new Toggle(c, text, checked, changed);
    }

    static LinearLayout.LayoutParams rowParams(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 52));
        lp.topMargin = dp(c, 8);
        return lp;
    }

    static LinearLayout.LayoutParams compactParams(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 46));
        lp.topMargin = dp(c, 6);
        return lp;
    }

    static StateListDrawable rowBackground(Context c) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, shape(c, 0x40FFFFFF, 0xF2FFFFFF, 2));
        states.addState(new int[] {}, shape(c, 0x1AFFFFFF, 0, 0));
        return states;
    }

    private static GradientDrawable shape(Context c, int fill, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, 16));
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), stroke);
        return g;
    }

    static Typeface font(Context c, int id) {
        return c.getResources().getFont(id);
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** The row with a switch; OK flips it. */
    static final class Toggle extends LinearLayout {
        private final Knob knob;
        private final Changed changed;
        private final TextView label;
        private boolean checked;

        Toggle(Context c, CharSequence text, boolean checked, Changed changed) {
            super(c);
            this.changed = changed;
            this.checked = checked;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(c, 18), 0, dp(c, 18), 0);
            setFocusable(true);
            setClickable(true);
            setBackground(rowBackground(c));
            setLayoutParams(rowParams(c));
            label = new TextView(c);
            label.setText(text);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            label.setTextColor(0xFFFFFFFF);
            label.setTypeface(font(c, R.font.inter_regular));
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(label, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            knob = new Knob(c);
            LayoutParams kp = new LayoutParams(dp(c, 50), dp(c, 28));
            kp.leftMargin = dp(c, 14);
            addView(knob, kp);
            knob.on = checked;
            setOnClickListener(v -> setChecked(!this.checked, true));
        }

        boolean isChecked() {
            return checked;
        }

        void setChecked(boolean value) {
            setChecked(value, false);
        }

        private void setChecked(boolean value, boolean notify) {
            if (checked == value) return;
            checked = value;
            knob.on = value;
            knob.invalidate();
            if (notify && changed != null) changed.on(value);
        }

        /** A switch that is off for now: dim, and the D-pad passes over it. */
        void setAvailable(boolean available) {
            setEnabled(available);
            setFocusable(available);
            setAlpha(available ? 1f : 0.45f);
        }

        void setLabel(CharSequence text) {
            label.setText(text);
        }
    }

    private static final class Knob extends View {
        boolean on;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        Knob(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            rect.set(0, 0, w, h);
            paint.setColor(on ? 0xFF70D9A0 : 0x40FFFFFF);
            canvas.drawRoundRect(rect, h / 2, h / 2, paint);
            paint.setColor(0xFFFFFFFF);
            float r = h / 2 - dp(getContext(), 3);
            float cx = on ? w - h / 2 : h / 2;
            canvas.drawCircle(cx, h / 2, r, paint);
        }
    }
}
