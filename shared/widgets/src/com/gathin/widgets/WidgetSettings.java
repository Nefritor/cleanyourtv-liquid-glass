package com.gathin.widgets;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;

/**
 * The cards editor, on the real screen. The cards stand where they really are (the screen under it is Home itself, or
 * the screensaver's picture), the free cells show as a faint grid, and:
 *  - "+" opens a strip of every card with a preview and a line on what it shows; one opens its page (size, options,
 *    "Add"); "Add" puts it on the screen, already picked up;
 *  - on the screen OK picks a card up, the arrows carry it (others step aside when they can, otherwise it shakes)
 *    and OK sets it down; a long OK opens the card's page.
 * Built in code so both apps show the same editor in their own fonts.
 */
public final class WidgetSettings {
    private WidgetSettings() {}

    private enum Mode { TOOLBAR, BOARD, CARRY, PICKER, PAGE }

    /** The free cells of the grid, each fading out when a card comes over it and back when it leaves. */
    private static final class Ghost extends View {
        private final Widgets.Surface surface;
        private final float d;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float[] alpha, from, to;
        private ValueAnimator anim;

        Ghost(Context c, Widgets.Surface surface) {
            super(c);
            this.surface = surface;
            d = c.getResources().getDisplayMetrics().density;
            alpha = new float[surface.cols * surface.rows];
            from = new float[alpha.length];
            to = new float[alpha.length];
            fill.setStyle(Paint.Style.FILL);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1.6f * d);
            stroke.setPathEffect(new DashPathEffect(new float[] {8 * d, 6 * d}, 0));
        }

        void show(Map<String, Widgets.Placement> arrangement, boolean animate) {
            for (int r = 0; r < surface.rows; r++) {
                for (int c = 0; c < surface.cols; c++) {
                    boolean covered = false;
                    Widgets.Placement cell = new Widgets.Placement(c, r, 1, 1);
                    for (int[] b : surface.reserved) covered |= cell.overlaps(new Widgets.Placement(b[0], b[1], b[2], b[3]));
                    for (Widgets.Placement p : arrangement.values()) covered |= cell.overlaps(p);
                    to[r * surface.cols + c] = covered ? 0f : 1f;
                }
            }
            if (anim != null) anim.cancel();
            System.arraycopy(alpha, 0, from, 0, alpha.length);
            if (!animate) {
                System.arraycopy(to, 0, alpha, 0, alpha.length);
                invalidate();
                return;
            }
            anim = ValueAnimator.ofFloat(0f, 1f).setDuration(230);
            anim.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                for (int i = 0; i < alpha.length; i++) alpha[i] = from[i] + (to[i] - from[i]) * t;
                invalidate();
            });
            anim.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            for (int r = 0; r < surface.rows; r++) {
                for (int c = 0; c < surface.cols; c++) {
                    float a = alpha[r * surface.cols + c];
                    if (a < 0.02f) continue;
                    float x0 = (surface.originX + c * (Widgets.CELL_W + Widgets.GAP)) * d;
                    float y0 = (surface.originY + r * (Widgets.CELL_H + Widgets.GAP)) * d;
                    rect.set(x0, y0, x0 + Widgets.CELL_W * d, y0 + Widgets.CELL_H * d);
                    fill.setColor(((int) (0x18 * a) << 24) | 0xFFFFFF);
                    stroke.setColor(((int) (0x66 * a) << 24) | 0xFFFFFF);
                    canvas.drawRoundRect(rect, 21 * d, 21 * d, fill);
                    canvas.drawRoundRect(rect, 21 * d, 21 * d, stroke);
                }
            }
        }
    }

    private static final class Root extends FrameLayout {
        Editor editor;

        Root(Context c) {
            super(c);
            setFocusable(true);
        }

        @Override public boolean dispatchKeyEvent(KeyEvent e) {
            if (editor != null && editor.key(e)) return true;
            return super.dispatchKeyEvent(e);
        }
    }

    /** background: what the cards stand on besides the grid (null when the screen beneath shows through). */
    public static View build(Context c, Widgets.Surface s, View background) {
        Root root = new Root(c);
        root.editor = new Editor(c, s, root, background);
        return root;
    }

    private static final class Editor {
        final Context c;
        final Widgets.Surface s;
        final Root root;
        final float d;
        final Widgets.Host host;
        final Ghost ghost;
        final View scrim;
        final LinearLayout toolbar;
        final TextView hint;
        final TextView plus;
        FrameLayout picker, page;
        TextView addButton;
        Mode mode = Mode.TOOLBAR;
        Map<String, Widgets.Placement> arrangement;
        Map<String, Widgets.Placement> origin;   // before the card in hand was picked up
        Map<String, Widgets.Placement> base;     // the others while one is in hand
        String sel;
        String hand;
        boolean handIsNew;
        Widgets.Placement at;
        boolean longFired;
        // The open page.
        String pageId;
        int pageW, pageH;
        boolean pageFromPicker;
        int pickerIndex;

        Editor(Context c, Widgets.Surface s, Root root, View background) {
            this.c = c;
            this.s = s;
            this.root = root;
            d = c.getResources().getDisplayMetrics().density;
            if (background != null) root.addView(background, new FrameLayout.LayoutParams(-1, -1));
            scrim = new View(c);
            scrim.setBackgroundColor(0x70000000);
            root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
            ghost = new Ghost(c, s);
            root.addView(ghost, new FrameLayout.LayoutParams(-1, -1));
            host = new Widgets.Host(c, s).withoutBlur();
            root.addView(host.view(), new FrameLayout.LayoutParams(-1, -1));
            arrangement = Arrange.current(c, s);

            View strip = new View(c);
            strip.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xE6000000, 0x00000000}));
            root.addView(strip, new FrameLayout.LayoutParams(-1, px(70)));
            toolbar = new LinearLayout(c);
            toolbar.setOrientation(LinearLayout.HORIZONTAL);
            toolbar.setGravity(Gravity.CENTER_VERTICAL);
            toolbar.setPadding(px(40), 0, px(40), 0);
            TextView title = new TextView(c);
            title.setText(Widgets.isRussian() ? "Виджеты" : "Widgets");
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            title.setTextColor(0xFFFFFFFF);
            title.setTypeface(s.light);
            title.setShadowLayer(8 * d, 0, 0, 0xAA000000);
            toolbar.addView(title);
            plus = button(Widgets.isRussian() ? "+  Добавить" : "+  Add");
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, px(40));
            pl.leftMargin = px(18);
            toolbar.addView(plus, pl);
            hint = new TextView(c);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            hint.setTextColor(0xCCFFFFFF);
            hint.setTypeface(s.regular);
            hint.setGravity(Gravity.CENTER);
            hint.setShadowLayer(6 * d, 0, 0, 0xCC000000);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, -2, 1f);
            hl.leftMargin = px(14);
            hl.rightMargin = px(14);
            toolbar.addView(hint, hl);
            TextView reset = button(Widgets.isRussian() ? "Сбросить" : "Reset");
            TextView done = button(Widgets.isRussian() ? "Готово" : "Done");
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, px(40));
            rl.leftMargin = px(10);
            toolbar.addView(reset, rl);
            toolbar.addView(done, rl);
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, px(40));
            tlp.topMargin = px(6);
            root.addView(toolbar, tlp);

            plus.setOnClickListener(v -> openPicker());
            reset.setOnClickListener(v -> {
                Widgets.reset(c, s);
                arrangement = Arrange.current(c, s);
                sel = null;
                refresh(true);
            });
            done.setOnClickListener(v -> {
                if (c instanceof Activity) ((Activity) c).finish();
            });
            root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    host.start();
                    refresh(false);
                    plus.requestFocus();
                }

                @Override public void onViewDetachedFromWindow(View v) { host.stop(); }
            });
            setMode(Mode.TOOLBAR);
        }

        int px(float dp) {
            return Math.round(dp * d);
        }

        TextView button(String text) {
            TextView v = new TextView(c);
            v.setText(text);
            v.setGravity(Gravity.CENTER);
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            v.setTextColor(0xFFFFFFFF);
            v.setTypeface(s.medium);
            v.setSingleLine(true);
            v.setPadding(px(18), 0, px(18), 0);
            v.setFocusable(true);
            v.setClickable(true);
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[] {android.R.attr.state_focused}, shape(0x59FFFFFF, 0xF2FFFFFF, 2, 20));
            states.addState(new int[] {}, shape(0xB3141A24, 0x33FFFFFF, 1, 20));
            v.setBackground(states);
            return v;
        }

        GradientDrawable shape(int fill, int stroke, int strokeDp, float radiusDp) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(fill);
            g.setCornerRadius(radiusDp * d);
            if (strokeDp > 0) g.setStroke(px(strokeDp), stroke);
            return g;
        }

        void refresh(boolean animate) {
            host.setDraft(arrangement);
            ghost.show(arrangement, animate);
        }

        void setMode(Mode m) {
            mode = m;
            boolean modal = m == Mode.PICKER || m == Mode.PAGE;
            scrim.setBackgroundColor(modal ? 0xA6000000 : 0x70000000);
            String ru1, en1;
            switch (m) {
                case CARRY:
                    ru1 = "◀ ▲ ▼ ▶ переносят виджет, OK ставит, Назад отменяет. Другие уступят место.";
                    en1 = "◀ ▲ ▼ ▶ carry the widget, OK sets it down, Back cancels. Others make way.";
                    break;
                case BOARD:
                    ru1 = "OK берёт виджет и переносит. Долгое OK открывает его настройки. ▲ к кнопкам.";
                    en1 = "OK picks a widget up. Long OK opens its settings. ▲ to the buttons.";
                    break;
                default:
                    ru1 = "«+» добавляет виджет. ▼ переходит к виджетам на экране.";
                    en1 = "\"+\" adds a widget. ▼ goes to the widgets on the screen.";
            }
            hint.setText(Widgets.isRussian() ? ru1 : en1);
            hint.setVisibility(modal ? View.INVISIBLE : View.VISIBLE);
        }

        // ---- keys ---------------------------------------------------------------------------------

        boolean key(KeyEvent e) {
            int code = e.getKeyCode();
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            boolean up = e.getAction() == KeyEvent.ACTION_UP;
            boolean arrow = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                    || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            switch (mode) {
                case TOOLBAR:
                    if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (down) enterBoard();
                        return true;
                    }
                    return false;
                case BOARD:
                    if (arrow) {
                        if (down) boardArrow(code);
                        return true;
                    }
                    if (ok) {
                        if (down) {
                            if (e.getRepeatCount() == 0) longFired = false;
                            else if (!longFired && sel != null) {
                                longFired = true;
                                openPage(sel, false);
                            }
                        } else if (up) {
                            if (!longFired && sel != null) pickUp(sel);
                            longFired = false;
                        }
                        return true;
                    }
                    return false;
                case CARRY:
                    if (arrow) {
                        if (down) carryArrow(code);
                        return true;
                    }
                    if (ok) {
                        if (up) drop();
                        return true;
                    }
                    if (code == KeyEvent.KEYCODE_BACK) {
                        if (up) cancelCarry();
                        return true;
                    }
                    return false;
                case PICKER:
                    if (code == KeyEvent.KEYCODE_BACK) {
                        if (up) closePicker();
                        return true;
                    }
                    return false;
                case PAGE:
                    if (code == KeyEvent.KEYCODE_BACK) {
                        if (up) closePage();
                        return true;
                    }
                    return false;
            }
            return false;
        }

        void enterBoard() {
            if (arrangement.isEmpty()) return;
            if (sel == null || !arrangement.containsKey(sel)) {
                Widgets.Placement best = null;
                for (Map.Entry<String, Widgets.Placement> en : arrangement.entrySet()) {
                    Widgets.Placement p = en.getValue();
                    if (best == null || p.row < best.row || (p.row == best.row && p.col < best.col)) {
                        best = p;
                        sel = en.getKey();
                    }
                }
            }
            setMode(Mode.BOARD);
            host.setHighlight(sel);
            root.requestFocus();
        }

        void boardArrow(int code) {
            Widgets.Placement from = arrangement.get(sel);
            if (from == null) return;
            float fx = from.col + from.w / 2f, fy = from.row + from.h / 2f;
            String best = null;
            float bestScore = Float.MAX_VALUE;
            for (Map.Entry<String, Widgets.Placement> en : arrangement.entrySet()) {
                if (en.getKey().equals(sel)) continue;
                Widgets.Placement p = en.getValue();
                float dx = p.col + p.w / 2f - fx, dy = p.row + p.h / 2f - fy;
                float along, across;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) { along = -dx; across = dy; }
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { along = dx; across = dy; }
                else if (code == KeyEvent.KEYCODE_DPAD_UP) { along = -dy; across = dx; }
                else { along = dy; across = dx; }
                if (along <= 0.01f) continue;
                float score = along + 2.5f * Math.abs(across);
                if (score < bestScore) {
                    bestScore = score;
                    best = en.getKey();
                }
            }
            if (best != null) {
                sel = best;
                host.setHighlight(sel);
            } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                host.setHighlight(null);
                setMode(Mode.TOOLBAR);
                plus.requestFocus();
            }
        }

        // ---- carrying -----------------------------------------------------------------------------

        void pickUp(String id) {
            origin = Arrange.copy(arrangement);
            base = Arrange.copy(arrangement);
            base.remove(id);
            hand = id;
            handIsNew = false;
            at = arrangement.get(id).copy();
            host.setLifted(id);
            host.setHighlight(id);
            setMode(Mode.CARRY);
        }

        void carryArrow(int code) {
            int dc = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
            int dr = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
            Widgets.Placement n = at.copy();
            n.col += dc;
            n.row += dr;
            if (!Arrange.inside(s, n)) return;   // the edge of the grid
            Widgets.Placement home = handIsNew ? null : origin.get(hand);
            Map<String, Widgets.Placement> res = Arrange.place(s, base, hand, n, dc, dr, home);
            if (res == null) {
                // Nobody gives way: take the place of the card in the way (it swaps into the place picked up from).
                for (Widgets.Placement o : base.values()) {
                    if (!o.overlaps(n)) continue;
                    Widgets.Placement aligned = new Widgets.Placement(o.col, o.row, n.w, n.h);
                    res = Arrange.place(s, base, hand, aligned, dc, dr, home, true);
                    if (res != null) {
                        n = aligned;
                        break;
                    }
                }
            }
            if (res == null) {
                host.shake(hand);
                return;
            }
            at = n;
            arrangement = res;
            refresh(true);
        }

        void drop() {
            Arrange.commit(c, s, arrangement);
            sel = hand;
            hand = null;
            host.setLifted(null);
            setMode(Mode.BOARD);
            host.setHighlight(sel);
            refresh(true);
        }

        void cancelCarry() {
            arrangement = Arrange.copy(origin);
            hand = null;
            host.setLifted(null);
            if (handIsNew) {
                sel = null;
                setMode(Mode.TOOLBAR);
                host.setHighlight(null);
                plus.requestFocus();
            } else {
                setMode(Mode.BOARD);
                host.setHighlight(sel);
            }
            refresh(true);
        }

        // ---- the strip of cards ---------------------------------------------------------------------

        View previewCard(String id, int w, int h) {
            Widgets.Backdrop kept = s.backdrop;
            s.backdrop = null;
            View v = Cards.build(c, s, id, new Widgets.Placement(0, 0, w, h));
            s.backdrop = kept;
            return v;
        }

        /** The card at its real size, scaled down to fit the box and centred in it. */
        FrameLayout previewBox(String id, int w, int h, int boxWdp, int boxHdp) {
            FrameLayout box = new FrameLayout(c);
            View card = previewCard(id, w, h);
            if (card == null) return box;
            Widgets.Placement p = new Widgets.Placement(0, 0, w, h);
            int wdp = Widgets.widthDp(p), hdp = Widgets.heightDp(p);
            float scale = Math.min(1f, Math.min(boxWdp / (float) wdp, boxHdp / (float) hdp));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(wdp), px(hdp));
            lp.leftMargin = Math.round((px(boxWdp) - px(wdp) * scale) / 2f);
            lp.topMargin = Math.round((px(boxHdp) - px(hdp) * scale) / 2f);
            card.setPivotX(0);
            card.setPivotY(0);
            card.setScaleX(scale);
            card.setScaleY(scale);
            box.addView(card, lp);
            return box;
        }

        void openPicker() {
            if (picker != null) root.removeView(picker);
            hand = null;
            picker = new FrameLayout(c);
            GradientDrawable sheet = new GradientDrawable();
            sheet.setColor(0xFF111720);
            sheet.setCornerRadii(new float[] {28 * d, 28 * d, 28 * d, 28 * d, 0, 0, 0, 0});
            sheet.setStroke(px(1), 0x26FFFFFF);
            picker.setBackground(sheet);
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-1, px(300));
            plp.gravity = Gravity.BOTTOM;
            root.addView(picker, plp);
            TextView head = new TextView(c);
            head.setText(Widgets.isRussian() ? "Добавить виджет" : "Add a widget");
            head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            head.setTextColor(0xFFFFFFFF);
            head.setTypeface(s.light);
            FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2);
            hl.leftMargin = px(40);
            hl.topMargin = px(16);
            picker.addView(head, hl);
            HorizontalScrollView scroll = new HorizontalScrollView(c);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setClipToPadding(false);
            scroll.setPadding(px(34), 0, px(34), 0);
            LinearLayout strip = new LinearLayout(c);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            scroll.addView(strip, new ViewGroup.LayoutParams(-2, -1));
            FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, px(240));
            sl.topMargin = px(54);
            picker.addView(scroll, sl);
            for (Widgets.Spec spec : Widgets.all()) {
                if (arrangement.containsKey(spec.id)) continue;   // only what is not on the screen yet
                LinearLayout tile = new LinearLayout(c);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setPadding(px(12), px(12), px(12), px(10));
                tile.setFocusable(true);
                tile.setClickable(true);
                StateListDrawable states = new StateListDrawable();
                states.addState(new int[] {android.R.attr.state_focused}, shape(0x33FFFFFF, 0xF2FFFFFF, 2, 20));
                states.addState(new int[] {}, shape(0x14FFFFFF, 0, 0, 20));
                tile.setBackground(states);
                int[] size = spec.sizes[0];
                tile.addView(previewBox(spec.id, size[0], size[1], 276, 120), new LinearLayout.LayoutParams(px(276), px(120)));
                TextView name = new TextView(c);
                name.setText(spec.title());
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                name.setTextColor(0xFFFFFFFF);
                name.setTypeface(s.medium);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(px(276), -2);
                nl.topMargin = px(10);
                tile.addView(name, nl);
                TextView desc = new TextView(c);
                desc.setText(Widgets.description(spec.id));
                desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                desc.setTextColor(0x99FFFFFF);
                desc.setTypeface(s.regular);
                desc.setMaxLines(3);
                desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(276), -2);
                dl.topMargin = px(3);
                tile.addView(desc, dl);
                final int index = strip.getChildCount();
                tile.setOnClickListener(v -> {
                    pickerIndex = index;
                    openPage(spec.id, true);
                });
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -1);
                tl.rightMargin = px(14);
                strip.addView(tile, tl);
            }
            if (strip.getChildCount() == 0) {
                TextView none = new TextView(c);
                none.setText(Widgets.isRussian() ? "Все виджеты уже на экране." : "Every widget is already on the screen.");
                none.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                none.setTextColor(0xCCFFFFFF);
                none.setTypeface(s.regular);
                none.setPadding(px(6), px(30), 0, 0);
                none.setFocusable(true);
                strip.addView(none);
            }
            setMode(Mode.PICKER);
            strip.getChildAt(0).requestFocus();
        }

        void closePicker() {
            if (picker != null) {
                root.removeView(picker);
                picker = null;
            }
            setMode(Mode.TOOLBAR);
            plus.requestFocus();
        }

        // ---- a card's page --------------------------------------------------------------------------

        void openPage(String id, boolean fromPicker) {
            pageId = id;
            pageFromPicker = fromPicker;
            Widgets.Spec spec = Widgets.spec(id);
            Widgets.Placement cur = arrangement.get(id);
            pageW = cur != null ? cur.w : spec.sizes[0][0];
            pageH = cur != null ? cur.h : spec.sizes[0][1];
            if (picker != null) picker.setVisibility(View.GONE);
            if (page != null) root.removeView(page);
            page = new FrameLayout(c);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(580), -2);
            lp.gravity = Gravity.CENTER;
            root.addView(page, lp);
            setMode(Mode.PAGE);
            buildPage();
        }

        void buildPage() {
            final Widgets.Spec spec = Widgets.spec(pageId);
            page.removeAllViews();
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(px(28), px(22), px(28), px(24));
            col.setBackground(shape(0xFF121821, 0x33FFFFFF, 1, 28));
            page.addView(col, new FrameLayout.LayoutParams(-1, -2));
            TextView head = new TextView(c);
            head.setText(spec.title());
            head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            head.setTextColor(0xFFFFFFFF);
            head.setTypeface(s.light);
            col.addView(head);
            TextView desc = new TextView(c);
            desc.setText(Widgets.description(pageId));
            desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            desc.setTextColor(0x99FFFFFF);
            desc.setTypeface(s.regular);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
            dl.topMargin = px(4);
            col.addView(desc, dl);
            final FrameLayout previewHolder = new FrameLayout(c);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, px(150));
            pl.topMargin = px(12);
            previewHolder.setBackground(shape(0x1AFFFFFF, 0, 0, 20));
            col.addView(previewHolder, pl);
            final Runnable preview = () -> {
                previewHolder.removeAllViews();
                previewHolder.addView(previewBox(pageId, pageW, pageH, 524, 138), new FrameLayout.LayoutParams(-1, -1));
            };
            preview.run();

            // The rows (size and the card's options) scroll when there are more than fit under the preview.
            final int rowCount = 1 + Cards.params(pageId).length;
            final LinearLayout rowsBox = new LinearLayout(c);
            rowsBox.setOrientation(LinearLayout.VERTICAL);
            final android.widget.ScrollView rowsScroll = new android.widget.ScrollView(c);
            rowsScroll.setVerticalScrollBarEnabled(false);
            rowsScroll.setClipToPadding(false);
            rowsScroll.addView(rowsBox, new ViewGroup.LayoutParams(-1, -2));
            LinearLayout.LayoutParams rsl = new LinearLayout.LayoutParams(-1, px(Math.min(rowCount, 3) * 56 - 8));
            rsl.topMargin = px(8);
            col.addView(rowsScroll, rsl);
            // Size.
            final TextView sizeRow = row();
            final Runnable sizeLabel = () -> sizeRow.setText((Widgets.isRussian() ? "Размер" : "Size") + "   ·   " + pageW + "×" + pageH);
            sizeLabel.run();
            final Runnable grow = () -> changeSize(spec, preview, sizeLabel, previewHolder);
            sizeRow.setOnClickListener(v -> grow.run());
            sizeRow.setOnKeyListener((v, code, e) -> {
                if (e.getAction() == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_LEFT)) {
                    grow.run();
                    return true;
                }
                return false;
            });
            rowsBox.addView(sizeRow, rowParams(0));
            // The card's own options.
            for (final Cards.Param param : Cards.params(pageId)) {
                final TextView pr = row();
                final String[] options = param.options();
                final Runnable label = () -> {
                    int v = Math.max(0, Math.min(options.length - 1, Widgets.param(c, pageId, param.key, Cards.paramDefault(pageId, param.key))));
                    pr.setText(param.label() + "   ·   " + options[v]);
                };
                label.run();
                final java.util.function.IntConsumer step = delta -> {
                    int v = Widgets.param(c, pageId, param.key, Cards.paramDefault(pageId, param.key));
                    Widgets.setParam(c, s, pageId, param.key, (v + delta + options.length) % options.length);
                    label.run();
                    preview.run();
                    host.rebuild();
                };
                pr.setOnClickListener(v -> step.accept(1));
                pr.setOnKeyListener((v, code, e) -> {
                    if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { step.accept(1); return true; }
                    if (code == KeyEvent.KEYCODE_DPAD_LEFT) { step.accept(-1); return true; }
                    return false;
                });
                rowsBox.addView(pr, rowParams(8));
            }
            // Buttons.
            LinearLayout buttons = new LinearLayout(c);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            boolean placed = arrangement.containsKey(pageId);
            addButton = button(placed ? (Widgets.isRussian() ? "Убрать с экрана" : "Remove") : (Widgets.isRussian() ? "Добавить" : "Add"));
            buttons.addView(addButton, new LinearLayout.LayoutParams(0, px(46), 1f));
            TextView back = button(Widgets.isRussian() ? "Назад" : "Back");
            LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, px(46), 1f);
            b2.leftMargin = px(10);
            buttons.addView(back, b2);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
            bl.topMargin = px(18);
            col.addView(buttons, bl);
            addButton.setOnClickListener(v -> {
                if (arrangement.containsKey(pageId)) removeCard(); else addCard();
            });
            back.setOnClickListener(v -> closePage());
            sizeRow.requestFocus();
        }

        TextView row() {
            TextView v = new TextView(c);
            v.setGravity(Gravity.CENTER_VERTICAL);
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            v.setTextColor(0xFFFFFFFF);
            v.setTypeface(s.regular);
            v.setSingleLine(true);
            v.setPadding(px(18), 0, px(18), 0);
            v.setFocusable(true);
            v.setClickable(true);
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[] {android.R.attr.state_focused}, shape(0x40FFFFFF, 0xF2FFFFFF, 2, 16));
            states.addState(new int[] {}, shape(0x1AFFFFFF, 0, 0, 16));
            v.setBackground(states);
            return v;
        }

        LinearLayout.LayoutParams rowParams(int topDp) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(48));
            lp.topMargin = px(topDp);
            return lp;
        }

        void changeSize(Widgets.Spec spec, Runnable preview, Runnable label, View shaker) {
            int at = 0;
            for (int i = 0; i < spec.sizes.length; i++) if (spec.sizes[i][0] == pageW && spec.sizes[i][1] == pageH) at = i;
            int[] next = spec.sizes[(at + 1) % spec.sizes.length];
            Widgets.Placement cur = arrangement.get(pageId);
            if (cur != null) {
                // A card on the screen grows in place (pulled back inside the grid when it would stick out), and
                // others make way if they can.
                Widgets.Placement n = new Widgets.Placement(Math.min(cur.col, s.cols - next[0]), Math.min(cur.row, s.rows - next[1]), next[0], next[1]);
                Map<String, Widgets.Placement> rest = Arrange.copy(arrangement);
                rest.remove(pageId);
                Map<String, Widgets.Placement> res = Arrange.place(s, rest, pageId, n, 0, 0);
                if (res == null) {
                    Widgets.Host.shakeView(shaker);
                    return;
                }
                arrangement = res;
                Arrange.commit(c, s, arrangement);
                refresh(true);
            }
            pageW = next[0];
            pageH = next[1];
            label.run();
            preview.run();
        }

        void addCard() {
            Map<String, Widgets.Placement> prior = Arrange.copy(arrangement);
            Map<String, Widgets.Placement> res = Arrange.add(s, prior, pageId, pageW, pageH);
            if (res == null) {
                Widgets.Host.shakeView(addButton);
                return;
            }
            String id = pageId;
            closeAll();
            arrangement = res;
            origin = prior;
            base = Arrange.copy(prior);
            hand = id;
            handIsNew = true;
            sel = id;
            at = res.get(id).copy();
            host.setLifted(id);
            host.setHighlight(id);
            setMode(Mode.CARRY);
            root.requestFocus();
            refresh(true);
        }

        void removeCard() {
            arrangement.remove(pageId);
            Arrange.commit(c, s, arrangement);
            if (sel != null && sel.equals(pageId)) sel = null;
            refresh(true);
            closePage();
        }

        void closePage() {
            if (page != null) {
                root.removeView(page);
                page = null;
            }
            if (pageFromPicker && picker != null) {
                picker.setVisibility(View.VISIBLE);
                setMode(Mode.PICKER);
                HorizontalScrollView sv = (HorizontalScrollView) picker.getChildAt(1);
                LinearLayout strip = (LinearLayout) sv.getChildAt(0);
                if (strip.getChildCount() > 0) strip.getChildAt(Math.min(pickerIndex, strip.getChildCount() - 1)).requestFocus();
            } else {
                closeAll();
                if (arrangement.isEmpty()) {
                    plus.requestFocus();
                } else {
                    sel = sel != null && arrangement.containsKey(sel) ? sel : null;
                    enterBoard();
                }
            }
        }

        void closeAll() {
            if (page != null) {
                root.removeView(page);
                page = null;
            }
            if (picker != null) {
                root.removeView(picker);
                picker = null;
            }
            setMode(Mode.TOOLBAR);
        }
    }
}
