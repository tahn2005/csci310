package com.team23.trojanrideshare.domain;

import java.time.Instant;

/**
 * Marker an organizer puts on a rider's unpaid share (FR-4.7). Visible to the organizer
 * and to the flagged rider (on their profile), never to other riders.
 */
public class NonPaymentFlag {

    private String rideId;
    private String userId;
    private String organizerId;
    private String rideTitle;
    private String userName;
    private String organizerName;
    private long amountCents;
    private Instant createdAt;

    public NonPaymentFlag() {
    }

    public NonPaymentFlag(String rideId, String userId, String organizerId) {
        this.rideId = rideId;
        this.userId = userId;
        this.organizerId = organizerId;
    }

    public String getRideId() {
        return rideId;
    }

    public void setRideId(String rideId) {
        this.rideId = rideId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public String getRideTitle() {
        return rideTitle;
    }

    public void setRideTitle(String rideTitle) {
        this.rideTitle = rideTitle;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getOrganizerName() {
        return organizerName;
    }

    public void setOrganizerName(String organizerName) {
        this.organizerName = organizerName;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(long amountCents) {
        this.amountCents = amountCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
