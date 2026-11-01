package com.team23.trojanrideshare.ui.myrides;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.ui.common.BaseViewModel;
import com.team23.trojanrideshare.ui.common.Event;

import java.util.ArrayList;
import java.util.List;

/** FR-3.7 My Rides & Requests: rides I organize (with incoming requests), rides I joined, my requests. */
public class MyRidesViewModel extends BaseViewModel {

    private final MutableLiveData<List<Ride>> organizing = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Ride>> joined = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<JoinRequest>> incoming = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<JoinRequest>> requests = new MutableLiveData<>(new ArrayList<>());

    public MyRidesViewModel(@NonNull Application app) {
        super(app);
    }

    public LiveData<List<Ride>> organizing() {
        return organizing;
    }

    public LiveData<List<Ride>> joined() {
        return joined;
    }

    /** Pending requests for rides I organize (FR-3.8). */
    public LiveData<List<JoinRequest>> incoming() {
        return incoming;
    }

    /** Requests I sent, newest first (FR-3.7.2). */
    public LiveData<List<JoinRequest>> requests() {
        return requests;
    }

    public String uid() {
        return app().session.uid();
    }

    public void load() {
        String me = uid();
        app().history.getUpcoming(call(list -> {
            List<Ride> org = new ArrayList<>();
            List<Ride> mine = new ArrayList<>();
            for (Ride r : list) {
                if (r.isOrganizer(me)) org.add(r);
                else mine.add(r);
            }
            organizing.setValue(org);
            joined.setValue(mine);
        }));
        app().joins.getIncomingRequests(call(incoming::setValue));
        app().joins.getMyRequests(call(requests::setValue));
    }

    public void accept(JoinRequest q) {
        app().joins.accept(q, call(r -> {
            say(q.getRequesterName() + " joined " + r.getTitle() + ".");
            load();
        }, this::failAndReload));
    }

    public void reject(JoinRequest q) {
        app().joins.reject(q, call(r -> {
            say("Request rejected.");
            load();
        }, this::failAndReload));
    }

    public void cancel(JoinRequest q) {
        app().joins.cancelRequest(q.getRideId(), q.getRequestId(), call(v -> {
            say("Your request was cancelled.");
            load();
        }, this::failAndReload));
    }

    private void failAndReload(AppException e) {
        error.setValue(new Event<>(e));
        load();
    }
}
