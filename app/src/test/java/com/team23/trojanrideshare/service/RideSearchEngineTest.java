package com.team23.trojanrideshare.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.SearchCriteria;
import com.team23.trojanrideshare.domain.SortOption;
import com.team23.trojanrideshare.domain.util.GeoUtils;
import com.team23.trojanrideshare.testutil.Fixtures;
import com.team23.trojanrideshare.testutil.Results;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Feature 3 (Ride Search) white-box tests for {@link RideSearchEngine}.
 * Written by Person 3. Each group below is one test case in the testing document:
 * WB 3.1 (radius filter on origin AND destination) and WB 3.2 (sort orders).
 *
 * <p>Straight-line distances between the fixture places (from {@link GeoUtils}):
 * USC→Leavey 0.29 mi, USC→Union Station 3.50 mi, USC→LAX 9.14 mi,
 * LAX→Santa Monica 6.92 mi, USC→Santa Monica 12.19 mi.
 */
public class RideSearchEngineTest extends ServiceTestBase {

    @Before
    public void searchAsAlice() {
        as("alice"); // every ride below is posted by Bob, so Alice may see all of them
    }

    /** Stores a 4-seat ride organized by Bob. */
    private Ride post(String title, Place from, Place to, LocalDateTime departure) {
        Ride r = Fixtures.ride("bob", from, to, departure, 4);
        r.setTitle(title);
        return db.put(r);
    }

    /** Criteria for "USC to LAX" with the given radius. */
    private static SearchCriteria uscToLax(double radius) {
        SearchCriteria c = new SearchCriteria();
        c.setOrigin(Fixtures.USC);
        c.setDestination(Fixtures.LAX);
        c.setRadiusMiles(radius);
        return c;
    }

    /** Reloads the engine's cache from the database, then returns the titles of the results in order. */
    private List<String> titles(SearchCriteria c) {
        Results<Void> loaded = new Results<>();
        search.refresh(loaded);
        loaded.get();
        List<String> out = new ArrayList<>();
        for (Ride r : search.search(c)) out.add(r.getTitle());
        return out;
    }

    // ---------------------------------------------------------------- WB 3.1: radius on both ends

    @Test
    public void radiusExcludesRideWhenOnlyOriginIsOutOfRange() {
        post("control: both ends near", Fixtures.USC_NEARBY, Fixtures.LAX, NOW.plusDays(1));
        // Destination matches exactly (0 mi) but origin is 12.19 mi from USC.
        post("origin far", Fixtures.SANTA_MONICA, Fixtures.LAX, NOW.plusDays(1));

        List<String> found = titles(uscToLax(5));

        assertEquals(Arrays.asList("control: both ends near"), found);
    }

    @Test
    public void radiusExcludesRideWhenOnlyDestinationIsOutOfRange() {
        post("control: both ends near", Fixtures.USC_NEARBY, Fixtures.LAX, NOW.plusDays(1));
        // Origin matches exactly (0 mi) but destination is 6.92 mi from LAX.
        post("destination far", Fixtures.USC, Fixtures.SANTA_MONICA, NOW.plusDays(1));

        List<String> found = titles(uscToLax(5));

        assertEquals(Arrays.asList("control: both ends near"), found);
    }

    @Test
    public void wideningRadiusBringsBackOnlyTheEndThatIsNowInRange() {
        post("origin 12.19 mi off", Fixtures.SANTA_MONICA, Fixtures.LAX, NOW.plusDays(1));
        post("destination 6.92 mi off", Fixtures.USC, Fixtures.SANTA_MONICA, NOW.plusDays(2));

        assertTrue("5 mi: both rides have one end out of range", titles(uscToLax(5)).isEmpty());
        assertEquals("7 mi: destination now in range, origin of the other ride still too far",
                Arrays.asList("destination 6.92 mi off"), titles(uscToLax(7)));
        assertEquals("13 mi: both rides in range",
                Arrays.asList("origin 12.19 mi off", "destination 6.92 mi off"), titles(uscToLax(13)));
    }

    @Test
    public void rideExactlyAtRadiusIsIncludedAndJustBeyondIsExcluded() {
        post("Union Station to LAX", Fixtures.UNION, Fixtures.LAX, NOW.plusDays(1));
        double d = GeoUtils.distanceMiles(Fixtures.UNION, Fixtures.USC); // ≈ 3.50 mi

        assertEquals("distance == radius counts as within", 1, titles(uscToLax(d)).size());
        assertTrue("0.01 mi short of the distance excludes it", titles(uscToLax(d - 0.01)).isEmpty());
    }

    @Test
    public void onlyTheEndpointsThatWereEnteredAreFiltered() {
        post("from Santa Monica", Fixtures.SANTA_MONICA, Fixtures.LAX, NOW.plusDays(1));
        SearchCriteria destinationOnly = new SearchCriteria();
        destinationOnly.setDestination(Fixtures.LAX);
        destinationOnly.setRadiusMiles(1);

        // No origin entered -> the far-away origin is not checked.
        assertEquals(1, titles(destinationOnly).size());

        SearchCriteria originOnly = new SearchCriteria();
        originOnly.setOrigin(Fixtures.USC);
        originOnly.setRadiusMiles(1);
        assertTrue(titles(originOnly).isEmpty());
    }

    @Test
    public void rideWithoutCoordinatesIsNeverWithinRadius() {
        Place unknown = new Place("x", "Somewhere", "", Double.NaN, Double.NaN);
        post("no coordinates", unknown, Fixtures.LAX, NOW.plusDays(1));

        assertTrue(titles(uscToLax(SearchCriteria.MAX_RADIUS)).isEmpty());
    }

    @Test
    public void searchAlsoHidesOwnCancelledDepartedAndFullRides() {
        post("eligible", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1));
        Ride mine = Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4);
        mine.setTitle("alice's own");
        db.put(mine);
        Ride cancelled = post("cancelled", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1));
        cancelled.setActivityStatus(ActivityStatus.CANCELLED);
        db.put(cancelled);
        post("departed", Fixtures.USC, Fixtures.LAX, NOW.minusMinutes(1));
        Ride full = Fixtures.ride("bob", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 2, "carol");
        full.setTitle("full");
        db.put(full);

        SearchCriteria seatsOnly = uscToLax(5);
        assertEquals(Arrays.asList("eligible"), titles(seatsOnly));

        SearchCriteria anySeats = uscToLax(5);
        anySeats.setSeatsOnly(false);
        assertEquals("unchecking 'Available seats only' shows the Full ride",
                Arrays.asList("eligible", "full"), titles(anySeats));
    }

    // ---------------------------------------------------------------- WB 3.2: sort orders

    /** Three rides whose time order differs from their distance order. */
    private void postThreeRides() {
        post("Union +1d", Fixtures.UNION, Fixtures.LAX, NOW.plusDays(1));      // 3.50 mi
        post("USC +3d", Fixtures.USC, Fixtures.LAX, NOW.plusDays(3));          // 0.00 mi
        post("Leavey +2d", Fixtures.USC_NEARBY, Fixtures.LAX, NOW.plusDays(2)); // 0.29 mi
    }

    @Test
    public void sortEarliestFirstOrdersByDepartureAscending() {
        postThreeRides();
        SearchCriteria c = uscToLax(10);
        c.setSort(SortOption.TIME_EARLIEST);

        assertEquals(Arrays.asList("Union +1d", "Leavey +2d", "USC +3d"), titles(c));
    }

    @Test
    public void sortLatestFirstOrdersByDepartureDescending() {
        postThreeRides();
        SearchCriteria c = uscToLax(10);
        c.setSort(SortOption.TIME_LATEST);

        assertEquals(Arrays.asList("USC +3d", "Leavey +2d", "Union +1d"), titles(c));
    }

    @Test
    public void sortProximityOrdersByCombinedDistance() {
        postThreeRides();
        SearchCriteria c = uscToLax(10);
        c.setSort(SortOption.PROXIMITY);

        assertEquals(Arrays.asList("USC +3d", "Leavey +2d", "Union +1d"), titles(c));
    }

    @Test
    public void proximityTiesAreBrokenByEarlierDeparture() {
        post("same route later", Fixtures.USC, Fixtures.LAX, NOW.plusDays(2));
        post("same route earlier", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1));
        SearchCriteria c = uscToLax(10);
        c.setSort(SortOption.PROXIMITY);

        assertEquals(Arrays.asList("same route earlier", "same route later"), titles(c));
    }

    @Test
    public void proximityThenTimePrefersDepartureClosestToTargetTime() {
        LocalDateTime day = NOW.plusDays(1).toLocalDate().atStartOfDay();
        post("09:00", Fixtures.USC, Fixtures.LAX, day.withHour(9));
        post("14:00", Fixtures.USC, Fixtures.LAX, day.withHour(14));
        // Same proximity (0.0 mi). Target 13:00 ± 6 h keeps both; 14:00 is 1 h away, 09:00 is 4 h away.
        SearchCriteria c = uscToLax(10);
        c.setTargetTime(LocalTime.of(13, 0));
        c.setWindowHours(6);
        c.setSort(SortOption.PROXIMITY_THEN_TIME);

        assertEquals(Arrays.asList("14:00", "09:00"), titles(c));
    }

    @Test
    public void proximityThenTimeStillPutsCloserRideFirst() {
        // Proximity is compared to 0.1 mi first, so the nearer ride wins even if its time is worse.
        LocalDateTime day = NOW.plusDays(1).toLocalDate().atStartOfDay();
        post("Union 13:00", Fixtures.UNION, Fixtures.LAX, day.withHour(13));
        post("USC 09:00", Fixtures.USC, Fixtures.LAX, day.withHour(9));
        SearchCriteria c = uscToLax(10);
        c.setTargetTime(LocalTime.of(13, 0));
        c.setWindowHours(6);
        c.setSort(SortOption.PROXIMITY_THEN_TIME);

        assertEquals(Arrays.asList("USC 09:00", "Union 13:00"), titles(c));
    }

    @Test
    public void proximitySortWithoutLocationFallsBackToEarliest() {
        postThreeRides();
        SearchCriteria c = new SearchCriteria(); // no origin or destination entered
        c.setSort(SortOption.PROXIMITY);

        assertEquals(Arrays.asList("Union +1d", "Leavey +2d", "USC +3d"), titles(c));
        assertFalse(c.hasLocation());
    }
}
