package com.blah0x1e6f.androidsample;

import android.location.Location;

/**
 * This is a simple class without its own looper; instead it latches
 * on the application's normal location generating logic and simply
 * changes the location provided.  It keeps state, so that the path keeps
 * progressing instead of dancing around the user's current location.
 */
class LocationSimulator {
    double dxTot = 0;
    double dyTot = 0;

    void randomizeLocation(Location loc) {
        loc.setAccuracy(1.0f); // So the service doesn't filter this location out

        // dy adjustment
        double dy = randBetween(-0.0001, 0.0005);
        dy = dy * -1;
        dyTot += dy;
        loc.setLatitude(loc.getLatitude() + dyTot);

        // dx adjustment
        double dx = randBetween(-0.0005, 0.001);
        dxTot += dx;
        loc.setLongitude(loc.getLongitude() + dxTot);
    }

    private static double randBetween(double a, double b) {
        return a + Math.random() * (b-a);
    }
}
