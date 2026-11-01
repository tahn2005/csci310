package com.team23.trojanrideshare.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A shared-ride post (FR-2). Its riders, pending requests, and cost shares are part of it. */
public class Ride {

    public static final int MIN_RIDERS = 2;
    public static final int MAX_RIDERS = 6;

    private String rideId;
    private String title;
    private String organizerId;
    private String organizerName;
    private Place origin;
    private Place destination;
    private LocalDateTime departure;
    private int maxRiders;
    private long expectedCostCents;
    private long actualCostCents;
    private String note;
    private ActivityStatus activityStatus = ActivityStatus.ON_TIME;
    private CapacityStatus capacityStatus = CapacityStatus.OPEN;
    private List<RideMember> members = new ArrayList<>();
    /** Pending join requests: requester's user id -> request id. A user has at most one. */
    private Map<String, String> pendingRequests = new LinkedHashMap<>();
    /** Cost shares once the ride is Completed, keyed by user id, in rider order. */
    private Map<String, CostShare> shares = new LinkedHashMap<>();
    private String splitMethod;
    private Instant createdAt;
    private Instant completedAt;

    // ---------------------------------------------------------------- behaviour

    public int riderCount() {
        return members.size();
    }

    public int seatsLeft() {
        return Math.max(0, maxRiders - riderCount());
    }

    public boolean isOrganizer(String uid) {
        return uid != null && uid.equals(organizerId);
    }

    public boolean hasMember(String uid) {
        return member(uid) != null;
    }

    public RideMember member(String uid) {
        if (uid == null) return null;
        for (RideMember m : members) {
            if (uid.equals(m.getUserId())) return m;
        }
        return null;
    }

    public List<String> memberIds() {
        List<String> ids = new ArrayList<>();
        for (RideMember m : members) ids.add(m.getUserId());
        return ids;
    }

    /** On-Time or Delayed: the ride is still going to happen. */
    public boolean isActive() {
        return activityStatus != null && activityStatus.isActive();
    }

    public boolean isFull() {
        return riderCount() >= maxRiders;
    }

    public boolean hasDeparted(LocalDateTime now) {
        return departure != null && !departure.isAfter(now);
    }

    /** FR-2.6: Full when riders reach the maximum, Open otherwise, N/A once Completed. */
    public void updateCapacity() {
        if (activityStatus == ActivityStatus.COMPLETED) {
            capacityStatus = CapacityStatus.NOT_APPLICABLE;
        } else {
            capacityStatus = isFull() ? CapacityStatus.FULL : CapacityStatus.OPEN;
        }
    }

    /** e.g. "Open – 2 of 4 riders", "Full – 4 of 4 riders", "N/A – 3 riders". */
    public String capacityLabel() {
        if (capacityStatus == CapacityStatus.NOT_APPLICABLE) {
            return "N/A – " + riderCount() + (riderCount() == 1 ? " rider" : " riders");
        }
        return capacityStatus.label() + " – " + riderCount() + " of " + maxRiders + " riders";
    }

    public boolean hasPendingRequestFrom(String uid) {
        return uid != null && pendingRequests.containsKey(uid);
    }

    public String pendingRequestIdOf(String uid) {
        return uid == null ? null : pendingRequests.get(uid);
    }

    public int pendingCount() {
        return pendingRequests.size();
    }

    /**
     * FR-3.10.2: the accepted rider (other than {@code leavingUid}) with the most recent
     * acceptance time. Returns null if nobody else is in the ride.
     */
    public RideMember successorAfter(String leavingUid) {
        RideMember best = null;
        for (RideMember m : members) {
            if (m.getUserId().equals(leavingUid)) continue;
            if (best == null) {
                best = m;
            } else {
                Instant a = m.getAcceptedAt();
                Instant b = best.getAcceptedAt();
                if (a != null && (b == null || a.isAfter(b))) best = m;
            }
        }
        return best;
    }

    public CostShare shareOf(String uid) {
        return uid == null ? null : shares.get(uid);
    }

    public List<CostShare> getShares() {
        return new ArrayList<>(shares.values());
    }

    public void setShares(List<CostShare> list) {
        shares = new LinkedHashMap<>();
        if (list != null) {
            for (CostShare s : list) shares.put(s.getUserId(), s);
        }
    }

    // ---------------------------------------------------------------- accessors

    public String getRideId() {
        return rideId;
    }

    public void setRideId(String rideId) {
        this.rideId = rideId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public String getOrganizerName() {
        return organizerName;
    }

    public void setOrganizerName(String organizerName) {
        this.organizerName = organizerName;
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

    public LocalDateTime getDeparture() {
        return departure;
    }

    public void setDeparture(LocalDateTime departure) {
        this.departure = departure;
    }

    public int getMaxRiders() {
        return maxRiders;
    }

    public void setMaxRiders(int maxRiders) {
        this.maxRiders = maxRiders;
    }

    public long getExpectedCostCents() {
        return expectedCostCents;
    }

    public void setExpectedCostCents(long expectedCostCents) {
        this.expectedCostCents = expectedCostCents;
    }

    public long getActualCostCents() {
        return actualCostCents;
    }

    public void setActualCostCents(long actualCostCents) {
        this.actualCostCents = actualCostCents;
    }

    public String getNote() {
        return note == null ? "" : note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public ActivityStatus getActivityStatus() {
        return activityStatus;
    }

    public void setActivityStatus(ActivityStatus activityStatus) {
        this.activityStatus = activityStatus;
    }

    public CapacityStatus getCapacityStatus() {
        return capacityStatus;
    }

    public void setCapacityStatus(CapacityStatus capacityStatus) {
        this.capacityStatus = capacityStatus;
    }

    public List<RideMember> getMembers() {
        return members;
    }

    public void setMembers(List<RideMember> members) {
        this.members = members == null ? new ArrayList<>() : new ArrayList<>(members);
    }

    public Map<String, String> getPendingRequests() {
        return pendingRequests;
    }

    public void setPendingRequests(Map<String, String> pendingRequests) {
        this.pendingRequests = pendingRequests == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(pendingRequests);
    }

    public Map<String, CostShare> getShareMap() {
        return Collections.unmodifiableMap(shares);
    }

    public String getSplitMethod() {
        return splitMethod;
    }

    public void setSplitMethod(String splitMethod) {
        this.splitMethod = splitMethod;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
