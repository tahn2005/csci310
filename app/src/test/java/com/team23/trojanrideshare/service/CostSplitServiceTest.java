package com.team23.trojanrideshare.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.CapacityStatus;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.NonPaymentFlag;
import com.team23.trojanrideshare.domain.PaymentStatus;
import com.team23.trojanrideshare.domain.RequestStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.testutil.Fixtures;
import com.team23.trojanrideshare.testutil.Results;

import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CostSplitServiceTest extends ServiceTestBase {

    private Ride departedRide() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusHours(1), 4, "bob", "carol"));
        clock.advance(Duration.ofHours(2));
        return r;
    }

    @Test
    public void equalSplitOfTenDollarsAmongThree() throws AppException {
        Ride r = departedRide();
        List<CostShare> shares = costs.preview(r, 1000, new EqualSplit(null));
        assertEquals(334, shares.get(0).getAmountCents());
        assertEquals(333, shares.get(1).getAmountCents());
        assertEquals(333, shares.get(2).getAmountCents());
    }

    @Test
    public void organizerChoosesWhoGetsExtraCent() throws AppException {
        Ride r = departedRide();
        // $47.00 / 3 = $15.66 with 2 cents left over.
        List<CostShare> shares = costs.preview(r, 4700, new EqualSplit(List.of("alice", "carol")));
        assertEquals(1567, shares.get(0).getAmountCents());
        assertEquals(1566, shares.get(1).getAmountCents());
        assertEquals(1567, shares.get(2).getAmountCents());
        try {
            costs.preview(r, 4700, new EqualSplit(List.of("carol")));
        } catch (AppException e) {
            assertEquals(ErrorCode.VALIDATION, e.getCode());
            return;
        }
        throw new AssertionError("expected validation error");
    }

    @Test
    public void customSplitMustAddUp() {
        Ride r = departedRide();
        Map<String, Long> amounts = new HashMap<>();
        amounts.put("alice", 1000L);
        amounts.put("bob", 1000L);
        amounts.put("carol", 999L);
        try {
            costs.preview(r, 3000, new CustomSplit(amounts));
            throw new AssertionError("expected validation error");
        } catch (AppException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("$0.01 less"));
        }
    }

    @Test
    public void completingSavesSharesAndStatusTogether() {
        Ride r = departedRide();
        as("dave");
        JoinRequest q = new JoinRequest();
        // A pending request that must be auto-rejected on completion (FR-3.11.2).
        clock.advance(Duration.ofHours(-2));
        Results<JoinRequest> sent = new Results<>();
        joins.sendRequest(r.getRideId(), "", sent);
        q = sent.get();
        clock.advance(Duration.ofHours(2));

        as("alice");
        Results<Ride> res = new Results<>();
        costs.completeRide(r.getRideId(), 4700, new EqualSplit(null), res);
        Ride after = db.ride(r.getRideId());
        assertEquals(ActivityStatus.COMPLETED, after.getActivityStatus());
        assertEquals(CapacityStatus.NOT_APPLICABLE, after.getCapacityStatus());
        assertEquals(4700, after.getActualCostCents());
        assertEquals(PaymentStatus.PAID, after.shareOf("alice").getStatus());
        assertEquals(PaymentStatus.UNPAID, after.shareOf("bob").getStatus());
        long sum = 0;
        for (CostShare s : after.getShares()) sum += s.getAmountCents();
        assertEquals(4700, sum);
        assertEquals(RequestStatus.REJECTED, db.requests.get(q.getRequestId()).getStatus());
    }

    @Test
    public void cannotCompleteBeforeDepartureOrAsRider() {
        Ride r = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusHours(1), 4, "bob"));
        as("alice");
        assertNotNull(costs.completeBlockReason(r));
        Results<Ride> early = new Results<>();
        costs.completeRide(r.getRideId(), 1000, new EqualSplit(null), early);
        assertEquals(ErrorCode.NOT_ALLOWED, early.err().getCode());

        clock.advance(Duration.ofHours(2));
        as("bob");
        Results<Ride> rider = new Results<>();
        costs.completeRide(r.getRideId(), 1000, new EqualSplit(null), rider);
        assertEquals(ErrorCode.NOT_ALLOWED, rider.err().getCode());
        assertEquals(ActivityStatus.ON_TIME, db.ride(r.getRideId()).getActivityStatus());
    }

    @Test
    public void invalidCostLeavesRideUnchanged() {
        Ride r = departedRide();
        as("alice");
        Results<Ride> res = new Results<>();
        costs.completeRide(r.getRideId(), 0, new EqualSplit(null), res);
        assertEquals(ErrorCode.VALIDATION, res.err().getCode());
        assertEquals(ActivityStatus.ON_TIME, db.ride(r.getRideId()).getActivityStatus());
    }

    @Test
    public void flagThenPayClearsFlag() {
        Ride r = departedRide();
        as("alice");
        costs.completeRide(r.getRideId(), 4500, new EqualSplit(null), new Results<>());

        Results<NonPaymentFlag> own = new Results<>();
        costs.flag(r.getRideId(), "alice", own);
        assertEquals(ErrorCode.NOT_ALLOWED, own.err().getCode());

        Results<NonPaymentFlag> flagged = new Results<>();
        costs.flag(r.getRideId(), "bob", flagged);
        flagged.get();
        Results<List<NonPaymentFlag>> mine = new Results<>();
        as("bob");
        costs.getMyFlags(mine);
        assertEquals(1, mine.get().size());

        // Only bob can mark bob's share paid.
        as("carol");
        costs.markPaid(r.getRideId(), new Results<>());
        assertEquals(PaymentStatus.UNPAID, db.ride(r.getRideId()).shareOf("bob").getStatus());

        as("bob");
        Results<Ride> paid = new Results<>();
        costs.markPaid(r.getRideId(), paid);
        paid.get();
        assertEquals(PaymentStatus.PAID, db.ride(r.getRideId()).shareOf("bob").getStatus());
        Results<List<NonPaymentFlag>> after = new Results<>();
        costs.getMyFlags(after);
        assertTrue(after.get().isEmpty());

        as("alice");
        Results<NonPaymentFlag> paidAlready = new Results<>();
        costs.flag(r.getRideId(), "bob", paidAlready);
        assertEquals("This rider has already paid.", paidAlready.err().getMessage());
    }

    @Test
    public void nonMemberHasNoShare() {
        Ride r = departedRide();
        as("alice");
        costs.completeRide(r.getRideId(), 4500, new EqualSplit(null), new Results<>());
        as("dave");
        Results<Ride> res = new Results<>();
        costs.markPaid(r.getRideId(), res);
        assertEquals(ErrorCode.NOT_ALLOWED, res.err().getCode());
        assertNull(db.ride(r.getRideId()).shareOf("dave"));
    }
}
