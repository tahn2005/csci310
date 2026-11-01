package com.team23.trojanrideshare.data.firestore;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.team23.trojanrideshare.data.RideRepository;
import com.team23.trojanrideshare.data.RideTransaction;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.NonPaymentFlag;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.util.Clock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** rides/{rideId} (with riders, pending requests, and cost shares) and nonPaymentFlags/{rideId_uid}. */
public class FirestoreRideRepository implements RideRepository {

    private static final int IN_QUERY_LIMIT = 10;

    private final FirebaseFirestore db;
    private final NetworkMonitor network;
    private final Clock clock;

    public FirestoreRideRepository(FirebaseFirestore db, NetworkMonitor network, Clock clock) {
        this.db = db;
        this.network = network;
        this.clock = clock;
    }

    private static List<Ride> rides(QuerySnapshot qs) {
        List<Ride> out = new ArrayList<>();
        for (DocumentSnapshot s : qs.getDocuments()) out.add(FirestoreMapper.toRide(s));
        return out;
    }

    @Override
    public void createRide(Ride r, Callback<Ride> cb) {
        if (!network.isOnline()) {
            cb.onError(AppException.offline());
            return;
        }
        DocumentReference ref = db.collection(FirestorePaths.RIDES).document();
        r.setRideId(ref.getId());
        ref.set(FirestoreMapper.toMap(r))
                .addOnSuccessListener(v -> cb.onSuccess(r))
                .addOnFailureListener(e -> cb.onError(FirestoreErrors.map(e)));
    }

    @Override
    public void getRide(String rideId, Callback<Ride> cb) {
        db.collection(FirestorePaths.RIDES).document(rideId).get(Source.SERVER)
                .addOnSuccessListener(s -> {
                    if (!s.exists()) {
                        cb.onError(new AppException(ErrorCode.NOT_FOUND, "This ride no longer exists."));
                    } else {
                        cb.onSuccess(FirestoreMapper.toRide(s));
                    }
                })
                .addOnFailureListener(e -> cb.onError(FirestoreErrors.map(e)));
    }

    @Override
    public void getUpcomingRides(LocalDateTime now, Callback<List<Ride>> cb) {
        db.collection(FirestorePaths.RIDES)
                .whereGreaterThan("departureMillis", FirestoreMapper.millis(now))
                .get(Source.SERVER)
                .addOnSuccessListener(qs -> cb.onSuccess(rides(qs)))
                .addOnFailureListener(e -> cb.onError(FirestoreErrors.map(e)));
    }

    @Override
    public void getRidesOf(String uid, Callback<List<Ride>> cb) {
        db.collection(FirestorePaths.RIDES).whereArrayContains("memberIds", uid).get(Source.SERVER)
                .addOnSuccessListener(qs -> cb.onSuccess(rides(qs)))
                .addOnFailureListener(e -> cb.onError(FirestoreErrors.map(e)));
    }

    @Override
    public void getRidesByIds(List<String> rideIds, Callback<List<Ride>> cb) {
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(rideIds));
        if (ids.isEmpty()) {
            cb.onSuccess(new ArrayList<>());
            return;
        }
        List<Task<QuerySnapshot>> tasks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += IN_QUERY_LIMIT) {
            List<String> chunk = ids.subList(i, Math.min(ids.size(), i + IN_QUERY_LIMIT));
            tasks.add(db.collection(FirestorePaths.RIDES)
                    .whereIn(FieldPath.documentId(), new ArrayList<>(chunk)).get(Source.SERVER));
        }
        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    List<Ride> out = new ArrayList<>();
                    for (Object o : results) out.addAll(rides((QuerySnapshot) o));
                    cb.onSuccess(out);
                })
                .addOnFailureListener(e -> cb.onError(FirestoreErrors.map(e)));
    }

    @Override
    public void getSharesOf(String uid, Callback<List<CostShare>> cb) {
        getRidesOf(uid, new Callback<List<Ride>>() {
            @Override
            public void onSuccess(List<Ride> result) {
                List<CostShare> out = new ArrayList<>();
                for (Ride r : result) {
                    CostShare s = r.shareOf(uid);
                    if (s != null) out.add(s);
                }
                cb.onSuccess(out);
            }

            @Override
            public void onError(AppException e) {
                cb.onError(e);
            }
        });
    }

    @Override
    public void getFlagsForRide(String rideId, String organizerId, Callback<List<NonPaymentFlag>> cb) {
        db.collection(FirestorePaths.FLAGS)
                .whereEqualTo("rideId", rideId)
                .whereEqualTo("organizerId", organizerId)
                .get(Source.SERVER)
                .addOnSuccessListener(qs -> {
                    List<NonPaymentFlag> out = new ArrayList<>();
                    for (DocumentSnapshot s : qs.getDocuments()) out.add(FirestoreMapper.toFlag(s));
                    cb.onSuccess(out);
                })
                .addOnFailureListener(e -> cb.onError(FirestoreErrors.map(e)));
    }

    @Override
    public void getFlagsAgainst(String uid, Callback<List<NonPaymentFlag>> cb) {
        db.collection(FirestorePaths.FLAGS).whereEqualTo("userId", uid).get(Source.SERVER)
                .addOnSuccessListener(qs -> {
                    List<NonPaymentFlag> out = new ArrayList<>();
                    for (DocumentSnapshot s : qs.getDocuments()) out.add(FirestoreMapper.toFlag(s));
                    cb.onSuccess(out);
                })
                .addOnFailureListener(e -> cb.onError(FirestoreErrors.map(e)));
    }

    @Override
    public <T> void runTransaction(String rideId, RideTransaction<T> t, Callback<T> cb) {
        if (!network.isOnline()) {
            cb.onError(AppException.offline());
            return;
        }
        db.runTransaction(tx -> {
            FirestoreRideTxContext ctx = new FirestoreRideTxContext(db, tx, rideId, clock);
            T result;
            try {
                result = t.apply(ctx);
            } catch (AppException e) {
                throw new FirestoreErrors.TxAbort(e);
            }
            ctx.flush();
            return result;
        }).addOnSuccessListener(cb::onSuccess)
                .addOnFailureListener(e -> cb.onError(FirestoreErrors.map(e)));
    }
}
