package com.team23.trojanrideshare.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.CapacityStatus;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.RequestStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.testutil.Fixtures;
import com.team23.trojanrideshare.testutil.Results;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * Feature 3 (Requests to Join) white-box tests for {@link JoinRequestService}.
 * Written by Person 3. Each group below is one test case in the testing document:
 * WB 3.3 (sending a request), WB 3.4 (blocked requests), WB 3.5 (leaving a ride).
 */
public class JoinRequestServiceTest extends ServiceTestBase {

    /** Stores a ride organized by {@code organizer} with the given riders already accepted. */
    private Ride ride(String organizer, LocalDateTime departure, int max, String... riders) {
        return db.put(Fixtures.ride(organizer, Fixtures.USC, Fixtures.LAX, departure, max, riders));
    }

    private Results<JoinRequest> send(String rideId, String message) {
        Results<JoinRequest> res = new Results<>();
        joins.sendRequest(rideId, message, res);
        return res;
    }

    private Results<JoinRequestService.LeaveOutcome> leave(String rideId) {
        Results<JoinRequestService.LeaveOutcome> res = new Results<>();
        joins.leaveRide(rideId, res);
        return res;
    }

    /** Asserts the request was refused with this reason and nothing was written. */
    private void assertBlocked(String rideId, String expectedReason) {
        Ride before = db.ride(rideId);
        int requestsBefore = db.requests.size();

        AppException e = send(rideId, "please").err();

        assertEquals(ErrorCode.NOT_ALLOWED, e.getCode());
        assertEquals(expectedReason, e.getMessage());
        assertEquals("no request stored", requestsBefore, db.requests.size());
        Ride after = db.ride(rideId);
        assertEquals("ride's pending requests unchanged", before.getPendingRequests(), after.getPendingRequests());
        assertEquals("ride's riders unchanged", before.memberIds(), after.memberIds());
    }

    // ---------------------------------------------------------------- WB 3.3: sending a request

    @Test
    public void sendingRequestCreatesPendingRequestWithRequesterTimestampAndMessage() {
        Ride r = ride("alice", NOW.plusDays(2), 4);
        as("bob");

        JoinRequest q = send(r.getRideId(), "  Can I bring a suitcase?  ").get();

        assertEquals(RequestStatus.PENDING, q.getStatus());
        assertEquals("bob", q.getRequesterId());
        assertEquals("Bob", q.getRequesterName());
        assertEquals("alice", q.getOrganizerId());
        assertEquals(r.getRideId(), q.getRideId());
        assertEquals("message is trimmed", "Can I bring a suitcase?", q.getMessage());
        assertEquals("timestamp is the time it was sent", clock.instant(), q.getCreatedAt());
        assertNull("not answered yet", q.getRespondedAt());

        // Saved: the request document, and the ride's map of pending requests.
        JoinRequest stored = db.requests.get(q.getRequestId());
        assertNotNull(stored);
        assertEquals(RequestStatus.PENDING, stored.getStatus());
        assertEquals("Can I bring a suitcase?", stored.getMessage());
        Ride saved = db.ride(r.getRideId());
        assertEquals(q.getRequestId(), saved.pendingRequestIdOf("bob"));
        assertFalse("requesting does not make Bob a rider", saved.hasMember("bob"));
    }

    @Test
    public void messageIsOptional() {
        Ride r = ride("alice", NOW.plusDays(2), 4);
        as("bob");

        JoinRequest withNull = send(r.getRideId(), null).get();
        assertEquals("", withNull.getMessage());
        assertEquals(RequestStatus.PENDING, withNull.getStatus());

        Ride r2 = ride("alice", NOW.plusDays(3), 4);
        JoinRequest blank = send(r2.getRideId(), "   ").get();
        assertEquals("whitespace-only message is stored as empty", "", blank.getMessage());
    }

    @Test
    public void messageLengthBoundaryIs200Characters() {
        Ride r = ride("alice", NOW.plusDays(2), 4);
        as("bob");
        String max = repeat('a', JoinRequest.MAX_MESSAGE_LENGTH);

        AppException tooLong = send(r.getRideId(), max + "b").err();
        assertEquals(ErrorCode.VALIDATION, tooLong.getCode());
        assertTrue("nothing stored for a 201-character message", db.requests.isEmpty());

        assertEquals(max, send(r.getRideId(), max).get().getMessage());
    }

    @Test
    public void sentRequestIsListedUnderMyRequests() {
        Ride r = ride("alice", NOW.plusDays(2), 4);
        as("bob");
        JoinRequest q = send(r.getRideId(), "hi").get();

        Results<java.util.List<JoinRequest>> mine = new Results<>();
        joins.getMyRequests(mine);

        assertEquals(1, mine.get().size());
        JoinRequest listed = mine.get().get(0);
        assertEquals(q.getRequestId(), listed.getRequestId());
        assertEquals(RequestStatus.PENDING, listed.getStatus());
        assertEquals("ride is attached for the list row", r.getRideId(), listed.getRide().getRideId());
    }

    // ---------------------------------------------------------------- WB 3.4: blocked requests

    @Test
    public void cannotRequestToJoinOwnRide() {
        Ride r = ride("alice", NOW.plusDays(2), 4);
        as("alice");

        assertEquals(JoinRequestService.OWN_RIDE, joins.checkEligibility(db.ride(r.getRideId())));
        assertBlocked(r.getRideId(), JoinRequestService.OWN_RIDE);
    }

    @Test
    public void cannotRequestToJoinFullRide() {
        Ride r = ride("alice", NOW.plusDays(2), 2, "carol"); // 2 of 2
        as("bob");

        assertEquals(JoinRequestService.FULL, joins.checkEligibility(db.ride(r.getRideId())));
        assertBlocked(r.getRideId(), JoinRequestService.FULL);
    }

    @Test
    public void cannotRequestToJoinRideAlreadyJoined() {
        Ride r = ride("alice", NOW.plusDays(2), 4, "bob");
        as("bob");

        assertEquals(JoinRequestService.ALREADY_RIDER, joins.checkEligibility(db.ride(r.getRideId())));
        assertBlocked(r.getRideId(), JoinRequestService.ALREADY_RIDER);
    }

    @Test
    public void alreadyJoinedTakesPriorityOverFull() {
        // Bob is in a ride that is now Full: the reason shown should say he is already in it.
        Ride r = ride("alice", NOW.plusDays(2), 2, "bob");
        as("bob");

        assertBlocked(r.getRideId(), JoinRequestService.ALREADY_RIDER);
    }

    @Test
    public void cannotSendSecondRequestWhileOneIsPending() {
        Ride r = ride("alice", NOW.plusDays(2), 4);
        as("bob");
        send(r.getRideId(), "first").get();

        assertBlocked(r.getRideId(), JoinRequestService.ALREADY_PENDING);
        assertEquals(1, db.requests.size());
    }

    @Test
    public void eligibilityIsCheckedAgainWhenTheRequestIsSent() {
        // The screen showed an open seat, but someone was accepted before Bob tapped Send.
        Ride r = ride("alice", NOW.plusDays(2), 2);
        as("bob");
        Ride shown = db.ride(r.getRideId());
        assertNull(joins.checkEligibility(shown));

        Ride filled = db.ride(r.getRideId());
        filled.getMembers().add(new com.team23.trojanrideshare.domain.RideMember("carol", "Carol", clock.instant()));
        filled.updateCapacity();
        db.put(filled);

        assertBlocked(r.getRideId(), JoinRequestService.FULL);
    }

    @Test
    public void cannotRequestToJoinCancelledOrDepartedRide() {
        Ride cancelled = ride("alice", NOW.plusDays(2), 4);
        cancelled.setActivityStatus(ActivityStatus.CANCELLED);
        db.put(cancelled);
        Ride departed = ride("alice", NOW.minusMinutes(1), 4);
        as("bob");

        assertBlocked(cancelled.getRideId(), JoinRequestService.UNAVAILABLE);
        assertBlocked(departed.getRideId(), JoinRequestService.DEPARTED);
    }

    // ---------------------------------------------------------------- WB 3.5: leaving a ride

    @Test
    public void riderCanLeaveExactlyThreeHoursBeforeDeparture() {
        Ride r = ride("alice", NOW.plus(JoinRequestService.LEAVE_CUTOFF), 4, "bob");
        as("bob");

        assertNull(joins.leaveBlockReason(db.ride(r.getRideId())));
        JoinRequestService.LeaveOutcome out = leave(r.getRideId()).get();

        assertFalse(out.rideCancelled);
        assertNull("no organizer change", out.newOrganizerName);
        Ride saved = db.ride(r.getRideId());
        assertEquals(Arrays.asList("alice"), saved.memberIds());
        assertEquals(ActivityStatus.ON_TIME, saved.getActivityStatus());
    }

    @Test
    public void riderCannotLeaveWithinThreeHoursOfDeparture() {
        Ride r = ride("alice", NOW.plus(JoinRequestService.LEAVE_CUTOFF).minusMinutes(1), 4, "bob");
        as("bob");

        assertEquals(JoinRequestService.LEAVE_TOO_LATE, joins.leaveBlockReason(db.ride(r.getRideId())));
        assertFalse(joins.canLeave(db.ride(r.getRideId())));
        AppException e = leave(r.getRideId()).err();

        assertEquals(ErrorCode.NOT_ALLOWED, e.getCode());
        assertEquals(JoinRequestService.LEAVE_TOO_LATE, e.getMessage());
        assertEquals("Bob is still a rider", Arrays.asList("alice", "bob"), db.ride(r.getRideId()).memberIds());
    }

    @Test
    public void leaveWindowClosesAsTheClockMoves() {
        Ride r = ride("alice", NOW.plusHours(4), 4, "bob");
        as("bob");
        assertTrue(joins.canLeave(db.ride(r.getRideId())));

        clock.advance(Duration.ofHours(1).plusSeconds(1)); // now 2h59m59s before departure

        assertFalse(joins.canLeave(db.ride(r.getRideId())));
        leave(r.getRideId()).err();
    }

    @Test
    public void leavingAFullRideReopensIt() {
        Ride r = ride("alice", NOW.plusDays(1), 2, "bob");
        assertEquals(CapacityStatus.FULL, db.ride(r.getRideId()).getCapacityStatus());
        as("bob");

        leave(r.getRideId()).get();

        Ride saved = db.ride(r.getRideId());
        assertEquals(CapacityStatus.OPEN, saved.getCapacityStatus());
        assertEquals(1, saved.seatsLeft());
        assertEquals("Open – 1 of 2 riders", saved.capacityLabel());
    }

    @Test
    public void soloOrganizerLeavingCancelsRideAndRejectsPendingRequests() {
        Ride r = ride("alice", NOW.plusDays(1), 4);
        as("dave");
        JoinRequest pending = send(r.getRideId(), "room?").get();
        as("alice");

        JoinRequestService.LeaveOutcome out = leave(r.getRideId()).get();

        assertTrue(out.rideCancelled);
        assertNull(out.newOrganizerName);
        Ride saved = db.ride(r.getRideId());
        assertEquals(ActivityStatus.CANCELLED, saved.getActivityStatus());
        assertEquals(CapacityStatus.NOT_APPLICABLE, saved.getCapacityStatus());
        assertEquals("organizer stays on the cancelled ride's record", Arrays.asList("alice"), saved.memberIds());
        assertEquals(0, saved.pendingCount());
        assertEquals(RequestStatus.REJECTED, db.requests.get(pending.getRequestId()).getStatus());
    }

    @Test
    public void organizerLeavingTransfersOwnershipToMostRecentlyAcceptedRider() {
        // Bob accepted before Carol (Fixtures gives later riders later acceptance times).
        Ride r = ride("alice", NOW.plusDays(1), 4, "bob", "carol");
        as("dave");
        JoinRequest pending = send(r.getRideId(), "room?").get();
        as("alice");
        assertEquals("carol", joins.successorIfLeaving(db.ride(r.getRideId())).getUserId());

        JoinRequestService.LeaveOutcome out = leave(r.getRideId()).get();

        assertFalse(out.rideCancelled);
        assertEquals("CAROL", out.newOrganizerName);
        Ride saved = db.ride(r.getRideId());
        assertEquals("carol", saved.getOrganizerId());
        assertEquals("CAROL", saved.getOrganizerName());
        assertEquals(Arrays.asList("bob", "carol"), saved.memberIds());
        assertTrue("ride continues", saved.isActive());
        assertEquals(CapacityStatus.OPEN, saved.getCapacityStatus());
        // The pending request now goes to the new organizer and is still pending.
        JoinRequest moved = db.requests.get(pending.getRequestId());
        assertEquals("carol", moved.getOrganizerId());
        assertEquals(RequestStatus.PENDING, moved.getStatus());
        assertEquals(pending.getRequestId(), saved.pendingRequestIdOf("dave"));
    }

    @Test
    public void organizerIsAlsoBoundByTheThreeHourRule() {
        Ride r = ride("alice", NOW.plusHours(2), 4, "bob");
        as("alice");

        assertEquals(JoinRequestService.LEAVE_TOO_LATE, leave(r.getRideId()).err().getMessage());
        Ride saved = db.ride(r.getRideId());
        assertEquals("alice", saved.getOrganizerId());
        assertEquals(ActivityStatus.ON_TIME, saved.getActivityStatus());
    }

    @Test
    public void nonMemberCannotLeave() {
        Ride r = ride("alice", NOW.plusDays(1), 4);
        as("bob");

        assertEquals("You're not in this ride.", leave(r.getRideId()).err().getMessage());
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}
