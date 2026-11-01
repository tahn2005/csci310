package com.team23.trojanrideshare.domain.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.testutil.Fixtures;

import org.junit.Test;

public class GeoUtilsTest {

    @Test
    public void samePlaceIsZero() {
        assertEquals(0, GeoUtils.distanceMiles(Fixtures.USC, Fixtures.USC), 1e-9);
    }

    @Test
    public void uscToLaxIsAboutTenMiles() {
        double d = GeoUtils.distanceMiles(Fixtures.USC, Fixtures.LAX);
        assertTrue("was " + d, d > 9 && d < 11);
    }

    @Test
    public void isSymmetric() {
        assertEquals(GeoUtils.distanceMiles(Fixtures.LAX, Fixtures.UNION),
                GeoUtils.distanceMiles(Fixtures.UNION, Fixtures.LAX), 1e-9);
    }

    @Test
    public void missingCoordinatesAreInfinitelyFar() {
        Place unknown = new Place("x", "Somewhere", "", Double.NaN, Double.NaN);
        assertTrue(Double.isInfinite(GeoUtils.distanceMiles(unknown, Fixtures.USC)));
    }
}
