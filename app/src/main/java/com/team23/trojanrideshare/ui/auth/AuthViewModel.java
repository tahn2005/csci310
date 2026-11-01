package com.team23.trojanrideshare.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team23.trojanrideshare.domain.User;
import com.team23.trojanrideshare.ui.common.BaseViewModel;
import com.team23.trojanrideshare.ui.common.Event;

/** Sign Up and Log In (FR-1.1 – FR-1.4). */
public class AuthViewModel extends BaseViewModel {

    private final MutableLiveData<Event<User>> user = new MutableLiveData<>();

    public AuthViewModel(@NonNull Application app) {
        super(app);
    }

    /** Fires once when the user is signed in. */
    public LiveData<Event<User>> user() {
        return user;
    }

    public void signUp(User u, String password) {
        app().account.signUp(u, password, call(r -> user.setValue(new Event<>(r))));
    }

    public void logIn(String email, String password) {
        app().account.logIn(email, password, call(r -> user.setValue(new Event<>(r))));
    }

    public String backendLabel() {
        return app().backendLabel;
    }

    public int[] activeGradYears() {
        return app().account.activeGradYears();
    }
}
