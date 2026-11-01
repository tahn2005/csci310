package com.team23.trojanrideshare.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.CapacityStatus;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.testutil.Fixtures;
import com.team23.trojanrideshare.testutil.Results;

import org.junit.Test;

import java.util.Map;

public class RideServiceTest extends ServiceTestBase {

    private Ride form() {
        Ride r = new Ride();
        r.setTitle("LAX before Thanksgiving");
        r.setOrigin(Fixtures.USC);
        r.setDestination(Fixtures.LAX);
        r.setDeparture(NOW.plusDays(2));
        r.setMaxRiders(4);
        r.setExpectedCostCents(4500);
        return r;
    }

    @Test
    public void createSetsOrganizerAndInitialStatuses() {
        as("alice");
        Results<Ride> res = new Results<>();
        rideService.createRide(form(), res);
        Ride r = res.get();
        assertEquals("alice", r.getOrganizerId());
        assertEquals(1, r.riderCount());
        assertEquals(ActivityStatus.ON_TIME, r.getActivityStatus());
        assertEquals(CapacityStatus.OPEN, r.getCapacityStatus());
        assertEquals("Open – 1 of 4 riders", r.capacityLabel());
    }

    @Test
    public void validationRules() {
        Ride r = form();
        r.setTitle(" ");
        r.setDeparture(NOW.minusMinutes(1));
        r.setMaxRiders(7);
        r.setExpectedCostCents(0);
        r.setOrigin(new Place("typed", "typed text", "", Double.NaN, Double.NaN));
        Map<String, String> e = rideService.validate(r);
        assertTrue(e.containsKey(RideService.F_TITLE));
        assertTrue(e.containsKey(RideService.F_DEPARTURE));
        assertTrue(e.containsKey(RideService.F_MAX_RIDERS));
        assertTrue(e.containsKey(RideService.F_COST));
        assertTrue(e.containsKey(RideService.F_ORIGIN));
    }

    @Test
    public void originAndDestinationMustDiffer() {
        Ride r = form();
        r.setDestination(Fixtures.USC);
        assertTrue(rideService.validate(r).containsKey(RideService.F_DESTINATION));
    }

    @Test
    public void onlyOrganizerCanDelayOrCancel() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4, "bob"));
        as("bob");
        Results<Ride> denied = new Results<>();
        rideService.cancelRide(r.getRideId(), denied);
        assertEquals(ErrorCode.NOT_ALLOWED, denied.err().getCode());

        as("alice");
        Results<Ride> delayed = new Results<>();
        rideService.updateDeparture(r.getRideId(), NOW.plusDays(1).plusHours(2), ActivityStatus.DELAYED, delayed);
        assertEquals(ActivityStatus.DELAYED, db.ride(r.getRideId()).getActivityStatus());

        Results<Ride> cancelled = new Results<>();
        rideService.cancelRide(r.getRideId(), cancelled);
        assertEquals(ActivityStatus.CANCELLED, db.ride(r.getRideId()).getActivityStatus());
    }

    @Test
    public void newDepartureMustBeInFuture() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4));
        as("alice");
        Results<Ride> res = new Results<>();
        rideService.updateDeparture(r.getRideId(), NOW.minusHours(1), ActivityStatus.DELAYED, res);
        assertEquals(ErrorCode.VALIDATION, res.err().getCode());
    }
}
