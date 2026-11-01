package com.team23.trojanrideshare.domain;

/** Whether a rider has reimbursed the organizer for their share (FR-4.6). */
public enum PaymentStatus {
    UNPAID("Unpaid"),
    PAID("Paid");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static PaymentStatus fromName(String name) {
        if (name == null) return UNPAID;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return UNPAID;
        }
    }
}
