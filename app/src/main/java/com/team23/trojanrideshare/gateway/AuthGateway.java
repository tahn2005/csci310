package com.team23.trojanrideshare.gateway;

import com.team23.trojanrideshare.domain.Callback;

/** Wraps the authentication service: credentials are never stored by the app itself. */
public interface AuthGateway {

    /** Creates the account and signs it in. Returns the new user id. */
    void createAccount(String email, String password, Callback<String> cb);

    /** Returns the signed-in user id. */
    void signIn(String email, String password, Callback<String> cb);

    void signOut();

    /** Deletes the signed-in account (used to undo an incomplete sign-up). */
    void deleteAccount(Callback<Void> cb);

    /** The signed-in user id, or null. */
    String currentUserId();
}
