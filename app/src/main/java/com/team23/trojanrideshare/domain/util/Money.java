package com.team23.trojanrideshare.domain.util;

import com.team23.trojanrideshare.domain.AppException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * US-dollar amounts are stored as whole cents (long) so that cost shares always add up
 * exactly to the total (FR-4.2).
 */
public final class Money {

    private static final Pattern AMOUNT = Pattern.compile("^(\\d{1,7})?(\\.(\\d*))?$");

    private Money() {
    }

    /**
     * Parses "12", "12.5", "12.50", "$12.50", or "1,200.00" into cents.
     *
     * @throws AppException VALIDATION if blank, not a number, negative, or with more than
     *                      two decimal places.
     */
    public static long parse(String text) throws AppException {
        if (text == null || text.trim().isEmpty()) {
            throw AppException.validation("Enter an amount in US dollars.");
        }
        String t = text.trim().replace(",", "");
        if (t.startsWith("$")) t = t.substring(1).trim();
        if (t.startsWith("-")) {
            throw AppException.validation("The amount can't be negative.");
        }
        java.util.regex.Matcher m = AMOUNT.matcher(t);
        if (t.isEmpty() || t.equals(".") || !m.matches()) {
            throw AppException.validation("Enter a valid dollar amount, like 12.50.");
        }
        String whole = m.group(1) == null ? "0" : m.group(1);
        String frac = m.group(3) == null ? "" : m.group(3);
        if (frac.length() > 2) {
            throw AppException.validation("Use at most two decimal places.");
        }
        while (frac.length() < 2) frac = frac + "0";
        return Long.parseLong(whole) * 100 + Long.parseLong(frac);
    }

    /** 1234567 -> "$12,345.67". */
    public static String format(long cents) {
        String sign = cents < 0 ? "-" : "";
        long abs = Math.abs(cents);
        return sign + String.format(Locale.US, "$%,d.%02d", abs / 100, abs % 100);
    }

    /** 1250 -> "12.50", for pre-filling an input field. */
    public static String plain(long cents) {
        return String.format(Locale.US, "%d.%02d", cents / 100, Math.abs(cents % 100));
    }
}
