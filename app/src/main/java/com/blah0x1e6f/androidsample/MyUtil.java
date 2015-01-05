package com.blah0x1e6f.androidsample;

import android.graphics.Color;

public class MyUtil {
    protected static String formatFloat1(float num) { return String.format("%.1f", num); }

    protected static String formatFloat2(float num) { return String.format("%.2f", num); }

    protected static String formatFloat6(float num) { return String.format("%.6f", num); }

    protected static int getAccuracyColor(float accuracy) {
        if (accuracy <= 10)
            return Color.parseColor(MyColors.DEEP_GREEN);
        if (accuracy <= 25)
            return Color.parseColor(MyColors.DEEP_ORANGE);
        return Color.parseColor(MyColors.DEEP_RED);
    }

    /*
     * Returns value of the normal distribution (Gaussian) fn parameterized with mean=0 and stdev=1
     * (See http://en.wikipedia.org/wiki/Normal_distribution)
     */
    public static double stdNormDist(final double x) {
        double factor = 1 / Math.sqrt( 2 * Math.PI);
        double power = -(x * x / 2);
        double etothepower = Math.exp(power);
        return factor * etothepower;
    }
}
