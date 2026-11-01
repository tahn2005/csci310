package com.team23.trojanrideshare.domain;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Monthly Ride Summary for one month and year (FR-5.7). Cancelled rides are excluded. */
public class MonthlySummary {

    private final YearMonth month;
    private final int completedRides;
    private final long totalCostCents;
    private final int organizedCount;
    private final int joinedCount;
    private final List<HistoryEntry> rides;

    public MonthlySummary(YearMonth month, int completedRides, long totalCostCents,
                          int organizedCount, int joinedCount, List<HistoryEntry> rides) {
        this.month = month;
        this.completedRides = completedRides;
        this.totalCostCents = totalCostCents;
        this.organizedCount = organizedCount;
        this.joinedCount = joinedCount;
        this.rides = rides == null ? new ArrayList<>() : rides;
    }

    /** FR-5.9: false means "No activity! Please select another month." */
    public boolean hasActivity() {
        return completedRides > 0;
    }

    public YearMonth getMonth() {
        return month;
    }

    public int getCompletedRides() {
        return completedRides;
    }

    public long getTotalCostCents() {
        return totalCostCents;
    }

    public int getOrganizedCount() {
        return organizedCount;
    }

    public int getJoinedCount() {
        return joinedCount;
    }

    /** The Completed rides counted in this summary, oldest first. */
    public List<HistoryEntry> getRides() {
        return rides;
    }
}
