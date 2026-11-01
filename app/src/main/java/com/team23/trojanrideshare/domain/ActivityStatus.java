package com.team23.trojanrideshare.domain;

/** Whether a ride is still going to happen (FR-2, "activity status"). */
public enum ActivityStatus {
    ON_TIME("On-Time"),
    DELAYED("Delayed"),
    CANCELLED("Cancelled"),
    COMPLETED("Completed");

    private final String label;

    ActivityStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** On-Time and Delayed rides are still going to happen. */
    public boolean isActive() {
        return this == ON_TIME || this == DELAYED;
    }

    public static ActivityStatus fromName(String name) {
        if (name == null) return ON_TIME;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return ON_TIME;
        }
    }
}
