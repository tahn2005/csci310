package com.team23.trojanrideshare.domain;

/** One row of the Ride History page: the ride and the logged-in user's share (FR-5.2). */
public class HistoryEntry {

    private final Ride ride;
    private final CostShare myShare;

    public HistoryEntry(Ride ride, CostShare myShare) {
        this.ride = ride;
        this.myShare = myShare;
    }

    public Ride getRide() {
        return ride;
    }

    /** Null unless the ride is Completed. */
    public CostShare getMyShare() {
        return myShare;
    }
}
