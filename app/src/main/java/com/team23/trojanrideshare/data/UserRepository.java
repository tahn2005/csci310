package com.team23.trojanrideshare.data;

import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.User;

/** Reads and writes user profiles in the central database. */
public interface UserRepository {

    /**
     * Saves a new profile and reserves its USC ID in one transaction.
     * Fails with DUPLICATE if the USC ID is already registered (FR-1.2.6).
     */
    void createUser(User u, Callback<Void> cb);

    /** Fails with NOT_FOUND if the profile does not exist. */
    void getUser(String uid, Callback<User> cb);

    /** Saves the editable fields (nickname, public rides). Email and USC ID never change. */
    void updateUser(User u, Callback<Void> cb);
}
