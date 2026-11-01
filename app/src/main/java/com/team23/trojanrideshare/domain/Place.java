package com.team23.trojanrideshare.domain;

import java.util.Objects;

/** A location chosen from the location API's suggestions (FR-2.2.2). */
public class Place {

    private String placeId;
    private String name;
    private String address;
    private double lat = Double.NaN;
    private double lng = Double.NaN;

    public Place() {
    }

    public Place(String placeId, String name, String address, double lat, double lng) {
        this.placeId = placeId;
        this.name = name;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
    }

    public String getPlaceId() {
        return placeId;
    }

    public void setPlaceId(String placeId) {
        this.placeId = placeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Secondary line such as "Los Angeles, CA". May be empty. */
    public String getAddress() {
        return address == null ? "" : address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public boolean hasCoordinates() {
        return !Double.isNaN(lat) && !Double.isNaN(lng);
    }

    /** One-line label used in suggestion lists and ride cards. */
    public String label() {
        String a = getAddress();
        return a.isEmpty() ? name : name + ", " + a;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Place)) return false;
        return Objects.equals(placeId, ((Place) o).placeId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(placeId);
    }

    @Override
    public String toString() {
        return label();
    }
}
