package com.team23.trojanrideshare.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.CapacityStatus;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.Place;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.User;
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

        Results<Ride> notMine = new Results<>();
        rideService.editRide(r.getRideId(), edits(r), ActivityStatus.DELAYED, notMine);
        assertEquals(ErrorCode.NOT_ALLOWED, notMine.err().getCode());

        as("alice");
        Ride later = edits(r);
        later.setDeparture(NOW.plusDays(1).plusHours(2));
        Results<Ride> delayed = new Results<>();
        rideService.editRide(r.getRideId(), later, ActivityStatus.DELAYED, delayed);
        assertEquals(ActivityStatus.DELAYED, db.ride(r.getRideId()).getActivityStatus());

        Results<Ride> cancelled = new Results<>();
        rideService.cancelRide(r.getRideId(), cancelled);
        assertEquals(ActivityStatus.CANCELLED, db.ride(r.getRideId()).getActivityStatus());
        // A cancelled ride no longer advertises open seats.
        assertEquals(CapacityStatus.NOT_APPLICABLE, db.ride(r.getRideId()).getCapacityStatus());
    }

    /** The edit form pre-filled with the ride's current details. */
    private static Ride edits(Ride r) {
        Ride c = new Ride();
        c.setTitle(r.getTitle());
        c.setDeparture(r.getDeparture());
        c.setMaxRiders(r.getMaxRiders());
        c.setExpectedCostCents(r.getExpectedCostCents());
        c.setNote(r.getNote());
        return c;
    }

    @Test
    public void newDepartureMustBeInFuture() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4));
        as("alice");
        Ride past = edits(r);
        past.setDeparture(NOW.minusHours(1));
        Results<Ride> res = new Results<>();
        rideService.editRide(r.getRideId(), past, ActivityStatus.DELAYED, res);
        assertEquals(ErrorCode.VALIDATION, res.err().getCode());
    }

    @Test
    public void editChangesDetailsButNotRoute() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4, "bob"));
        as("alice");
        Ride c = edits(r);
        c.setTitle("  LAX, earlier flight ");
        c.setMaxRiders(2);
        c.setExpectedCostCents(5200);
        c.setNote("Meet at the fountain");
        Results<Ride> res = new Results<>();
        rideService.editRide(r.getRideId(), c, ActivityStatus.ON_TIME, res);
        res.get();
        Ride saved = db.ride(r.getRideId());
        assertEquals("LAX, earlier flight", saved.getTitle());
        assertEquals(5200, saved.getExpectedCostCents());
        assertEquals("Meet at the fountain", saved.getNote());
        assertEquals(Fixtures.LAX, saved.getDestination());
        // Two riders in a two-seat ride: the ride is now Full.
        assertEquals(CapacityStatus.FULL, saved.getCapacityStatus());
    }

    @Test
    public void maxRidersCantDropBelowCurrentRiders() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4, "bob", "carol"));
        as("alice");
        Ride c = edits(r);
        c.setMaxRiders(2);
        Results<Ride> res = new Results<>();
        rideService.editRide(r.getRideId(), c, ActivityStatus.ON_TIME, res);
        assertEquals(ErrorCode.VALIDATION, res.err().getCode());
        assertTrue(res.err().getFieldErrors().containsKey(RideService.F_MAX_RIDERS));
        assertEquals(4, db.ride(r.getRideId()).getMaxRiders());
    }

    @Test
    public void nicknameChangeShowsOnRidesAndPendingRequests() {
        Ride mine = db.put(Fixtures.ride("bob", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4, "alice"));
        Ride joined = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.UNION, NOW.plusDays(2), 4, "bob"));
        Ride other = db.put(Fixtures.ride("carol", Fixtures.USC, Fixtures.SANTA_MONICA, NOW.plusDays(3), 4));
        as("bob");
        Results<JoinRequest> sent = new Results<>();
        joins.sendRequest(other.getRideId(), "", sent);
        sent.get();

        renameTo("Bobby");

        assertEquals("Bobby", db.ride(mine.getRideId()).getOrganizerName());
        assertEquals("Bobby", db.ride(mine.getRideId()).member("bob").getDisplayName());
        assertEquals("Bobby", db.ride(joined.getRideId()).member("bob").getDisplayName());
        assertEquals("ALICE", db.ride(joined.getRideId()).getOrganizerName());
        assertEquals("ALICE", db.ride(joined.getRideId()).member("alice").getDisplayName());
        assertEquals("Bobby", db.requests.get(sent.get().getRequestId()).getRequesterName());
    }

    /** Saves the nickname and runs both syncs in the order ProfileViewModel uses. */
    private void renameTo(String nickname) {
        Results<User> renamed = new Results<>();
        accounts.updateNickname(nickname, renamed);
        renamed.get();
        Results<Void> requests = new Results<>();
        joins.syncDisplayName(requests);
        requests.get();
        Results<Void> rides = new Results<>();
        rideService.syncDisplayName(rides);
        rides.get();
    }

    @Test
    public void nicknameChangeReachesCompletedRideShares() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.minusDays(1), 4, "bob"));
        as("alice");
        Results<Ride> done = new Results<>();
        costs.completeRide(r.getRideId(), 1000, new EqualSplit(null), done);
        done.get();
        as("bob");
        renameTo("Bobby");
        assertEquals("Bobby", db.ride(r.getRideId()).shareOf("bob").getDisplayName());
        assertEquals("ALICE", db.ride(r.getRideId()).shareOf("alice").getDisplayName());
        assertEquals(500, db.ride(r.getRideId()).shareOf("bob").getAmountCents());
    }

    @Test
    public void clearingNicknameRestoresFullName() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4, "bob"));
        as("bob");
        renameTo("Bobby");
        renameTo("");
        assertEquals("Bob", db.ride(r.getRideId()).member("bob").getDisplayName());
    }

    @Test
    public void requestAcceptedBeforeSyncStillGetsNewName() {
        Ride carols = db.put(Fixtures.ride("carol", Fixtures.USC, Fixtures.UNION, NOW.plusDays(1), 4));
        as("bob");
        Results<JoinRequest> sent = new Results<>();
        joins.sendRequest(carols.getRideId(), "", sent);
        Results<User> renamed = new Results<>();
        accounts.updateNickname("Bobby", renamed);
        renamed.get();

        // Carol accepts the request (still carrying the old name) before Bob's sync runs.
        as("carol");
        Results<Ride> accepted = new Results<>();
        joins.accept(sent.get(), accepted);
        accepted.get();
        assertEquals("Bob", db.ride(carols.getRideId()).member("bob").getDisplayName());

        session.set(db.users.get("bob"));
        Results<Void> requests = new Results<>();
        joins.syncDisplayName(requests);
        requests.get();
        Results<Void> rides = new Results<>();
        rideService.syncDisplayName(rides);
        rides.get();
        assertEquals("Bobby", db.ride(carols.getRideId()).member("bob").getDisplayName());
    }

    @Test
    public void requestAcceptedBetweenSyncsStillGetsNewName() {
        Ride carols = db.put(Fixtures.ride("carol", Fixtures.USC, Fixtures.UNION, NOW.plusDays(1), 4));
        as("bob");
        Results<JoinRequest> sent = new Results<>();
        joins.sendRequest(carols.getRideId(), "", sent);
        Results<User> renamed = new Results<>();
        accounts.updateNickname("Bobby", renamed);
        renamed.get();
        Results<Void> requests = new Results<>();
        joins.syncDisplayName(requests);
        requests.get();

        // Carol accepts after the request was renamed but before the rides pass runs.
        as("carol");
        Results<Ride> accepted = new Results<>();
        joins.accept(db.requests.get(sent.get().getRequestId()), accepted);
        accepted.get();

        session.set(db.users.get("bob"));
        Results<Void> rides = new Results<>();
        rideService.syncDisplayName(rides);
        rides.get();
        assertEquals("Bobby", db.ride(carols.getRideId()).member("bob").getDisplayName());
    }

    @Test
    public void syncWithoutSessionFails() {
        session.clear();
        Results<Void> res = new Results<>();
        rideService.syncDisplayName(res);
        assertEquals(ErrorCode.NOT_ALLOWED, res.err().getCode());
        Results<Void> res2 = new Results<>();
        joins.syncDisplayName(res2);
        assertEquals(ErrorCode.NOT_ALLOWED, res2.err().getCode());
    }

    @Test
    public void editValidationCoversEveryField() {
        Ride c = new Ride();
        c.setTitle("x".repeat(RideService.MAX_TITLE_LENGTH + 1));
        c.setDeparture(NOW.minusMinutes(1));
        c.setMaxRiders(7);
        c.setExpectedCostCents(0);
        c.setNote("x".repeat(RideService.MAX_NOTE_LENGTH + 1));
        Map<String, String> e = rideService.validateEdit(c);
        assertEquals(5, e.size());
        c.setTitle("  ");
        c.setMaxRiders(1);
        assertTrue(rideService.validateEdit(c).containsKey(RideService.F_TITLE));
        assertTrue(rideService.validateEdit(c).containsKey(RideService.F_MAX_RIDERS));
    }

    @Test
    public void editCanOnlySetOnTimeOrDelayed() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4));
        as("alice");
        Results<Ride> res = new Results<>();
        rideService.editRide(r.getRideId(), edits(r), ActivityStatus.COMPLETED, res);
        assertEquals(ErrorCode.VALIDATION, res.err().getCode());
        assertEquals(ActivityStatus.ON_TIME, db.ride(r.getRideId()).getActivityStatus());
    }

    @Test
    public void cancelledRideCannotBeEdited() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 4));
        as("alice");
        Results<Ride> cancelled = new Results<>();
        rideService.cancelRide(r.getRideId(), cancelled);
        cancelled.get();
        Results<Ride> res = new Results<>();
        rideService.editRide(r.getRideId(), edits(r), ActivityStatus.ON_TIME, res);
        assertEquals(ErrorCode.NOT_ALLOWED, res.err().getCode());
        assertEquals(ActivityStatus.CANCELLED, db.ride(r.getRideId()).getActivityStatus());
    }

    @Test
    public void addingSeatsReopensAFullRide() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), 2, "bob"));
        assertEquals(CapacityStatus.FULL, r.getCapacityStatus());
        as("alice");
        Ride c = edits(r);
        c.setMaxRiders(4);
        Results<Ride> res = new Results<>();
        rideService.editRide(r.getRideId(), c, ActivityStatus.ON_TIME, res);
        res.get();
        assertEquals(CapacityStatus.OPEN, db.ride(r.getRideId()).getCapacityStatus());
    }
}
