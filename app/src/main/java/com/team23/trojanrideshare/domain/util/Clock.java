package com.team23.trojanrideshare.domain.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Source of "now". Every time-based rule reads the time from here, so it can be controlled in one place. */
public interface Clock {

    LocalDateTime now();

    default Instant instant() {
        return now().atZone(ZoneId.systemDefault()).toInstant();
    }
}
