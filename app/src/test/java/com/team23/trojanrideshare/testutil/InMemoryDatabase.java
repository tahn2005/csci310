package com.team23.trojanrideshare.testutil;

import com.team23.trojanrideshare.data.JoinRequestRepository;
import com.team23.trojanrideshare.data.RideRepository;
import com.team23.trojanrideshare.data.RideTransaction;
import com.team23.trojanrideshare.data.RideTxContext;
import com.team23.trojanrideshare.data.UserRepository;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.NonPaymentFlag;
import com.team23.trojanrideshare.domain.RequestStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An in-memory stand-in for Cloud Firestore implementing all three repositories, with
 * all-or-nothing transactions. Lets the Application Logic layer be tested on the JVM.
 */
public class InMemoryDatabase implements RideRepository, JoinRequestRepository, UserRepository {

    public final Map<String, Ride> rides = new LinkedHashMap<>();
    public final Map<String, JoinRequest> requests = new LinkedHashMap<>();
    public final Map<String, NonPaymentFlag> flags = new LinkedHashMap<>();
    public final Map<String, User> users = new LinkedHashMap<>();
    public final Map<String, String> uscIds = new HashMap<>();
    public boolean offline;
    private int nextId = 1;

    private boolean checkOnline(Callback<?> cb) {
        if (offline) {
            cb.onError(AppException.offline());
            return false;
        }
        return true;
    }

    private static String flagKey(String rideId, String userId) {
        return rideId + "_" + userId;
    }

    // ------------------------------------------------------------------ users

    @Override
    public void createUser(User u, Callback<Void> cb) {
        if (!checkOnline(cb)) return;
        if (uscIds.containsKey(u.getUscId())) {
            cb.onError(new AppException(ErrorCode.DUPLICATE, "An account with this USC ID already exists."));
            return;
        }
        uscIds.put(u.getUscId(), u.getUid());
        users.put(u.getUid(), Copies.user(u));
        cb.onSuccess(null);
    }

    @Override
    public void getUser(String uid, Callback<User> cb) {
        if (!checkOnline(cb)) return;
        User u = users.get(uid);
        if (u == null) cb.onError(new AppException(ErrorCode.NOT_FOUND, "This user no longer exists."));
        else cb.onSuccess(Copies.user(u));
    }

    @Override
    public void updateUser(User u, Callback<Void> cb) {
        if (!checkOnline(cb)) return;
        User stored = users.get(u.getUid());
        stored.setNickname(u.getNickname());
        stored.setPublicRideIds(u.getPublicRideIds());
        cb.onSuccess(null);
    }

    // ------------------------------------------------------------------ rides

    /** Stores a ride directly (test setup). */
    public Ride put(Ride r) {
        if (r.getRideId() == null) r.setRideId("ride" + (nextId++));
        rides.put(r.getRideId(), Copies.ride(r));
        return r;
    }

    public Ride ride(String id) {
        return Copies.ride(rides.get(id));
    }

    @Override
    public void createRide(Ride r, Callback<Ride> cb) {
        if (!checkOnline(cb)) return;
        put(r);
        cb.onSuccess(Copies.ride(r));
    }

    @Override
    public void getRide(String rideId, Callback<Ride> cb) {
        if (!checkOnline(cb)) return;
        Ride r = rides.get(rideId);
        if (r == null) cb.onError(new AppException(ErrorCode.NOT_FOUND, "This ride no longer exists."));
        else cb.onSuccess(Copies.ride(r));
    }

    @Override
    public void getUpcomingRides(LocalDateTime now, Callback<List<Ride>> cb) {
        if (!checkOnline(cb)) return;
        List<Ride> out = new ArrayList<>();
        for (Ride r : rides.values()) if (r.getDeparture().isAfter(now)) out.add(Copies.ride(r));
        cb.onSuccess(out);
    }

    @Override
    public void getRidesOf(String uid, Callback<List<Ride>> cb) {
        if (!checkOnline(cb)) return;
        List<Ride> out = new ArrayList<>();
        for (Ride r : rides.values()) if (r.hasMember(uid)) out.add(Copies.ride(r));
        cb.onSuccess(out);
    }

    @Override
    public void getRidesByIds(List<String> rideIds, Callback<List<Ride>> cb) {
        if (!checkOnline(cb)) return;
        List<Ride> out = new ArrayList<>();
        for (String id : rideIds) if (rides.containsKey(id)) out.add(Copies.ride(rides.get(id)));
        cb.onSuccess(out);
    }

    @Override
    public void getSharesOf(String uid, Callback<List<CostShare>> cb) {
        if (!checkOnline(cb)) return;
        List<CostShare> out = new ArrayList<>();
        for (Ride r : rides.values()) if (r.shareOf(uid) != null) out.add(r.shareOf(uid).copy());
        cb.onSuccess(out);
    }

    @Override
    public void getFlagsForRide(String rideId, String organizerId, Callback<List<NonPaymentFlag>> cb) {
        List<NonPaymentFlag> out = new ArrayList<>();
        for (NonPaymentFlag f : flags.values()) {
            if (f.getRideId().equals(rideId) && f.getOrganizerId().equals(organizerId)) out.add(f);
        }
        cb.onSuccess(out);
    }

    @Override
    public void getFlagsAgainst(String uid, Callback<List<NonPaymentFlag>> cb) {
        List<NonPaymentFlag> out = new ArrayList<>();
        for (NonPaymentFlag f : flags.values()) if (f.getUserId().equals(uid)) out.add(f);
        cb.onSuccess(out);
    }

    @Override
    public <T> void runTransaction(String rideId, RideTransaction<T> t, Callback<T> cb) {
        if (!checkOnline(cb)) return;
        Tx ctx = new Tx(rideId);
        T result;
        try {
            result = t.apply(ctx);
        } catch (AppException e) {
            cb.onError(e); // nothing was written
            return;
        }
        ctx.commit();
        cb.onSuccess(result);
    }

    private class Tx implements RideTxContext {
        private final String rideId;
        private Ride ride;
        private Ride rideToWrite;
        private final Map<String, JoinRequest> requestWrites = new LinkedHashMap<>();
        private final List<String> requestDeletes = new ArrayList<>();
        private final Map<String, NonPaymentFlag> flagWrites = new LinkedHashMap<>();
        private final List<String> flagDeletes = new ArrayList<>();

        Tx(String rideId) {
            this.rideId = rideId;
        }

        @Override
        public Ride getRide() throws AppException {
            if (ride == null) {
                Ride r = rides.get(rideId);
                if (r == null) throw new AppException(ErrorCode.NOT_FOUND, "This ride no longer exists.");
                ride = Copies.ride(r);
            }
            return ride;
        }

        @Override
        public JoinRequest getRequest(String requestId) {
            JoinRequest q = requests.get(requestId);
            return q == null ? null : Copies.request(q);
        }

        @Override
        public String newRequestId() {
            return "req" + (nextId++);
        }

        @Override
        public void putRide(Ride r) {
            rideToWrite = r;
        }

        @Override
        public void putRequest(JoinRequest q) {
            requestWrites.put(q.getRequestId(), Copies.request(q));
        }

        @Override
        public void deleteRequest(String requestId) {
            requestDeletes.add(requestId);
        }

        @Override
        public void rejectPending() throws AppException {
            for (String id : getRide().getPendingRequests().values()) {
                JoinRequest q = Copies.request(requests.get(id));
                q.setStatus(RequestStatus.REJECTED);
                requestWrites.put(id, q);
            }
            getRide().getPendingRequests().clear();
            rideToWrite = getRide();
        }

        @Override
        public void reassignPending(String newOrganizerId) throws AppException {
            for (String id : getRide().getPendingRequests().values()) {
                JoinRequest q = Copies.request(requests.get(id));
                q.setOrganizerId(newOrganizerId);
                requestWrites.put(id, q);
            }
        }

        @Override
        public void putShares(List<CostShare> shares) throws AppException {
            getRide().setShares(shares);
            rideToWrite = getRide();
        }

        @Override
        public void putFlag(NonPaymentFlag f) {
            flagWrites.put(flagKey(f.getRideId(), f.getUserId()), f);
        }

        @Override
        public void removeFlag(String userId) {
            flagDeletes.add(flagKey(rideId, userId));
        }

        void commit() {
            if (rideToWrite != null) rides.put(rideId, Copies.ride(rideToWrite));
            requests.putAll(requestWrites);
            for (String id : requestDeletes) requests.remove(id);
            flags.putAll(flagWrites);
            for (String id : flagDeletes) flags.remove(id);
        }
    }

    // ------------------------------------------------------------------ requests

    @Override
    public void getRequest(String requestId, Callback<JoinRequest> cb) {
        JoinRequest q = requests.get(requestId);
        if (q == null) cb.onError(new AppException(ErrorCode.NOT_FOUND, "This request was withdrawn."));
        else cb.onSuccess(Copies.request(q));
    }

    @Override
    public void getRequestsBy(String uid, Callback<List<JoinRequest>> cb) {
        List<JoinRequest> out = new ArrayList<>();
        for (JoinRequest q : requests.values()) if (q.getRequesterId().equals(uid)) out.add(Copies.request(q));
        cb.onSuccess(out);
    }

    @Override
    public void getPending(String rideId, String organizerId, Callback<List<JoinRequest>> cb) {
        List<JoinRequest> out = new ArrayList<>();
        for (JoinRequest q : requests.values()) {
            if (q.getRideId().equals(rideId) && q.getOrganizerId().equals(organizerId)
                    && q.getStatus() == RequestStatus.PENDING) out.add(Copies.request(q));
        }
        cb.onSuccess(out);
    }
}
