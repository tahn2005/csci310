package com.team23.trojanrideshare.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.CapacityStatus;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.RequestStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.testutil.Fixtures;
import com.team23.trojanrideshare.testutil.Results;

import org.junit.Test;

import java.time.Duration;
import java.util.List;

public class JoinRequestServiceTest extends ServiceTestBase {

    private Ride ride(int max, String... riders) {
        return db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(1), max, riders));
    }

    private JoinRequest send(String uid, Ride r) {
        as(uid);
        Results<JoinRequest> res = new Results<>();
        joins.sendRequest(r.getRideId(), "hi", res);
        return res.get();
    }

    @Test
    public void eligibilityMessages() {
        Ride r = ride(3, "bob");
        as("alice");
        assertEquals(JoinRequestService.OWN_RIDE, joins.checkEligibility(r));
        as("bob");
        assertEquals(JoinRequestService.ALREADY_RIDER, joins.checkEligibility(r));
        as("carol");
        assertNull(joins.checkEligibility(r));

        send("carol", r);
        as("carol");
        assertEquals(JoinRequestService.ALREADY_PENDING, joins.checkEligibility(db.ride(r.getRideId())));

        Ride full = ride(2, "bob");
        as("dave");
        assertEquals(JoinRequestService.FULL, joins.checkEligibility(full));

        Ride done = ride(4);
        done.setActivityStatus(ActivityStatus.COMPLETED);
        assertEquals(JoinRequestService.UNAVAILABLE, joins.checkEligibility(done));

        clock.advance(Duration.ofDays(2));
        assertEquals(JoinRequestService.DEPARTED, joins.checkEligibility(ride(4)));
    }

    @Test
    public void sendCreatesPendingRequest() {
        Ride r = ride(4);
        JoinRequest q = send("bob", r);
        assertEquals(RequestStatus.PENDING, q.getStatus());
        assertEquals("alice", q.getOrganizerId());
        assertEquals(NOW, java.time.LocalDateTime.ofInstant(q.getCreatedAt(), java.time.ZoneId.systemDefault()));
        assertEquals(q.getRequestId(), db.ride(r.getRideId()).pendingRequestIdOf("bob"));
    }

    @Test
    public void sendRechecksEligibilityInsideTransaction() {
        Ride r = ride(2);
        Ride stale = db.ride(r.getRideId());
        // Someone else fills the ride after bob opened it (FR-3.E4).
        Ride filled = db.ride(r.getRideId());
        filled.getMembers().add(new com.team23.trojanrideshare.domain.RideMember("carol", "Carol", null));
        filled.updateCapacity();
        db.rides.put(r.getRideId(), filled);
        as("bob");
        assertNull(joins.checkEligibility(stale));
        Results<JoinRequest> res = new Results<>();
        joins.sendRequest(r.getRideId(), "", res);
        assertEquals(JoinRequestService.FULL, res.err().getMessage());
        assertTrue(db.requests.isEmpty());
    }

    @Test
    public void messageLimitIs200Characters() {
        Ride r = ride(4);
        as("bob");
        Results<JoinRequest> res = new Results<>();
        joins.sendRequest(r.getRideId(), "x".repeat(201), res);
        assertEquals(ErrorCode.VALIDATION, res.err().getCode());
    }

    @Test
    public void acceptAddsRiderAndFillsRide() {
        Ride r = ride(2);
        JoinRequest q = send("bob", r);
        JoinRequest q2 = send("carol", r);
        as("alice");
        Results<Ride> res = new Results<>();
        joins.accept(q, res);
        Ride after = db.ride(r.getRideId());
        assertTrue(after.hasMember("bob"));
        assertEquals(CapacityStatus.FULL, after.getCapacityStatus());
        assertEquals(RequestStatus.ACCEPTED, db.requests.get(q.getRequestId()).getStatus());
        // FR-3.11.1: other pending requests stay pending when the ride fills.
        assertEquals(RequestStatus.PENDING, db.requests.get(q2.getRequestId()).getStatus());

        Results<Ride> fullRes = new Results<>();
        joins.accept(q2, fullRes);
        assertEquals(JoinRequestService.FULL_ON_ACCEPT, fullRes.err().getMessage());
    }

    @Test
    public void onlyOrganizerAnswersAndAnswersOnce() {
        Ride r = ride(4);
        JoinRequest q = send("bob", r);
        as("carol");
        Results<Ride> denied = new Results<>();
        joins.reject(q, denied);
        assertEquals(ErrorCode.NOT_ALLOWED, denied.err().getCode());

        as("alice");
        joins.reject(q, new Results<>());
        assertEquals(RequestStatus.REJECTED, db.requests.get(q.getRequestId()).getStatus());
        assertFalse(db.ride(r.getRideId()).hasMember("bob"));
        Results<Ride> again = new Results<>();
        joins.accept(q, again);
        assertEquals(JoinRequestService.ANSWERED, again.err().getMessage());

        // FR-3.11.3: a rejected user may request again.
        as("bob");
        assertNull(joins.checkEligibility(db.ride(r.getRideId())));
    }

    @Test
    public void cancelPendingRequestDeletesIt() {
        Ride r = ride(4);
        JoinRequest q = send("bob", r);
        as("bob");
        Results<Void> res = new Results<>();
        joins.cancelRequest(r.getRideId(), q.getRequestId(), res);
        res.get();
        assertFalse(db.requests.containsKey(q.getRequestId()));
        assertEquals(0, db.ride(r.getRideId()).pendingCount());

        as("alice");
        Results<Ride> late = new Results<>();
        joins.accept(q, late);
        assertEquals(JoinRequestService.WITHDRAWN, late.err().getMessage());
    }

    @Test
    public void riderCanLeaveUntilThreeHoursBefore() {
        Ride r = ride(2, "bob");
        as("bob");
        Results<JoinRequestService.LeaveOutcome> res = new Results<>();
        joins.leaveRide(r.getRideId(), res);
        assertFalse(res.get().rideCancelled);
        Ride after = db.ride(r.getRideId());
        assertFalse(after.hasMember("bob"));
        assertEquals(CapacityStatus.OPEN, after.getCapacityStatus());
        assertEquals(ActivityStatus.ON_TIME, after.getActivityStatus());

        Ride soon = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusHours(2), 4, "bob"));
        as("bob");
        assertEquals(JoinRequestService.LEAVE_TOO_LATE, joins.leaveBlockReason(soon));
        Results<JoinRequestService.LeaveOutcome> late = new Results<>();
        joins.leaveRide(soon.getRideId(), late);
        assertEquals(ErrorCode.NOT_ALLOWED, late.err().getCode());
    }

    @Test
    public void organizerLeavingHandsRideToMostRecentRider() {
        Ride r = ride(4, "bob", "carol");
        JoinRequest q = send("dave", r);
        as("alice");
        assertEquals("carol", joins.successorIfLeaving(db.ride(r.getRideId())).getUserId());
        Results<JoinRequestService.LeaveOutcome> res = new Results<>();
        joins.leaveRide(r.getRideId(), res);
        assertEquals("CAROL", res.get().newOrganizerName);
        Ride after = db.ride(r.getRideId());
        assertEquals("carol", after.getOrganizerId());
        assertEquals(2, after.riderCount());
        // The new organizer gets the pending requests.
        assertEquals("carol", db.requests.get(q.getRequestId()).getOrganizerId());
    }

    @Test
    public void onlyRiderLeavingCancelsRideAndRejectsPending() {
        Ride r = ride(4);
        JoinRequest q = send("bob", r);
        as("alice");
        Results<JoinRequestService.LeaveOutcome> res = new Results<>();
        joins.leaveRide(r.getRideId(), res);
        assertTrue(res.get().rideCancelled);
        assertEquals(ActivityStatus.CANCELLED, db.ride(r.getRideId()).getActivityStatus());
        assertEquals(CapacityStatus.NOT_APPLICABLE, db.ride(r.getRideId()).getCapacityStatus());
        assertEquals(RequestStatus.REJECTED, db.requests.get(q.getRequestId()).getStatus());
    }

    @Test
    public void myRequestsAndIncomingRequests() {
        Ride r1 = ride(4);
        Ride r2 = ride(4);
        send("bob", r1);
        clock.advance(Duration.ofMinutes(5));
        send("bob", r2);
        send("carol", r2);
        as("bob");
        Results<List<JoinRequest>> mine = new Results<>();
        joins.getMyRequests(mine);
        assertEquals(2, mine.get().size());
        assertEquals(r2.getRideId(), mine.get().get(0).getRideId()); // newest first
        assertEquals(r2.getRideId(), mine.get().get(0).getRide().getRideId());

        as("alice");
        Results<List<JoinRequest>> incoming = new Results<>();
        joins.getIncomingRequests(incoming);
        assertEquals(3, incoming.get().size());
    }

    @Test
    public void offlineWritesFailWithoutChanges() {
        Ride r = ride(4);
        db.offline = true;
        as("bob");
        Results<JoinRequest> res = new Results<>();
        joins.sendRequest(r.getRideId(), "", res);
        assertEquals(ErrorCode.OFFLINE, res.err().getCode());
        db.offline = false;
        assertEquals(0, db.ride(r.getRideId()).pendingCount());
    }
}
