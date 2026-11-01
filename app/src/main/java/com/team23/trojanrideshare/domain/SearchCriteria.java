package com.team23.trojanrideshare.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/** Everything the user has entered on the Ride Search page (FR-3.2 – FR-3.4). */
public class SearchCriteria {

    public static final double DEFAULT_RADIUS = 5;
    public static final double MIN_RADIUS = 0.5;
    public static final double MAX_RADIUS = 50;
    public static final double MIN_WINDOW_HOURS = 0.5;
    public static final double MAX_WINDOW_HOURS = 6;

    private Place origin;
    private Place destination;
    private double radiusMiles = DEFAULT_RADIUS;
    private LocalDate date;
    private LocalTime targetTime;
    private double windowHours = 1;
    private boolean seatsOnly = true;
    private SortOption sort = SortOption.TIME_EARLIEST;

    public boolean hasLocation() {
        return origin != null || destination != null;
    }

    public SearchCriteria copy() {
        SearchCriteria c = new SearchCriteria();
        c.origin = origin;
        c.destination = destination;
        c.radiusMiles = radiusMiles;
        c.date = date;
        c.targetTime = targetTime;
        c.windowHours = windowHours;
        c.seatsOnly = seatsOnly;
        c.sort = sort;
        return c;
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
        this.radiusMiles = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radiusMiles));
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getTargetTime() {
        return targetTime;
    }

    public void setTargetTime(LocalTime targetTime) {
        this.targetTime = targetTime;
    }

    public double getWindowHours() {
        return windowHours;
    }

    public void setWindowHours(double windowHours) {
        this.windowHours = Math.max(MIN_WINDOW_HOURS, Math.min(MAX_WINDOW_HOURS, windowHours));
    }

    public boolean isSeatsOnly() {
        return seatsOnly;
    }

    public void setSeatsOnly(boolean seatsOnly) {
        this.seatsOnly = seatsOnly;
    }

    public SortOption getSort() {
        return sort;
    }

    public void setSort(SortOption sort) {
        this.sort = sort == null ? SortOption.TIME_EARLIEST : sort;
    }
}
