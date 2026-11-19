
package com.team23.trojanrideshare.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.team23.trojanrideshare.domain.MonthlySummary;

import org.junit.Test;

import java.time.YearMonth;

import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.testutil.Fixtures;
import com.team23.trojanrideshare.domain.HistoryFilter;
import com.team23.trojanrideshare.domain.HistoryEntry;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;

public class RideHistoryServiceTest extends ServiceTestBase {

    // WB 5.4: A month with no ride activity

    @Test
    public void monthWithNoActivityReturnsEmptySummary() {
        as("alice");

        MonthlySummary summary = RideHistoryService.summarize(
                java.util.Collections.emptyList(),
                YearMonth.of(2026, 9),
                "alice"
        );

        assertEquals(0, summary.getCompletedRides());
        assertEquals(0, summary.getTotalCostCents());
        assertEquals(0, summary.getOrganizedCount());
        assertEquals(0, summary.getJoinedCount());
        assertTrue(summary.getRides().isEmpty());
        assertFalse(summary.hasActivity());
    }


    // WB 5.1: Monthly summary uses scheduled departure month

    @Test
    public void monthlySummaryUsesScheduledMonth() {
        as("alice");

        // Ride scheduled for October 31
        Ride ride = Fixtures.ride(
                "alice",
                Fixtures.USC,
                Fixtures.LAX,
                LocalDateTime.of(2026, 10, 31, 18, 0),
                4,
                "bob"
        );

        // The ride has been completed
        ride.setActivityStatus(ActivityStatus.COMPLETED);

        // Calculate October's summary
        MonthlySummary october = RideHistoryService.summarize(
                Collections.singletonList(ride),
                YearMonth.of(2026, 10),
                "alice"
        );

        // Calculate November's summary using the same ride
        MonthlySummary november = RideHistoryService.summarize(
                Collections.singletonList(ride),
                YearMonth.of(2026, 11),
                "alice"
        );

        // The ride must count toward October
        assertEquals(1, october.getCompletedRides());
        assertEquals(1, october.getOrganizedCount());
        assertEquals(1, october.getRides().size());

        // The ride must NOT count toward November
        assertEquals(0, november.getCompletedRides());
        assertEquals(0, november.getOrganizedCount());
        assertTrue(november.getRides().isEmpty());
        assertFalse(november.hasActivity());
    }


    // WB 5.2: Cancelled rides are excluded from monthly totals

    @Test
    public void cancelledRidesAreExcludedFromMonthlySummary() {
        as("alice");

        // Alice organizes a completed ride
        Ride organized = Fixtures.ride(
                "alice", Fixtures.USC, Fixtures.LAX,
                LocalDateTime.of(2026, 10, 10, 12, 0),
                4, "bob"
        );
        organized.setActivityStatus(ActivityStatus.COMPLETED);

        // Alice joins Bob's completed ride
        Ride joined = Fixtures.ride(
                "bob", Fixtures.USC, Fixtures.SANTA_MONICA,
                LocalDateTime.of(2026, 10, 15, 12, 0),
                4, "alice"
        );
        joined.setActivityStatus(ActivityStatus.COMPLETED);

        // Alice organizes a cancelled ride
        Ride cancelled = Fixtures.ride(
                "alice", Fixtures.USC, Fixtures.LAX,
                LocalDateTime.of(2026, 10, 20, 12, 0),
                4, "carol"
        );
        cancelled.setActivityStatus(ActivityStatus.CANCELLED);

        MonthlySummary summary = RideHistoryService.summarize(
                Arrays.asList(organized, joined, cancelled),
                YearMonth.of(2026, 10),
                "alice"
        );

        assertEquals(2, summary.getCompletedRides());
        assertEquals(1, summary.getOrganizedCount());
        assertEquals(1, summary.getJoinedCount());
        assertEquals(2, summary.getRides().size());

        // Cancelled rides must not contribute to total costs.
        assertEquals(0, summary.getTotalCostCents());

        assertTrue(summary.hasActivity());
    }


    // WB 5.3: History filtering by status and participant

    @Test
    public void historyFiltersByStatusAndParticipant() {
        as("alice");

        // Ride A: Completed, Alice and Bob
        Ride rideA = Fixtures.ride(
                "alice", Fixtures.USC, Fixtures.LAX,
                LocalDateTime.of(2026, 10, 10, 12, 0),
                4, "bob"
        );
        rideA.setActivityStatus(ActivityStatus.COMPLETED);

        // Ride B: Cancelled, Alice and Carol
        Ride rideB = Fixtures.ride(
                "alice", Fixtures.USC, Fixtures.SANTA_MONICA,
                LocalDateTime.of(2026, 10, 12, 12, 0),
                4, "carol"
        );
        rideB.setActivityStatus(ActivityStatus.CANCELLED);

        // Ride C: Completed, Bob and Alice
        Ride rideC = Fixtures.ride(
                "bob", Fixtures.USC, Fixtures.UNION,
                LocalDateTime.of(2026, 10, 15, 12, 0),
                4, "alice"
        );
        rideC.setActivityStatus(ActivityStatus.COMPLETED);

        // Ride D: Completed, Alice and Dave
        Ride rideD = Fixtures.ride(
                "alice", Fixtures.USC, Fixtures.LAX,
                LocalDateTime.of(2026, 10, 20, 12, 0),
                4, "dave"
        );
        rideD.setActivityStatus(ActivityStatus.COMPLETED);

        List<Ride> allRides = Arrays.asList(
                rideA, rideB, rideC, rideD
        );

        // Test 1: Completed filter
        HistoryFilter completedFilter = new HistoryFilter();
        completedFilter.setStatus(ActivityStatus.COMPLETED);

        List<HistoryEntry> completed =
                RideHistoryService.filter(
                        allRides, completedFilter, "alice"
                );

        assertEquals(3, completed.size());
        assertEquals(rideD, completed.get(0).getRide());
        assertEquals(rideC, completed.get(1).getRide());
        assertEquals(rideA, completed.get(2).getRide());

        // Test 2: Cancelled filter
        HistoryFilter cancelledFilter = new HistoryFilter();
        cancelledFilter.setStatus(ActivityStatus.CANCELLED);

        List<HistoryEntry> cancelled =
                RideHistoryService.filter(
                        allRides, cancelledFilter, "alice"
                );

        assertEquals(1, cancelled.size());
        assertEquals(rideB, cancelled.get(0).getRide());

        // Test 3: Participant filter (Bob)
        HistoryFilter participantFilter = new HistoryFilter();
        participantFilter.setParticipantId("bob");

        List<HistoryEntry> withBob =
                RideHistoryService.filter(
                        allRides, participantFilter, "alice"
                );

        assertEquals(2, withBob.size());
        assertEquals(rideC, withBob.get(0).getRide());
        assertEquals(rideA, withBob.get(1).getRide());

        // Test 4: Combined status and participant filter
        HistoryFilter combinedFilter = new HistoryFilter();
        combinedFilter.setStatus(ActivityStatus.CANCELLED);
        combinedFilter.setParticipantId("bob");

        List<HistoryEntry> combined =
                RideHistoryService.filter(
                        allRides, combinedFilter, "alice"
                );

        assertTrue(combined.isEmpty());
    }


    // WB 5.5: Historical origin/destination search and radius

    @Test
    public void historySearchFiltersBothLocationsByRadius() {
        as("alice");

        Ride rideA = Fixtures.ride(
                "alice", Fixtures.USC, Fixtures.LAX,
                LocalDateTime.of(2026, 10, 10, 12, 0),
                4, "bob"
        );
        rideA.setActivityStatus(ActivityStatus.COMPLETED);

        Ride rideB = Fixtures.ride(
                "alice", Fixtures.USC_NEARBY, Fixtures.LAX,
                LocalDateTime.of(2026, 10, 12, 12, 0),
                4, "carol"
        );
        rideB.setActivityStatus(ActivityStatus.COMPLETED);

        Ride rideC = Fixtures.ride(
                "alice", Fixtures.UNION, Fixtures.LAX,
                LocalDateTime.of(2026, 10, 15, 12, 0),
                4, "bob"
        );
        rideC.setActivityStatus(ActivityStatus.COMPLETED);

        Ride rideD = Fixtures.ride(
                "alice", Fixtures.USC, Fixtures.SANTA_MONICA,
                LocalDateTime.of(2026, 10, 20, 12, 0),
                4, "dave"
        );
        rideD.setActivityStatus(ActivityStatus.COMPLETED);

        HistoryFilter filter = new HistoryFilter();
        filter.setOrigin(Fixtures.USC);
        filter.setDestination(Fixtures.LAX);
        filter.setRadiusMiles(2.0);

        List<HistoryEntry> results = RideHistoryService.filter(
                Arrays.asList(rideA, rideB, rideC, rideD),
                filter,
                "alice"
        );

        // Only A and B should match both endpoints.
        assertEquals(2, results.size());

        // Results should be newest first by default.
        assertEquals(rideB, results.get(0).getRide());
        assertEquals(rideA, results.get(1).getRide());
    }

}
