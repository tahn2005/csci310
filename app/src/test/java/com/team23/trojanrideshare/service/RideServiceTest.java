package com.team23.trojanrideshare.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.testutil.Fixtures;
import com.team23.trojanrideshare.testutil.Results;

import org.junit.Test;

import java.time.LocalDateTime;

/**
 * Feature 2 (Ride Creation and Management) white-box tests for {@link RideService}.
 * Written by Luke Hanna (Person 2). Each group below is one test case in the testing document.
 */
public class RideServiceTest extends ServiceTestBase {

    /** A ride form that passes every check, so each test changes exactly one field. */
    private Ride validForm() {
        Ride r = new Ride();
        r.setTitle("LAX before Thanksgiving");
        r.setOrigin(Fixtures.USC);
        r.setDestination(Fixtures.LAX);
        r.setDeparture(NOW.plusDays(2));
        r.setMaxRiders(4);
        r.setExpectedCostCents(4000);
        return r;
    }

    /** The edit dialog pre-filled with the ride's current details (what the UI sends). */
    private static Ride editForm(Ride r) {
        Ride c = new Ride();
        c.setTitle(r.getTitle());
        c.setDeparture(r.getDeparture());
        c.setMaxRiders(r.getMaxRiders());
        c.setExpectedCostCents(r.getExpectedCostCents());
        c.setNote(r.getNote());
        return c;
    }

    private AppException createAndExpectError(Ride form) {
        Results<Ride> res = new Results<>();
        rideService.createRide(form, res);
        return res.err();
    }

    // ---------------------------------------------------------------- WB 2.1: $0.00 expected cost

    @Test
    public void createRejectsZeroExpectedCost() {
        as("alice");
        Ride form = validForm();
        form.setExpectedCostCents(0);

        AppException e = createAndExpectError(form);

        assertEquals(ErrorCode.VALIDATION, e.getCode());
        assertEquals("Expected cost must be greater than $0.", e.getFieldErrors().get(RideService.F_COST));
        assertEquals("only the cost field is wrong", 1, e.getFieldErrors().size());
        assertTrue("nothing is saved", db.rides.isEmpty());
    }

    @Test
    public void createRejectsNegativeCostButAcceptsOneCent() {
        as("alice");
        Ride negative = validForm();
        negative.setExpectedCostCents(-500);
        assertTrue(createAndExpectError(negative).getFieldErrors().containsKey(RideService.F_COST));

        // Boundary just above zero: $0.01 is the smallest valid cost.
        Ride oneCent = validForm();
        oneCent.setExpectedCostCents(1);
        Results<Ride> ok = new Results<>();
        rideService.createRide(oneCent, ok);
        assertEquals(1, ok.get().getExpectedCostCents());
        assertEquals(1, db.rides.size());
    }

    @Test
    public void createReportsEveryOtherInvalidFieldSeparately() {
        as("alice");
        Ride form = validForm();
        form.setTitle("   ");
        form.setOrigin(new com.team23.trojanrideshare.domain.Place("typed", "typed text", "", Double.NaN, Double.NaN));
        form.setDestination(null);
        form.setDeparture(NOW); // not in the future
        java.util.Map<String, String> e = createAndExpectError(form).getFieldErrors();
        assertEquals("Enter a title.", e.get(RideService.F_TITLE));
        assertEquals("Choose the origin from the location suggestions.", e.get(RideService.F_ORIGIN));
        assertEquals("Choose the destination from the location suggestions.", e.get(RideService.F_DESTINATION));
        assertEquals("The ride date and departure time must be in the future.", e.get(RideService.F_DEPARTURE));

        Ride longTitle = validForm();
        longTitle.setTitle(new String(new char[RideService.MAX_TITLE_LENGTH + 1]).replace('\0', 'x'));
        assertTrue(createAndExpectError(longTitle).getFieldErrors().containsKey(RideService.F_TITLE));

        Ride samePlace = validForm();
        samePlace.setDestination(Fixtures.USC);
        assertEquals("The origin and destination must be different places.",
                createAndExpectError(samePlace).getFieldErrors().get(RideService.F_DESTINATION));
        assertTrue(db.rides.isEmpty());
    }

    @Test
    public void createRequiresSignedInUser() {
        // No as(...): nobody is logged in.
        AppException e = createAndExpectError(validForm());
        assertEquals(ErrorCode.NOT_ALLOWED, e.getCode());
        assertTrue(db.rides.isEmpty());
    }

    // ---------------------------------------------------------------- WB 2.2: max riders 2–6

    @Test
    public void createRejectsMaxRidersBelowTwo() {
        as("alice");
        Ride form = validForm();
        form.setMaxRiders(1); // the organizer alone is not a shared ride

        AppException e = createAndExpectError(form);

        assertEquals(ErrorCode.VALIDATION, e.getCode());
        assertEquals("Maximum riders must be a whole number from 2 to 6, including you.",
                e.getFieldErrors().get(RideService.F_MAX_RIDERS));
        assertTrue(db.rides.isEmpty());
    }

    @Test
    public void createRejectsMaxRidersAboveSix() {
        as("alice");
        Ride form = validForm();
        form.setMaxRiders(7);

        AppException e = createAndExpectError(form);

        assertTrue(e.getFieldErrors().containsKey(RideService.F_MAX_RIDERS));
        assertTrue(db.rides.isEmpty());
    }

    @Test
    public void createAcceptsMaxRidersAtBothBoundaries() {
        as("alice");
        for (int max : new int[]{Ride.MIN_RIDERS, Ride.MAX_RIDERS}) {
            Ride form = validForm();
            form.setMaxRiders(max);
            Results<Ride> res = new Results<>();
            rideService.createRide(form, res);
            Ride r = res.get();
            assertEquals(max, r.getMaxRiders());
            assertEquals("the organizer counts as the first rider", 1, r.riderCount());
            assertEquals("Open – 1 of " + max + " riders", r.capacityLabel());
        }
        assertEquals(2, db.rides.size());
    }

    // ---------------------------------------------------------------- WB 2.3: Delayed + new time

    @Test
    public void editToDelayedWithNewTimeChangesDeparture() {
        LocalDateTime original = NOW.plusDays(1).withHour(12).withMinute(0);
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, original, 4, "bob"));
        as("alice");
        Ride changes = editForm(r);
        changes.setDeparture(original.plusHours(2).withSecond(45)); // seconds are dropped

        Results<Ride> res = new Results<>();
        rideService.editRide(r.getRideId(), changes, ActivityStatus.DELAYED, res);

        Ride saved = db.ride(r.getRideId());
        assertEquals(ActivityStatus.DELAYED, res.get().getActivityStatus());
        assertEquals(ActivityStatus.DELAYED, saved.getActivityStatus());
        assertEquals(original.plusHours(2), saved.getDeparture());
        // The route and riders stay as they were.
        assertEquals(Fixtures.USC.getPlaceId(), saved.getOrigin().getPlaceId());
        assertEquals(Fixtures.LAX.getPlaceId(), saved.getDestination().getPlaceId());
        assertEquals(2, saved.riderCount());
    }

    @Test
    public void editRejectsDelayedTimeInThePast() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4));
        as("alice");
        Ride changes = editForm(r);
        changes.setDeparture(NOW.minusMinutes(1));

        Results<Ride> res = new Results<>();
        rideService.editRide(r.getRideId(), changes, ActivityStatus.DELAYED, res);

        assertTrue(res.err().getFieldErrors().containsKey(RideService.F_DEPARTURE));
        assertEquals("ride is unchanged", ActivityStatus.ON_TIME, db.ride(r.getRideId()).getActivityStatus());
        assertEquals(NOW.plusDays(1), db.ride(r.getRideId()).getDeparture());
    }

    @Test
    public void editAcceptsOnlyOnTimeOrDelayedAsNewStatus() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4));
        as("alice");
        Results<Ride> res = new Results<>();
        rideService.editRide(r.getRideId(), editForm(r), ActivityStatus.COMPLETED, res);
        assertEquals("Choose On-Time or Delayed.", res.err().getMessage());
        assertEquals(ActivityStatus.ON_TIME, db.ride(r.getRideId()).getActivityStatus());

        // Delayed back to On-Time is allowed.
        Results<Ride> delayed = new Results<>();
        rideService.editRide(r.getRideId(), editForm(r), ActivityStatus.DELAYED, delayed);
        Results<Ride> onTime = new Results<>();
        rideService.editRide(r.getRideId(), editForm(db.ride(r.getRideId())), ActivityStatus.ON_TIME, onTime);
        assertEquals(ActivityStatus.ON_TIME, onTime.get().getActivityStatus());
    }

    // ---------------------------------------------------------------- WB 2.4: non-organizer blocked

    @Test
    public void nonOrganizerCannotEditRide() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4, "bob"));
        as("bob"); // an accepted rider, but not the organizer
        Ride changes = editForm(r);
        changes.setDeparture(NOW.plusDays(1).plusHours(3));

        Results<Ride> res = new Results<>();
        rideService.editRide(r.getRideId(), changes, ActivityStatus.DELAYED, res);

        assertEquals(ErrorCode.NOT_ALLOWED, res.err().getCode());
        assertEquals("Only the ride organizer can change this ride.", res.err().getMessage());
        assertEquals(ActivityStatus.ON_TIME, db.ride(r.getRideId()).getActivityStatus());
        assertEquals(NOW.plusDays(1), db.ride(r.getRideId()).getDeparture());
        assertEquals(rideService.editBlockReason(r), res.err().getMessage());
    }

    @Test
    public void nonOrganizerCannotCancelRide() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4, "bob"));
        as("carol"); // not in the ride at all

        Results<Ride> res = new Results<>();
        rideService.cancelRide(r.getRideId(), res);

        assertEquals(ErrorCode.NOT_ALLOWED, res.err().getCode());
        assertTrue("ride still active", db.ride(r.getRideId()).isActive());

        // The organizer can, which shows the block is about who is asking.
        as("alice");
        Results<Ride> ok = new Results<>();
        rideService.cancelRide(r.getRideId(), ok);
        assertEquals(ActivityStatus.CANCELLED, db.ride(r.getRideId()).getActivityStatus());
    }

    @Test
    public void cancelledRideCannotBeEditedEvenByOrganizer() {
        Ride r = Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4);
        r.setActivityStatus(ActivityStatus.CANCELLED);
        db.put(r);
        as("alice");

        Results<Ride> res = new Results<>();
        rideService.editRide(r.getRideId(), editForm(r), ActivityStatus.ON_TIME, res);

        assertEquals(ErrorCode.NOT_ALLOWED, res.err().getCode());
        assertEquals("This ride is cancelled and can't be changed.", res.err().getMessage());
        assertEquals(ActivityStatus.CANCELLED, db.ride(r.getRideId()).getActivityStatus());
    }

    // ---------------------------------------------------------------- WB 2.5: max riders vs riders

    @Test
    public void maxRidersCannotBeEditedBelowCurrentRiderCount() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4, "bob", "carol"));
        as("alice");
        Ride changes = editForm(r);
        changes.setMaxRiders(2); // 3 riders already

        Results<Ride> res = new Results<>();
        rideService.editRide(r.getRideId(), changes, ActivityStatus.ON_TIME, res);

        assertEquals(ErrorCode.VALIDATION, res.err().getCode());
        assertEquals("This ride already has 3 riders, so the maximum can't be lower than that.",
                res.err().getFieldErrors().get(RideService.F_MAX_RIDERS));
        assertEquals(4, db.ride(r.getRideId()).getMaxRiders());
    }

    @Test
    public void maxRidersCanBeEditedDownToExactlyTheRiderCountAndBecomesFull() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4, "bob", "carol"));
        as("alice");
        Ride changes = editForm(r);
        changes.setMaxRiders(3); // boundary: equal to the rider count

        Results<Ride> res = new Results<>();
        rideService.editRide(r.getRideId(), changes, ActivityStatus.ON_TIME, res);

        Ride saved = db.ride(r.getRideId());
        assertEquals(3, res.get().getMaxRiders());
        assertEquals(0, saved.seatsLeft());
        assertEquals("Full – 3 of 3 riders", saved.capacityLabel());
        assertFalse(rideService.validateEdit(changes).containsKey(RideService.F_MAX_RIDERS));
    }
}
