package com.team23.trojanrideshare.data.firestore;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.util.SafeLogger;

/** Translates Firebase exceptions into {@link AppException}s so services never see Firebase types. */
final class FirestoreErrors {

    /** Carries a business-rule failure out of a Firestore transaction function. */
    static final class TxAbort extends RuntimeException {
        final AppException error;

        TxAbort(AppException error) {
            super(error.getMessage());
            this.error = error;
        }
    }

    private FirestoreErrors() {
    }

    static AppException map(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof TxAbort) return ((TxAbort) t).error;
            if (t instanceof AppException) return (AppException) t;
        }
        SafeLogger.log("Firestore error: " + e);
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof FirebaseNetworkException) return AppException.offline();
            if (t instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) t).getCode();
                switch (code) {
                    case UNAVAILABLE:
                    case DEADLINE_EXCEEDED:
                        return AppException.offline();
                    case PERMISSION_DENIED:
                    case UNAUTHENTICATED:
                        return new AppException(ErrorCode.NOT_ALLOWED,
                                "You don't have permission to do that.", e);
                    case NOT_FOUND:
                        return new AppException(ErrorCode.NOT_FOUND, "That item no longer exists.", e);
                    case ABORTED:
                    case FAILED_PRECONDITION:
                        return new AppException(ErrorCode.CONFLICT,
                                "The data changed while you were working. Please refresh and try again.", e);
                    default:
                        break;
                }
            }
        }
        return new AppException(ErrorCode.UNKNOWN, "Something went wrong. Please try again.", e);
    }
}
