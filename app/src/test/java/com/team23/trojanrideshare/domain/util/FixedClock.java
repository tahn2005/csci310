package com.team23.trojanrideshare.domain.util;

import java.time.Duration;
import java.time.LocalDateTime;

/** A clock that only moves when told to. Used by unit tests. */
public class FixedClock implements Clock {

    private LocalDateTime now;

    public FixedClock(LocalDateTime now) {
        this.now = now;
    }

    @Override
    public LocalDateTime now() {
        return now;
    }

    public void set(LocalDateTime now) {
        this.now = now;
    }

    public void advance(Duration d) {
        now = now.plus(d);
    }
}
