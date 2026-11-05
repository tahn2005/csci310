package com.team23.trojanrideshare.gateway;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.util.SafeLogger;

import java.io.IOException;

/** Firebase Authentication stores passwords as salted one-way hashes (NFR-2). */
public class FirebaseAuthGateway implements AuthGateway {

    private final FirebaseAuth auth;
    private final String offlineMessage;

    /** @param offlineMessage what to tell the user when the server can't be reached. */
    public FirebaseAuthGateway(FirebaseAuth auth, String offlineMessage) {
        this.auth = auth;
        this.offlineMessage = offlineMessage;
    }

    @Override
    public void createAccount(String email, String password, Callback<String> cb) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> cb.onSuccess(r.getUser().getUid()))
                .addOnFailureListener(e -> cb.onError(map(e, true)));
    }

    @Override
    public void signIn(String email, String password, Callback<String> cb) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> cb.onSuccess(r.getUser().getUid()))
                .addOnFailureListener(e -> cb.onError(map(e, false)));
    }

    @Override
    public void signOut() {
        auth.signOut();
    }

    @Override
    public void deleteAccount(Callback<Void> cb) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            cb.onSuccess(null);
            return;
        }
        u.delete()
                .addOnSuccessListener(v -> {
                    auth.signOut();
                    cb.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    auth.signOut();
                    cb.onError(map(e, false));
                });
    }

    /**
     * The Auth SDK reports some unreachable-server cases as a generic FirebaseException
     * ("An internal error has occurred. [ Failed to connect to /10.0.2.2:9099 ]").
     */
    private static boolean isConnectionFailure(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (t instanceof IOException) return true;
            if (m != null && (m.contains("Failed to connect") || m.contains("Unable to resolve host")
                    || m.contains("timeout") || m.contains("Connection refused"))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String currentUserId() {
        FirebaseUser u = auth.getCurrentUser();
        return u == null ? null : u.getUid();
    }

    private AppException map(Exception e, boolean signingUp) {
        SafeLogger.log("Auth error: " + e);
        if (e instanceof FirebaseAuthUserCollisionException) {
            return new AppException(ErrorCode.DUPLICATE, "An account with this email already exists.", e);
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return new AppException(ErrorCode.VALIDATION, "Password must be at least 6 characters.", e);
        }
        if (e instanceof FirebaseAuthInvalidUserException
                || (!signingUp && e instanceof FirebaseAuthInvalidCredentialsException)) {
            return new AppException(ErrorCode.NOT_ALLOWED, "Incorrect email or password.", e);
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return new AppException(ErrorCode.VALIDATION, "Enter a valid USC email address.", e);
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return new AppException(ErrorCode.NOT_ALLOWED, "Too many attempts. Please try again later.", e);
        }
        if (e instanceof FirebaseNetworkException || isConnectionFailure(e)) {
            return new AppException(ErrorCode.OFFLINE, offlineMessage, e);
        }
        return new AppException(ErrorCode.UNKNOWN, "Something went wrong. Please try again.", e);
    }
}
