package com.team23.trojanrideshare.domain.util;

import java.util.regex.Pattern;

/**
 * Logging that never writes an email address or a USC ID in plaintext (NFR-2).
 * The app plugs in an Android Log sink at startup; tests use the default stdout sink.
 */
public final class SafeLogger {

    public interface Sink {
        void write(String message);
    }

    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+)");
    private static final Pattern TEN_DIGITS = Pattern.compile("(?<!\\d)\\d{10}(?!\\d)");

    private static volatile Sink sink = System.out::println;

    private SafeLogger() {
    }

    public static void setSink(Sink s) {
        sink = s == null ? System.out::println : s;
    }

    public static void log(String msg) {
        sink.write(mask(msg));
    }

    /** "tommy@usc.edu id 1234567890" -> "t***@usc.edu id **********". */
    public static String mask(String msg) {
        if (msg == null) return "null";
        String out = EMAIL.matcher(msg).replaceAll("$1***@$2");
        return TEN_DIGITS.matcher(out).replaceAll("**********");
    }
}
