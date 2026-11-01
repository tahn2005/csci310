package com.team23.trojanrideshare.domain;

import java.time.Instant;

/** A user's request to join a ride (FR-3.6). */
public class JoinRequest {

    public static final int MAX_MESSAGE_LENGTH = 200;

    private String requestId;
    private String rideId;
    private String requesterId;
    private String requesterName;
    /** The ride's current organizer, who is allowed to answer the request. */
    private String organizerId;
    private String message;
    private Instant createdAt;
    private Instant respondedAt;
    private RequestStatus status = RequestStatus.PENDING;

    /** Not stored: the ride, attached by the service for list screens (FR-3.7.2). */
    private transient Ride ride;

    public JoinRequest() {
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getRideId() {
        return rideId;
    }

    public void setRideId(String rideId) {
        this.rideId = rideId;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(String requesterId) {
        this.requesterId = requesterId;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public void setRequesterName(String requesterName) {
        this.requesterName = requesterName;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public String getMessage() {
        return message == null ? "" : message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public Ride getRide() {
        return ride;
    }

    public void setRide(Ride ride) {
        this.ride = ride;
    }
}
