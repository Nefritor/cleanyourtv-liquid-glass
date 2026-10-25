package com.gathin.widgets;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure grid arithmetic for the editor: where a card can stand, and how the others make way when one is put on top of
 * them. An arrangement is a map of card id to place; a card that is off is simply not in it.
 */
public final class Arrange {
    private Arrange() {}

    /** The cards as they are stored now. */
    public static Map<String, Widgets.Placement> current(Context c, Widgets.Surface s) {
        Map<String, Widgets.Placement> out = new HashMap<>();
        for (Widgets.Spec spec : Widgets.all()) {
            Widgets.Placement p = Widgets.placement(c, s, spec.id);
            if (p != null) out.put(spec.id, p);
        }
        return out;
    }

    public static Map<String, Widgets.Placement> copy(Map<String, Widgets.Placement> in) {
        Map<String, Widgets.Placement> out = new HashMap<>();
        for (Map.Entry<String, Widgets.Placement> e : in.entrySet()) out.put(e.getKey(), e.getValue().copy());
        return out;
    }

    /** Stores the arrangement: every card in it at its place, every other card off. */
    public static void commit(Context c, Widgets.Surface s, Map<String, Widgets.Placement> arrangement) {
        for (Widgets.Spec spec : Widgets.all()) {
            Widgets.setPlacementQuiet(c, spec.id, arrangement.get(spec.id));
        }
        if (s.onChange != null) s.onChange.run();
    }

    /** In the grid and clear of the reserved cells. */
    public static boolean inside(Widgets.Surface s, Widgets.Placement p) {
        if (p.col < 0 || p.row < 0 || p.col + p.w > s.cols || p.row + p.h > s.rows) return false;
        for (int[] r : s.reserved) {
            if (p.overlaps(new Widgets.Placement(r[0], r[1], r[2], r[3]))) return false;
        }
        return true;
    }

    /**
     * {@code base} with the card {@code id} put at {@code at}; the cards in its way are pushed on in the direction it
     * moved ({dc, dr}; {0, 0} lets each pick its nearest way out) and pushed cards push the next ones. Null when
     * somebody cannot make way, or {@code at} is not on the grid. {@code base} is not changed.
     */
    public static Map<String, Widgets.Placement> place(Widgets.Surface s, Map<String, Widgets.Placement> base, String id,
                                                       Widgets.Placement at, int dc, int dr) {
        return place(s, base, id, at, dc, dr, null);
    }

    /** As above; {@code home} is where the card in hand was picked up: a card in its way may swap into that place. */
    public static Map<String, Widgets.Placement> place(Widgets.Surface s, Map<String, Widgets.Placement> base, String id,
                                                       Widgets.Placement at, int dc, int dr, Widgets.Placement home) {
        return place(s, base, id, at, dc, dr, home, false);
    }

    /** swapFirst: a card in the way goes to {@code home} before it is pushed on (a clean swap). */
    public static Map<String, Widgets.Placement> place(Widgets.Surface s, Map<String, Widgets.Placement> base, String id,
                                                       Widgets.Placement at, int dc, int dr, Widgets.Placement home, boolean swapFirst) {
        if (!inside(s, at)) return null;
        Map<String, Widgets.Placement> arr = copy(base);
        arr.put(id, at.copy());
        Set<String> fixed = new HashSet<>();
        fixed.add(id);
        return push(s, arr, id, new int[] {dc, dr}, fixed, 0, home, swapFirst) ? arr : null;
    }

    private static boolean push(Widgets.Surface s, Map<String, Widgets.Placement> arr, String mover, int[] dir,
                                Set<String> fixed, int depth, Widgets.Placement home, boolean swapFirst) {
        if (depth > 10) return false;
        for (String other : new ArrayList<>(arr.keySet())) {
            if (other.equals(mover)) continue;
            Widgets.Placement m = arr.get(mover);
            Widgets.Placement o = arr.get(other);
            if (!o.overlaps(m)) continue;
            if (fixed.contains(other)) return false;
            boolean moved = false;
            if (swapFirst && home != null && depth == 0) moved = swapInto(s, arr, mover, other, home, fixed, depth);
            for (int[] d : moved ? new ArrayList<int[]>() : directions(dir)) {
                Widgets.Placement n = o.copy();
                int guard = 0;
                while (n.overlaps(m) && guard++ < 12) {
                    n.col += d[0];
                    n.row += d[1];
                }
                if (n.overlaps(m) || !inside(s, n)) continue;
                Map<String, Widgets.Placement> trial = copy(arr);
                trial.put(other, n);
                Set<String> nowFixed = new HashSet<>(fixed);
                nowFixed.add(other);
                if (push(s, trial, other, d, nowFixed, depth + 1, null, false)) {
                    arr.clear();
                    arr.putAll(trial);
                    moved = true;
                    break;
                }
            }
            if (!moved && home != null && depth == 0) moved = swapInto(s, arr, mover, other, home, fixed, depth);
            if (!moved) return false;
        }
        return true;
    }

    /** Moves the card {@code other} into the place the card in hand was picked up from; false when it does not fit there. */
    private static boolean swapInto(Widgets.Surface s, Map<String, Widgets.Placement> arr, String mover, String other,
                                    Widgets.Placement home, Set<String> fixed, int depth) {
        Widgets.Placement m = arr.get(mover), o = arr.get(other);
        Widgets.Placement[] spots = {
                new Widgets.Placement(home.col, home.row, o.w, o.h),
                new Widgets.Placement(home.col + home.w - o.w, home.row + home.h - o.h, o.w, o.h)};
        for (Widgets.Placement n : spots) {
            if (n.overlaps(m) || !inside(s, n)) continue;
            Map<String, Widgets.Placement> trial = copy(arr);
            trial.put(other, n);
            Set<String> nowFixed = new HashSet<>(fixed);
            nowFixed.add(other);
            if (push(s, trial, other, new int[] {0, 0}, nowFixed, depth + 1, null, false)) {
                arr.clear();
                arr.putAll(trial);
                return true;
            }
        }
        return false;
    }

    /** The way the card is pushed first, then the others (the opposite way last). */
    private static List<int[]> directions(int[] first) {
        List<int[]> out = new ArrayList<>();
        int[][] all = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        if (first[0] != 0 || first[1] != 0) out.add(first);
        for (int[] d : all) {
            if (d[0] == first[0] && d[1] == first[1]) continue;
            if (d[0] == -first[0] && d[1] == -first[1]) continue;
            out.add(d);
        }
        if (first[0] != 0 || first[1] != 0) out.add(new int[] {-first[0], -first[1]});
        return out;
    }

    /** The first place (scanning row by row) where a card of this size can be put, pushing others if need be. */
    public static Map<String, Widgets.Placement> add(Widgets.Surface s, Map<String, Widgets.Placement> base, String id, int w, int h) {
        for (int pass = 0; pass < 2; pass++) {
            for (int row = 0; row + h <= s.rows; row++) {
                for (int col = 0; col + w <= s.cols; col++) {
                    Widgets.Placement p = new Widgets.Placement(col, row, w, h);
                    if (pass == 0) {
                        boolean clear = inside(s, p);
                        for (Widgets.Placement o : base.values()) clear &= !o.overlaps(p);
                        if (clear) {
                            Map<String, Widgets.Placement> out = copy(base);
                            out.put(id, p);
                            return out;
                        }
                    } else {
                        Map<String, Widgets.Placement> out = place(s, base, id, p, 0, 0);
                        if (out != null) return out;
                    }
                }
            }
        }
        return null;
    }
}
