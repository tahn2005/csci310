package com.team23.trojanrideshare.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.SearchCriteria;
import com.team23.trojanrideshare.domain.SortOption;
import com.team23.trojanrideshare.testutil.Fixtures;
import com.team23.trojanrideshare.testutil.Results;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class RideSearchEngineTest extends ServiceTestBase {

    private Ride toLaxAt3, toLaxAt9, toUnion, mine, full, cancelled, past, fromNearby;

    @Before
    public void rides() {
        toLaxAt3 = db.put(Fixtures.ride("bob", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1).withHour(15), 4));
        toLaxAt9 = db.put(Fixtures.ride("carol", Fixtures.USC, Fixtures.LAX, NOW.plusDays(2).withHour(21), 4));
        toUnion = db.put(Fixtures.ride("bob", Fixtures.USC, Fixtures.UNION, NOW.plusDays(1).withHour(16), 4));
        fromNearby = db.put(Fixtures.ride("dave", Fixtures.USC_NEARBY, Fixtures.LAX, NOW.plusDays(3).withHour(14), 4));
        mine = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4));
        full = db.put(Fixtures.ride("bob", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1).withHour(18), 2, "carol"));
        Ride c = Fixtures.ride("bob", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4);
        c.setActivityStatus(ActivityStatus.CANCELLED);
        cancelled = db.put(c);
        past = db.put(Fixtures.ride("bob", Fixtures.USC, Fixtures.LAX, NOW.minusHours(1), 4));
        as("alice");
        search.refresh(new Results<>());
    }

    private List<String> ids(List<Ride> rides) {
        List<String> out = new ArrayList<>();
        for (Ride r : rides) out.add(r.getRideId());
        return out;
    }

    @Test
    public void listsOnlyEligibleRidesByEarliestDeparture() {
        List<String> got = ids(search.search(new SearchCriteria()));
        assertEquals(List.of(toLaxAt3.getRideId(), toUnion.getRideId(), toLaxAt9.getRideId(),
                fromNearby.getRideId()), got);
    }

    @Test
    public void fullRidesAppearWhenSeatsFilterIsOff() {
        SearchCriteria c = new SearchCriteria();
        c.setSeatsOnly(false);
        assertTrue(ids(search.search(c)).contains(full.getRideId()));
        assertTrue(!ids(search.search(c)).contains(mine.getRideId()));
        assertTrue(!ids(search.search(c)).contains(cancelled.getRideId()));
        assertTrue(!ids(search.search(c)).contains(past.getRideId()));
    }

    @Test
    public void radiusMatchesOriginAndDestination() {
        SearchCriteria c = new SearchCriteria();
        c.setOrigin(Fixtures.USC);
        c.setDestination(Fixtures.LAX);
        c.setRadiusMiles(0.5);
        List<String> got = ids(search.search(c));
        assertTrue(got.contains(toLaxAt3.getRideId()));
        assertTrue(got.contains(fromNearby.getRideId())); // Leavey is ~0.3 mi from USC Village
        assertTrue(!got.contains(toUnion.getRideId()));

        // FR-3.2.2: the radius can't go below 0.5 miles.
        c.setRadiusMiles(0.1);
        assertEquals(0.5, c.getRadiusMiles(), 0);

        c.setOrigin(Fixtures.UNION);
        assertTrue(search.search(c).isEmpty());
    }

    @Test
    public void dateAndTimeWindowFilters() {
        SearchCriteria c = new SearchCriteria();
        c.setDate(NOW.plusDays(1).toLocalDate());
        assertEquals(List.of(toLaxAt3.getRideId(), toUnion.getRideId()), ids(search.search(c)));

        c.setTargetTime(LocalTime.of(15, 0));
        c.setWindowHours(0.5);
        assertEquals(List.of(toLaxAt3.getRideId()), ids(search.search(c)));

        SearchCriteria anyDay = new SearchCriteria();
        anyDay.setTargetTime(LocalTime.of(15, 0));
        anyDay.setWindowHours(1);
        assertEquals(List.of(toLaxAt3.getRideId(), toUnion.getRideId(), fromNearby.getRideId()),
                ids(search.search(anyDay)));
    }

    @Test
    public void sortByLatestAndByProximity() {
        SearchCriteria c = new SearchCriteria();
        c.setSort(SortOption.TIME_LATEST);
        assertEquals(fromNearby.getRideId(), search.search(c).get(0).getRideId());

        c.setOrigin(Fixtures.USC_NEARBY);
        c.setRadiusMiles(50);
        c.setSort(SortOption.PROXIMITY);
        assertEquals(fromNearby.getRideId(), search.search(c).get(0).getRideId());
    }

    @Test
    public void proximityThenTimeBreaksTiesByTargetTime() {
        SearchCriteria c = new SearchCriteria();
        c.setOrigin(Fixtures.USC);
        c.setDestination(Fixtures.LAX);
        c.setRadiusMiles(1);
        c.setTargetTime(LocalTime.of(21, 0));
        c.setWindowHours(6);
        c.setSort(SortOption.PROXIMITY_THEN_TIME);
        List<Ride> got = search.search(c);
        assertEquals(toLaxAt9.getRideId(), got.get(0).getRideId());
    }

    @Test
    public void joinedRidesAreLabeled() {
        Ride joined = db.put(Fixtures.ride("bob", Fixtures.USC, Fixtures.LAX, NOW.plusDays(4), 4, "alice"));
        search.refresh(new Results<>());
        assertTrue(ids(search.search(new SearchCriteria())).contains(joined.getRideId()));
        assertTrue(search.isJoined(db.ride(joined.getRideId())));
    }
}
