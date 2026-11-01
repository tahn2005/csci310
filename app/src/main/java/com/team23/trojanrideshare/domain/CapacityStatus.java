package com.team23.trojanrideshare.domain;

/** Whether a ride can accept more riders (FR-2, "capacity status"). */
public enum CapacityStatus {
    OPEN("Open"),
    FULL("Full"),
    NOT_APPLICABLE("N/A");

    private final String label;

    CapacityStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static CapacityStatus fromName(String name) {
        if (name == null) return OPEN;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return OPEN;
        }
    }
}
