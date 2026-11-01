package com.team23.trojanrideshare.domain.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class SafeLoggerTest {

    @Test
    public void masksEmailsAndUscIds() {
        String out = SafeLogger.mask("Signed up tommy.trojan@usc.edu with id 1234567890");
        assertFalse(out.contains("tommy.trojan"));
        assertFalse(out.contains("1234567890"));
        assertEquals("Signed up t***@usc.edu with id **********", out);
    }

    @Test
    public void leavesOtherNumbersAlone() {
        assertEquals("ride 42 costs 4700 cents", SafeLogger.mask("ride 42 costs 4700 cents"));
    }
}
