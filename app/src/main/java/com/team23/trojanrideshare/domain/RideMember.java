package com.team23.trojanrideshare.domain;

import java.time.Instant;

/** A rider in a ride: the organizer or an accepted rider (glossary: "Rider"). */
public class RideMember {

    private String userId;
    private String displayName;
    /** When the member's request was accepted; the organizer's is the ride's creation time. */
    private Instant acceptedAt;

    public RideMember() {
    }

    public RideMember(String userId, String displayName, Instant acceptedAt) {
        this.userId = userId;
        this.displayName = displayName;
        this.acceptedAt = acceptedAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
}
