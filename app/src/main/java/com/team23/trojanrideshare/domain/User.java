package com.team23.trojanrideshare.domain;

import java.util.ArrayList;
import java.util.List;

/** A registered USC student (FR-1). Email and USC ID never change after registration. */
public class User {

    private String uid;
    private String fullName;
    private String nickname;
    private String email;
    private String uscId;
    private int gradYear;
    private List<String> publicRideIds = new ArrayList<>();

    public User() {
    }

    public User(String fullName, String nickname, String email, String uscId, int gradYear) {
        this.fullName = fullName;
        this.nickname = nickname;
        this.email = email;
        this.uscId = uscId;
        this.gradYear = gradYear;
    }

    /** FR-1.2.2: the nickname, or the full name if no nickname was given. */
    public String getDisplayName() {
        if (nickname == null || nickname.trim().isEmpty()) return fullName;
        return nickname.trim();
    }

    public boolean isRidePublic(String rideId) {
        return publicRideIds.contains(rideId);
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUscId() {
        return uscId;
    }

    public void setUscId(String uscId) {
        this.uscId = uscId;
    }

    public int getGradYear() {
        return gradYear;
    }

    public void setGradYear(int gradYear) {
        this.gradYear = gradYear;
    }

    public List<String> getPublicRideIds() {
        return publicRideIds;
    }

    public void setPublicRideIds(List<String> publicRideIds) {
        this.publicRideIds = publicRideIds == null ? new ArrayList<>() : new ArrayList<>(publicRideIds);
    }
}
