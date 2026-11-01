package com.team23.trojanrideshare.ui.profile;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.HistoryEntry;
import com.team23.trojanrideshare.domain.HistoryFilter;
import com.team23.trojanrideshare.domain.NonPaymentFlag;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.User;
import com.team23.trojanrideshare.ui.common.BaseViewModel;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** FR-1.6 – FR-1.10: a profile, its private ride lists, and its public rides. */
public class ProfileViewModel extends BaseViewModel {

    private final MutableLiveData<User> user = new MutableLiveData<>();
    private final MutableLiveData<List<Ride>> upcoming = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<HistoryEntry>> completed = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Ride>> publicRides = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<NonPaymentFlag>> flags = new MutableLiveData<>(new ArrayList<>());
    private String uid;

    public ProfileViewModel(@NonNull Application app) {
        super(app);
    }

    public LiveData<User> user() {
        return user;
    }

    public LiveData<List<Ride>> upcoming() {
        return upcoming;
    }

    public LiveData<List<HistoryEntry>> completed() {
        return completed;
    }

    public LiveData<List<Ride>> publicRides() {
        return publicRides;
    }

    /** Non-payment flags on the signed-in user. */
    public LiveData<List<NonPaymentFlag>> flags() {
        return flags;
    }

    public boolean isMe() {
        return uid != null && uid.equals(app().session.uid());
    }

    /** @param profileUid the user to show; null shows the signed-in user. */
    public void load(String profileUid) {
        uid = profileUid == null ? app().session.uid() : profileUid;
        app().account.getUser(uid, call(u -> {
            user.setValue(u);
            app().history.getPublicRides(u, call(publicRides::setValue));
        }));
        if (isMe()) {
            app().history.getUpcoming(call(upcoming::setValue));
            HistoryFilter f = new HistoryFilter();
            f.setStatus(ActivityStatus.COMPLETED);
            app().history.getHistory(f, call(completed::setValue));
            app().costs.getMyFlags(call(flags::setValue));
        }
    }

    public void reload() {
        load(uid);
    }

    public void saveNickname(String name) {
        app().account.updateNickname(name, call(u -> {
            user.setValue(u);
            say("Nickname updated.");
        }));
    }

    public void setRidePublic(String rideId, boolean on) {
        app().account.setRidePublic(rideId, on, call(u -> {
            user.setValue(u);
            app().history.getPublicRides(u, call(publicRides::setValue));
        }));
    }

    public void logOut() {
        app().account.logOut();
    }

    // ------------------------------------------------------------------ demo clock

    public LocalDateTime now() {
        return app().clock.now();
    }

    public Duration clockOffset() {
        return app().clock.getOffset();
    }

    public void shiftClock(Duration d) {
        app().setClockOffset(d == null ? Duration.ZERO : app().clock.getOffset().plus(d));
    }

    public String backendLabel() {
        return app().backendLabel + " · Locations: " + app().locationLabel;
    }
}
