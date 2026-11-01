package com.team23.trojanrideshare.domain;

/** Categories of failures that services report to the Presentation layer. */
public enum ErrorCode {
    /** Input breaks a rule (e.g. bad email, cost of $0). */
    VALIDATION,
    /** The user may not perform the action (e.g. not the organizer, ride is full). */
    NOT_ALLOWED,
    /** The data changed underneath the user (e.g. request already answered). */
    CONFLICT,
    /** The account, email, or USC ID already exists. */
    DUPLICATE,
    /** The central database or a cloud API is unreachable. */
    OFFLINE,
    /** The document no longer exists. */
    NOT_FOUND,
    /** Anything else. */
    UNKNOWN
}
