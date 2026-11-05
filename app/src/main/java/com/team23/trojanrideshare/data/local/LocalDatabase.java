package com.team23.trojanrideshare.data.local;

import com.team23.trojanrideshare.data.JoinRequestRepository;
import com.team23.trojanrideshare.data.RideRepository;
import com.team23.trojanrideshare.data.RideTransaction;
import com.team23.trojanrideshare.data.RideTxContext;
import com.team23.trojanrideshare.data.UserRepository;
import com.team23.trojanrideshare.data.firestore.FirestoreMapper;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.NonPaymentFlag;
import com.team23.trojanrideshare.domain.RequestStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.User;
import com.team23.trojanrideshare.domain.util.Clock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The three repositories on top of the on-device {@link LocalStore}. Used when no Firebase
 * project is configured, so the app works straight from Android Studio. Documents, rules for
 * USC ID uniqueness, and all-or-nothing ride transactions behave as with Cloud Firestore.
 */
public class LocalDatabase implements UserRepository, RideRepository, JoinRequestRepository {

    private static final String USERS = "users";
    private static final String USC_IDS = "uscIds";
    private static final String RIDES = "rides";
    private static final String JOIN_REQUESTS = "joinRequests";
    private static final String FLAGS = "nonPaymentFlags";

    private final LocalStore store;
    private final Clock clock;

    public LocalDatabase(LocalStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    private static String flagId(String rideId, String userId) {
        return rideId + "_" + userId;
    }

    private static List<Ride> rides(Map<String, Map<String, Object>> docs) {
        List<Ride> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : docs.entrySet()) {
            out.add(FirestoreMapper.toRide(e.getKey(), e.getValue()));
        }
        return out;
    }

    // ------------------------------------------------------------------ users

    @Override
    public void createUser(User u, Callback<Void> cb) {
        store.run(s -> {
            if (s.get(USC_IDS, u.getUscId()) != null) {
                throw new AppException(ErrorCode.DUPLICATE, "An account with this USC ID already exists.");
            }
            Map<String, Object> reservation = new HashMap<>();
            reservation.put("uid", u.getUid());
            s.put(USC_IDS, u.getUscId(), reservation);
            s.put(USERS, u.getUid(), FirestoreMapper.toMap(u));
            return null;
        }, cb);
    }

    @Override
    public void getUser(String uid, Callback<User> cb) {
        store.run(s -> {
            Map<String, Object> doc = s.get(USERS, uid);
            if (doc == null) throw new AppException(ErrorCode.NOT_FOUND, "This user no longer exists.");
            return FirestoreMapper.toUser(uid, doc);
        }, cb);
    }

    @Override
    public void updateUser(User u, Callback<Void> cb) {
        store.run(s -> {
            Map<String, Object> doc = s.get(USERS, u.getUid());
            if (doc == null) throw new AppException(ErrorCode.NOT_FOUND, "This user no longer exists.");
            doc.put("nickname", u.getNickname() == null ? "" : u.getNickname());
            doc.put("publicRideIds", new ArrayList<>(u.getPublicRideIds()));
            s.put(USERS, u.getUid(), doc);
            return null;
        }, cb);
    }

    // ------------------------------------------------------------------ rides

    @Override
    public void createRide(Ride r, Callback<Ride> cb) {
        store.run(s -> {
            r.setRideId(s.newId());
            s.put(RIDES, r.getRideId(), FirestoreMapper.toMap(r));
            return r;
        }, cb);
    }

    @Override
    public void getRide(String rideId, Callback<Ride> cb) {
        store.run(s -> {
            Map<String, Object> doc = s.get(RIDES, rideId);
            if (doc == null) throw new AppException(ErrorCode.NOT_FOUND, "This ride no longer exists.");
            return FirestoreMapper.toRide(rideId, doc);
        }, cb);
    }

    @Override
    public void getUpcomingRides(LocalDateTime now, Callback<List<Ride>> cb) {
        long after = FirestoreMapper.millis(now);
        store.run(s -> {
            List<Ride> out = new ArrayList<>();
            for (Ride r : rides(s.all(RIDES))) {
                if (r.getDeparture() != null && FirestoreMapper.millis(r.getDeparture()) > after) out.add(r);
            }
            return out;
        }, cb);
    }

    @Override
    public void getRidesOf(String uid, Callback<List<Ride>> cb) {
        store.run(s -> {
            List<Ride> out = new ArrayList<>();
            for (Ride r : rides(s.all(RIDES))) {
                if (r.hasMember(uid)) out.add(r);
            }
            return out;
        }, cb);
    }

    @Override
    public void getRidesByIds(List<String> rideIds, Callback<List<Ride>> cb) {
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(rideIds));
        store.run(s -> {
            List<Ride> out = new ArrayList<>();
            for (String id : ids) {
                Map<String, Object> doc = s.get(RIDES, id);
                if (doc != null) out.add(FirestoreMapper.toRide(id, doc));
            }
            return out;
        }, cb);
    }

    @Override
    public void getSharesOf(String uid, Callback<List<CostShare>> cb) {
        store.run(s -> {
            List<CostShare> out = new ArrayList<>();
            for (Ride r : rides(s.all(RIDES))) {
                CostShare share = r.shareOf(uid);
                if (share != null && r.hasMember(uid)) out.add(share);
            }
            return out;
        }, cb);
    }

    @Override
    public void getFlagsForRide(String rideId, String organizerId, Callback<List<NonPaymentFlag>> cb) {
        store.run(s -> {
            List<NonPaymentFlag> out = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> e : s.all(FLAGS).entrySet()) {
                NonPaymentFlag f = FirestoreMapper.toFlag(e.getKey(), e.getValue());
                if (rideId.equals(f.getRideId()) && organizerId.equals(f.getOrganizerId())) out.add(f);
            }
            return out;
        }, cb);
    }

    @Override
    public void getFlagsAgainst(String uid, Callback<List<NonPaymentFlag>> cb) {
        store.run(s -> {
            List<NonPaymentFlag> out = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> e : s.all(FLAGS).entrySet()) {
                NonPaymentFlag f = FirestoreMapper.toFlag(e.getKey(), e.getValue());
                if (uid.equals(f.getUserId())) out.add(f);
            }
            return out;
        }, cb);
    }

    @Override
    public <T> void runTransaction(String rideId, RideTransaction<T> t, Callback<T> cb) {
        store.run(s -> {
            Tx ctx = new Tx(s, rideId);
            T result = t.apply(ctx);
            ctx.commit();
            return result;
        }, cb);
    }

    // ------------------------------------------------------------------ join requests

    @Override
    public void getRequest(String requestId, Callback<JoinRequest> cb) {
        store.run(s -> {
            Map<String, Object> doc = s.get(JOIN_REQUESTS, requestId);
            if (doc == null) throw new AppException(ErrorCode.NOT_FOUND, "This request was withdrawn.");
            return FirestoreMapper.toRequest(requestId, doc);
        }, cb);
    }

    @Override
    public void getRequestsBy(String uid, Callback<List<JoinRequest>> cb) {
        store.run(s -> {
            List<JoinRequest> out = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> e : s.all(JOIN_REQUESTS).entrySet()) {
                JoinRequest q = FirestoreMapper.toRequest(e.getKey(), e.getValue());
                if (uid.equals(q.getRequesterId())) out.add(q);
            }
            return out;
        }, cb);
    }

    @Override
    public void getPending(String rideId, String organizerId, Callback<List<JoinRequest>> cb) {
        store.run(s -> {
            List<JoinRequest> out = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> e : s.all(JOIN_REQUESTS).entrySet()) {
                JoinRequest q = FirestoreMapper.toRequest(e.getKey(), e.getValue());
                if (rideId.equals(q.getRideId()) && organizerId.equals(q.getOrganizerId())
                        && q.getStatus() == RequestStatus.PENDING) out.add(q);
            }
            return out;
        }, cb);
    }

    // ------------------------------------------------------------------ transaction context

    /** Buffers every write and applies them together in {@link #commit()}, like Firestore. */
    private final class Tx implements RideTxContext {

        private final LocalStore s;
        private final String rideId;
        private final Map<String, Map<String, Object>> requestWrites = new LinkedHashMap<>();
        private final List<String> requestDeletes = new ArrayList<>();
        private final Map<String, Map<String, Object>> flagWrites = new LinkedHashMap<>();
        private final List<String> flagDeletes = new ArrayList<>();
        private Ride ride;
        private boolean rideDirty;

        Tx(LocalStore s, String rideId) {
            this.s = s;
            this.rideId = rideId;
        }

        @Override
        public Ride getRide() throws AppException {
            if (ride == null) {
                Map<String, Object> doc = s.get(RIDES, rideId);
                if (doc == null) throw new AppException(ErrorCode.NOT_FOUND, "This ride no longer exists.");
                ride = FirestoreMapper.toRide(rideId, doc);
            }
            return ride;
        }

        @Override
        public JoinRequest getRequest(String requestId) {
            Map<String, Object> doc = s.get(JOIN_REQUESTS, requestId);
            return doc == null ? null : FirestoreMapper.toRequest(requestId, doc);
        }

        @Override
        public String newRequestId() {
            return s.newId();
        }

        @Override
        public void putRide(Ride r) {
            ride = r;
            rideDirty = true;
        }

        @Override
        public void putRequest(JoinRequest q) {
            requestWrites.put(q.getRequestId(), FirestoreMapper.toMap(q));
            requestDeletes.remove(q.getRequestId());
        }

        @Override
        public void deleteRequest(String requestId) {
            requestWrites.remove(requestId);
            requestDeletes.add(requestId);
        }

        /** The request as this transaction will leave it (buffered write first). */
        private Map<String, Object> pendingDoc(String requestId) {
            Map<String, Object> doc = requestWrites.get(requestId);
            return doc != null ? doc : s.get(JOIN_REQUESTS, requestId);
        }

        @Override
        public void rejectPending() throws AppException {
            Ride r = getRide();
            Long now = FirestoreMapper.millis(clock.instant());
            for (String requestId : r.getPendingRequests().values()) {
                Map<String, Object> doc = pendingDoc(requestId);
                if (doc == null) continue;
                doc.put("status", RequestStatus.REJECTED.name());
                doc.put("respondedAt", now);
                requestWrites.put(requestId, doc);
            }
            r.getPendingRequests().clear();
            rideDirty = true;
        }

        @Override
        public void reassignPending(String newOrganizerId) throws AppException {
            for (String requestId : getRide().getPendingRequests().values()) {
                Map<String, Object> doc = pendingDoc(requestId);
                if (doc == null) continue;
                doc.put("organizerId", newOrganizerId);
                requestWrites.put(requestId, doc);
            }
        }

        @Override
        public void putShares(List<CostShare> shares) throws AppException {
            getRide().setShares(shares);
            rideDirty = true;
        }

        @Override
        public void putFlag(NonPaymentFlag f) {
            String id = flagId(f.getRideId(), f.getUserId());
            flagWrites.put(id, FirestoreMapper.toMap(f));
            flagDeletes.remove(id);
        }

        @Override
        public void removeFlag(String userId) {
            String id = flagId(rideId, userId);
            flagWrites.remove(id);
            flagDeletes.add(id);
        }

        void commit() {
            if (rideDirty && ride != null) s.put(RIDES, rideId, FirestoreMapper.toMap(ride));
            for (Map.Entry<String, Map<String, Object>> e : requestWrites.entrySet()) {
                s.put(JOIN_REQUESTS, e.getKey(), e.getValue());
            }
            for (String id : requestDeletes) s.delete(JOIN_REQUESTS, id);
            for (Map.Entry<String, Map<String, Object>> e : flagWrites.entrySet()) s.put(FLAGS, e.getKey(), e.getValue());
            for (String id : flagDeletes) s.delete(FLAGS, id);
        }
    }
}
