package com.team23.trojanrideshare.domain.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.ErrorCode;

import org.junit.Test;

public class MoneyTest {

    @Test
    public void parsesCommonFormats() throws AppException {
        assertEquals(1200, Money.parse("12"));
        assertEquals(1250, Money.parse("12.5"));
        assertEquals(1250, Money.parse("12.50"));
        assertEquals(1250, Money.parse("$12.50"));
        assertEquals(120000, Money.parse("1,200.00"));
        assertEquals(5, Money.parse(".05"));
        assertEquals(0, Money.parse("0"));
    }

    @Test
    public void rejectsMoreThanTwoDecimals() {
        expectInvalid("12.345");
    }

    @Test
    public void rejectsGarbageBlankAndNegative() {
        expectInvalid("");
        expectInvalid("   ");
        expectInvalid("abc");
        expectInvalid("1.2.3");
        expectInvalid("-5");
        expectInvalid(".");
    }

    @Test
    public void formatsDollars() {
        assertEquals("$0.00", Money.format(0));
        assertEquals("$3.34", Money.format(334));
        assertEquals("$1,234.56", Money.format(123456));
        assertEquals("-$1.00", Money.format(-100));
        assertEquals("12.50", Money.plain(1250));
    }

    private static void expectInvalid(String text) {
        try {
            Money.parse(text);
            fail("Expected '" + text + "' to be rejected");
        } catch (AppException e) {
            assertEquals(ErrorCode.VALIDATION, e.getCode());
        }
    }
}
