package com.team23.trojanrideshare.domain.util;

import com.team23.trojanrideshare.domain.Place;

/** Straight-line (great-circle) distance between two places, in miles (FR-3.2.3). */
public final class GeoUtils {

    private static final double EARTH_RADIUS_MILES = 3958.8;

    private GeoUtils() {
    }

    public static double distanceMiles(Place a, Place b) {
        if (a == null || b == null || !a.hasCoordinates() || !b.hasCoordinates()) {
            return Double.POSITIVE_INFINITY;
        }
        return distanceMiles(a.getLat(), a.getLng(), b.getLat(), b.getLng());
    }

    /** Haversine formula. */
    public static double distanceMiles(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_MILES * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}
