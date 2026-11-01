package com.team23.trojanrideshare.domain;

/** Status of a join request (FR-3.6.5). */
public enum RequestStatus {
    PENDING("Pending"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static RequestStatus fromName(String name) {
        if (name == null) return PENDING;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
