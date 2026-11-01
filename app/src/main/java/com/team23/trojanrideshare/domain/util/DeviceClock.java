package com.team23.trojanrideshare.domain.util;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * The device's local clock. An optional offset lets a demo jump forward in time
 * (e.g. to mark a ride Completed right after creating it); it is zero in normal use.
 */
public class DeviceClock implements Clock {

    private volatile Duration offset = Duration.ZERO;

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now().plus(offset);
    }

    public Duration getOffset() {
        return offset;
    }

    public void setOffset(Duration offset) {
        this.offset = offset == null ? Duration.ZERO : offset;
    }
}
