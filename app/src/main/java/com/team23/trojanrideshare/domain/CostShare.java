package com.team23.trojanrideshare.domain;

import java.time.Instant;

/** One rider's assigned part of a completed ride's actual cost (FR-4). */
public class CostShare {

    private String rideId;
    private String userId;
    private String displayName;
    private long amountCents;
    private PaymentStatus status = PaymentStatus.UNPAID;
    private Instant paidAt;

    public CostShare() {
    }

    public CostShare(String rideId, String userId, String displayName, long amountCents) {
        this.rideId = rideId;
        this.userId = userId;
        this.displayName = displayName;
        this.amountCents = amountCents;
    }

    public CostShare copy() {
        CostShare c = new CostShare(rideId, userId, displayName, amountCents);
        c.status = status;
        c.paidAt = paidAt;
        return c;
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(long amountCents) {
        this.amountCents = amountCents;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public boolean isPaid() {
        return status == PaymentStatus.PAID;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }
}
