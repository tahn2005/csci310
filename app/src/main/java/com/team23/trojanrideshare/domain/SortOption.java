package com.team23.trojanrideshare.domain;

/** Sort orders for the Ride Search page (FR-3.4). */
public enum SortOption {
    TIME_EARLIEST("Departure: earliest first"),
    TIME_LATEST("Departure: latest first"),
    PROXIMITY("Proximity"),
    PROXIMITY_THEN_TIME("Proximity, then time");

    private final String label;

    SortOption(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean needsLocation() {
        return this == PROXIMITY || this == PROXIMITY_THEN_TIME;
    }
}
