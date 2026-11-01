package com.team23.trojanrideshare.ui.common;

import com.team23.trojanrideshare.domain.Place;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Date, time, and place formatting for screens. */
public final class UiFormat {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US);

    private UiFormat() {
    }

    public static String date(LocalDate d) {
        return d == null ? "" : DATE.format(d);
    }

    public static String shortDate(LocalDate d) {
        return d == null ? "" : SHORT_DATE.format(d);
    }

    public static String time(LocalTime t) {
        return t == null ? "" : TIME.format(t);
    }

    /** "Sat, Nov 22, 2026 · 3:00 PM". */
    public static String dateTime(LocalDateTime t) {
        return t == null ? "" : DATE.format(t) + " · " + TIME.format(t);
    }

    public static String stamp(Instant i) {
        return i == null ? "" : STAMP.format(LocalDateTime.ofInstant(i, ZoneId.systemDefault()));
    }

    public static String placeName(Place p) {
        return p == null ? "?" : p.getName();
    }

    /** "USC Village → LAX". */
    public static String route(Place from, Place to) {
        return placeName(from) + "  →  " + placeName(to);
    }

    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        String s = parts[0].substring(0, 1);
        if (parts.length > 1) s += parts[parts.length - 1].substring(0, 1);
        return s.toUpperCase(Locale.US);
    }

    public static String hours(double h) {
        return (h == Math.floor(h) ? String.valueOf((int) h) : String.valueOf(h)) + (h == 1 ? " hour" : " hours");
    }

    public static String miles(double m) {
        return (m == Math.floor(m) ? String.valueOf((int) m) : String.valueOf(m)) + " mi";
    }
}
