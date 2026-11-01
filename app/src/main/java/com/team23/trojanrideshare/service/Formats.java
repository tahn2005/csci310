package com.team23.trojanrideshare.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Date formats used in service messages. */
final class Formats {

    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", Locale.US);

    private Formats() {
    }

    static String dateTime(LocalDateTime t) {
        return t == null ? "" : DATE_TIME.format(t);
    }
}
