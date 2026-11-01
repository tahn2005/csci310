package com.team23.trojanrideshare.domain;

/** Search, filter, and sort choices on the Ride History page (FR-5.3 – FR-5.5). */
public class HistoryFilter {

    private Place origin;
    private Place destination;
    private double radiusMiles = SearchCriteria.DEFAULT_RADIUS;
    /** Completed or Cancelled; null shows both. */
    private ActivityStatus status;
    /** Another rider who was in the ride; null shows all rides. */
    private String participantId;
    private boolean newestFirst = true;

    public boolean hasLocation() {
        return origin != null || destination != null;
    }

    public Place getOrigin() {
        return origin;
    }

    public void setOrigin(Place origin) {
        this.origin = origin;
    }

    public Place getDestination() {
        return destination;
    }

    public void setDestination(Place destination) {
        this.destination = destination;
    }

    public double getRadiusMiles() {
        return radiusMiles;
    }

    public void setRadiusMiles(double radiusMiles) {
        this.radiusMiles = Math.max(SearchCriteria.MIN_RADIUS,
                Math.min(SearchCriteria.MAX_RADIUS, radiusMiles));
    }

    public ActivityStatus getStatus() {
        return status;
    }

    public void setStatus(ActivityStatus status) {
        this.status = status;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public boolean isNewestFirst() {
        return newestFirst;
    }

    public void setNewestFirst(boolean newestFirst) {
        this.newestFirst = newestFirst;
    }
}
