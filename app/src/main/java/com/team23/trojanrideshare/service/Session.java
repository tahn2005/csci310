package com.team23.trojanrideshare.service;

import com.team23.trojanrideshare.domain.User;

/** The signed-in user's profile, shared by all services. Set by {@link AccountService}. */
public class Session {

    private volatile User user;

    public User user() {
        return user;
    }

    public String uid() {
        User u = user;
        return u == null ? null : u.getUid();
    }

    public String displayName() {
        User u = user;
        return u == null ? null : u.getDisplayName();
    }

    public boolean isSignedIn() {
        return user != null;
    }

    void set(User u) {
        user = u;
    }

    void clear() {
        user = null;
    }
}
