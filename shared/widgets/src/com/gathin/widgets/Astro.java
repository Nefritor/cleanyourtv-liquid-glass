package com.gathin.widgets;

import java.util.Calendar;
import java.util.TimeZone;

/** The sky, worked out on the TV with no network: the moon's phase, and sunrise and sunset for a place. */
public final class Astro {
    private static final double SYNODIC_DAYS = 29.530588853;
    /** A new moon: 2000-01-06 18:14 UTC, in Julian days. */
    private static final double NEW_MOON_JD = 2451550.26;

    private Astro() {}

    public static double julianDay(long millis) {
        return millis / 86_400_000.0 + 2440587.5;
    }

    /** 0 (new) to 1 (the next new moon), the fraction of the lunar month that has passed. */
    public static double moonAge(long millis) {
        double age = ((julianDay(millis) - NEW_MOON_JD) / SYNODIC_DAYS) % 1.0;
        return age < 0 ? age + 1 : age;
    }

    /** The lit part of the disc, 0 to 1. */
    public static double illumination(double age) {
        return (1 - Math.cos(2 * Math.PI * age)) / 2;
    }

    /** Index 0..7: new, waxing crescent, first quarter, waxing gibbous, full, waning gibbous, last quarter, waning crescent. */
    public static int phaseIndex(double age) {
        return (int) Math.floor(age * 8 + 0.5) % 8;
    }

    /**
     * {sunrise, sunset} as millis for the local calendar day of {@code now}, or null for a polar day or night. The usual
     * sunrise equation (as in the NOAA worksheet), good to a minute or two.
     */
    public static long[] sunTimes(double latitude, double longitude, long now, TimeZone zone) {
        Calendar day = Calendar.getInstance(zone);
        day.setTimeInMillis(now);
        day.set(Calendar.HOUR_OF_DAY, 12);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        double jd = julianDay(day.getTimeInMillis());
        double n = Math.ceil(jd - 2451545.0 + 0.0008);
        double meanNoon = n - longitude / 360.0;
        double m = Math.toRadians((357.5291 + 0.98560028 * meanNoon) % 360);
        double c = 1.9148 * Math.sin(m) + 0.02 * Math.sin(2 * m) + 0.0003 * Math.sin(3 * m);
        double lambda = Math.toRadians((Math.toDegrees(m) + c + 180 + 102.9372) % 360);
        double transit = 2451545.0 + meanNoon + 0.0053 * Math.sin(m) - 0.0069 * Math.sin(2 * lambda);
        double decl = Math.asin(Math.sin(lambda) * Math.sin(Math.toRadians(23.44)));
        double lat = Math.toRadians(latitude);
        double cosH = (Math.sin(Math.toRadians(-0.833)) - Math.sin(lat) * Math.sin(decl)) / (Math.cos(lat) * Math.cos(decl));
        if (cosH < -1 || cosH > 1) return null;
        double h = Math.toDegrees(Math.acos(cosH)) / 360.0;
        long rise = Math.round((transit - h - 2440587.5) * 86_400_000.0);
        long set = Math.round((transit + h - 2440587.5) * 86_400_000.0);
        return new long[] {rise, set};
    }
}
