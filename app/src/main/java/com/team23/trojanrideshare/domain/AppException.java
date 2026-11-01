package com.team23.trojanrideshare.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single exception type that crosses layer boundaries. Services never expose
 * Firebase exception types; repositories and gateways translate them into an AppException.
 */
public class AppException extends Exception {

    private final ErrorCode code;
    private final Map<String, String> fieldErrors;

    public AppException(ErrorCode code, String message) {
        this(code, message, Collections.emptyMap(), null);
    }

    public AppException(ErrorCode code, String message, Throwable cause) {
        this(code, message, Collections.emptyMap(), cause);
    }

    public AppException(ErrorCode code, String message, Map<String, String> fieldErrors, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    /** A VALIDATION error carrying one message per invalid form field. */
    public static AppException validation(Map<String, String> fieldErrors) {
        String first = fieldErrors.isEmpty() ? "Please fix the highlighted fields."
                : fieldErrors.values().iterator().next();
        return new AppException(ErrorCode.VALIDATION, first, fieldErrors, null);
    }

    public static AppException validation(String message) {
        return new AppException(ErrorCode.VALIDATION, message);
    }

    public static AppException notAllowed(String message) {
        return new AppException(ErrorCode.NOT_ALLOWED, message);
    }

    public static AppException conflict(String message) {
        return new AppException(ErrorCode.CONFLICT, message);
    }

    public static AppException offline() {
        return new AppException(ErrorCode.OFFLINE,
                "Couldn't reach the server. Check your connection and try again.");
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
