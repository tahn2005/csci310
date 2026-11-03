package com.team23.trojanrideshare.ui.ride;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team23.trojanrideshare.domain.ActivityStatus;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.NonPaymentFlag;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.RideMember;
import com.team23.trojanrideshare.ui.common.BaseViewModel;
import com.team23.trojanrideshare.ui.common.Event;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ride detail: post, riders, join/leave, organizer controls, requests, and cost shares. */
public class RideDetailViewModel extends BaseViewModel {

    private final MutableLiveData<Ride> ride = new MutableLiveData<>();
    private final MutableLiveData<List<JoinRequest>> pending = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Map<String, NonPaymentFlag>> flags = new MutableLiveData<>(new HashMap<>());
    private String rideId;

    public RideDetailViewModel(@NonNull Application app) {
        super(app);
    }

    public LiveData<Ride> ride() {
        return ride;
    }

    public LiveData<List<JoinRequest>> pending() {
        return pending;
    }

    /** Non-payment flags by rider id (organizer only). */
    public LiveData<Map<String, NonPaymentFlag>> flags() {
        return flags;
    }

    public void load(String id) {
        rideId = id;
        app().rides.getRide(id, call(r -> {
            ride.setValue(r);
            String me = uid();
            if (r.isOrganizer(me) && r.isActive()) {
                app().joins.getPending(id, call(pending::setValue));
            } else {
                pending.setValue(new ArrayList<>());
            }
            if (r.isOrganizer(me) && r.getActivityStatus() == ActivityStatus.COMPLETED) {
                app().costs.getFlagsForRide(id, call(list -> {
                    Map<String, NonPaymentFlag> byUser = new HashMap<>();
                    for (NonPaymentFlag f : list) byUser.put(f.getUserId(), f);
                    flags.setValue(byUser);
                }));
            }
        }));
    }

    public void reload() {
        if (rideId != null) load(rideId);
    }

    // ------------------------------------------------------------------ rules for the screen

    public String uid() {
        return app().session.uid();
    }

    public LocalDateTime now() {
        return app().clock.now();
    }

    public String joinBlock(Ride r) {
        return app().joins.checkEligibility(r);
    }

    public String leaveBlock(Ride r) {
        return app().joins.leaveBlockReason(r);
    }

    public String completeBlock(Ride r) {
        return app().costs.completeBlockReason(r);
    }

    public String editBlock(Ride r) {
        return app().rides.editBlockReason(r);
    }

    public RideMember successorIfLeaving(Ride r) {
        return app().joins.successorIfLeaving(r);
    }

    // ------------------------------------------------------------------ actions

    public void requestToJoin(String msg) {
        app().joins.sendRequest(rideId, msg, call(q -> {
            say("Request sent! The organizer will accept or reject it.");
            reload();
        }, e -> {
            error.setValue(new Event<>(e));
            reload();
        }));
    }

    public void cancelRequest() {
        Ride r = ride.getValue();
        String requestId = r == null ? null : r.pendingRequestIdOf(uid());
        if (requestId == null) return;
        app().joins.cancelRequest(rideId, requestId, call(v -> {
            say("Your request was cancelled.");
            reload();
        }));
    }

    public void leave() {
        app().joins.leaveRide(rideId, call(outcome -> {
            if (outcome.rideCancelled) say("You left the ride. It had no other riders, so it was cancelled.");
            else if (outcome.newOrganizerName != null) say("You left the ride. " + outcome.newOrganizerName + " is now the organizer.");
            else say("You left the ride.");
            reload();
        }));
    }

    public void cancelRide() {
        app().rides.cancelRide(rideId, call(r -> {
            say("Ride cancelled. Pending requests were rejected.");
            reload();
        }));
    }

    /** Field errors for the edit form, checked before the dialog closes. */
    public Map<String, String> editErrors(Ride changes) {
        return app().rides.validateEdit(changes);
    }

    /** @param changes title, departure, max riders, expected cost and note from the edit form */
    public void editRide(Ride changes, ActivityStatus s) {
        app().rides.editRide(rideId, changes, s, call(r -> {
            say("Ride updated: " + s.label() + ".");
            reload();
        }));
    }

    public void accept(JoinRequest q) {
        app().joins.accept(q, call(r -> {
            say(q.getRequesterName() + " joined the ride.");
            reload();
        }, this::failAndReload));
    }

    public void reject(JoinRequest q) {
        app().joins.reject(q, call(r -> {
            say("Request rejected.");
            reload();
        }, this::failAndReload));
    }

    public void markPaid() {
        app().costs.markPaid(rideId, call(r -> {
            say("Marked as paid. Thanks for settling up!");
            reload();
        }));
    }

    public void flag(String userId) {
        app().costs.flag(rideId, userId, call(f -> {
            say(f.getUserName() + " was flagged for non-payment.");
            reload();
        }));
    }

    public void unflag(String userId) {
        app().costs.unflag(rideId, userId, call(v -> {
            say("Flag removed.");
            reload();
        }));
    }

    private void failAndReload(AppException e) {
        error.setValue(new Event<>(e));
        reload();
    }
}
