package com.team23.trojanrideshare.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.HistoryEntry;
import com.team23.trojanrideshare.domain.HistoryFilter;
import com.team23.trojanrideshare.domain.MonthlySummary;
import com.team23.trojanrideshare.domain.PaymentStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.RideMember;
import com.team23.trojanrideshare.domain.User;
import com.team23.trojanrideshare.testutil.Fixtures;
import com.team23.trojanrideshare.testutil.Results;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class RideHistoryServiceTest extends ServiceTestBase {

    private Ride sepCompleted, octCompleted, octCancelled, octJoined, upcoming;

    private Ride completed(String org, LocalDateTime when, long myCents, String... riders) {
        Ride r = Fixtures.ride(org, Fixtures.USC, Fixtures.LAX, when, 4, riders);
        r.setActivityStatus(ActivityStatus.COMPLETED);
        r.updateCapacity();
        List<CostShare> shares = new ArrayList<>();
        for (RideMember m : r.getMembers()) {
            CostShare s = new CostShare(null, m.getUserId(), m.getDisplayName(), myCents);
            if (m.getUserId().equals(org)) s.setStatus(PaymentStatus.PAID);
            shares.add(s);
        }
        r.setShares(shares);
        r.setActualCostCents(myCents * r.riderCount());
        return db.put(r);
    }

    @Before
    public void rides() {
        sepCompleted = completed("alice", LocalDateTime.of(2026, 9, 12, 9, 0), 1500, "bob");
        octCompleted = completed("alice", LocalDateTime.of(2026, 10, 3, 9, 0), 2000, "carol");
        octJoined = completed("bob", LocalDateTime.of(2026, 10, 20, 18, 0), 1250, "alice");
        Ride c = Fixtures.ride("alice", Fixtures.USC, Fixtures.UNION, LocalDateTime.of(2026, 10, 10, 9, 0), 4, "dave");
        c.setActivityStatus(ActivityStatus.CANCELLED);
        octCancelled = db.put(c);
        upcoming = db.put(Fixtures.ride("alice", Fixtures.USC, Fixtures.LAX, NOW.plusDays(3), 4));
        as("alice");
    }

    private List<HistoryEntry> history(HistoryFilter f) {
        Results<List<HistoryEntry>> r = new Results<>();
        history.getHistory(f, r);
        return r.get();
    }

    @Test
    public void historyShowsCompletedAndCancelledNewestFirst() {
        List<HistoryEntry> h = history(new HistoryFilter());
        assertEquals(4, h.size());
        assertEquals(octJoined.getRideId(), h.get(0).getRide().getRideId());
        assertEquals(sepCompleted.getRideId(), h.get(3).getRide().getRideId());
        assertEquals(1250, h.get(0).getMyShare().getAmountCents());
        assertNull(h.get(1).getMyShare()); // cancelled ride has no share
    }

    @Test
    public void oldestFirstAndFilters() {
        HistoryFilter f = new HistoryFilter();
        f.setNewestFirst(false);
        assertEquals(sepCompleted.getRideId(), history(f).get(0).getRide().getRideId());

        f.setStatus(ActivityStatus.CANCELLED);
        assertEquals(1, history(f).size());

        HistoryFilter byCarol = new HistoryFilter();
        byCarol.setParticipantId("carol");
        assertEquals(List.of(octCompleted.getRideId()), List.of(history(byCarol).get(0).getRide().getRideId()));

        HistoryFilter toUnion = new HistoryFilter();
        toUnion.setDestination(Fixtures.UNION);
        toUnion.setRadiusMiles(1);
        assertEquals(octCancelled.getRideId(), history(toUnion).get(0).getRide().getRideId());
        assertEquals(1, history(toUnion).size());
    }

    @Test
    public void participantsListExcludesMe() {
        List<RideMember> p = RideHistoryService.participantsOf(history(new HistoryFilter()), "alice");
        assertEquals(3, p.size());
        for (RideMember m : p) assertFalse(m.getUserId().equals("alice"));
    }

    @Test
    public void monthlySummaryCountsCompletedOnly() {
        Results<MonthlySummary> r = new Results<>();
        history.getMonthlySummary(YearMonth.of(2026, 10), r);
        MonthlySummary s = r.get();
        assertTrue(s.hasActivity());
        assertEquals(2, s.getCompletedRides());
        assertEquals(2000 + 1250, s.getTotalCostCents());
        assertEquals(1, s.getOrganizedCount());
        assertEquals(1, s.getJoinedCount());
    }

    @Test
    public void monthWithoutActivity() {
        Results<MonthlySummary> r = new Results<>();
        history.getMonthlySummary(YearMonth.of(2026, 8), r);
        assertFalse(r.get().hasActivity());
    }

    @Test
    public void upcomingAndPublicRides() {
        Results<List<Ride>> up = new Results<>();
        history.getUpcoming(up);
        assertEquals(List.of(upcoming.getRideId()), List.of(up.get().get(0).getRideId()));
        assertEquals(1, up.get().size());

        User alice = db.users.get("alice");
        alice.setPublicRideIds(List.of(octCompleted.getRideId(), upcoming.getRideId(), octCancelled.getRideId()));
        Results<List<Ride>> pub = new Results<>();
        history.getPublicRides(alice, pub);
        assertEquals(2, pub.get().size());
        assertEquals(upcoming.getRideId(), pub.get().get(0).getRideId());
    }
}
