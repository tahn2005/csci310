package com.team23.trojanrideshare.data.firestore;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;
import com.team23.trojanrideshare.data.RideTxContext;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.NonPaymentFlag;
import com.team23.trojanrideshare.domain.RequestStatus;
import com.team23.trojanrideshare.domain.Ride;
import com.team23.trojanrideshare.domain.util.Clock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link RideTxContext} over a Firestore {@link Transaction}. Firestore requires every read
 * to happen before any write, so writes are buffered and applied by {@link #flush()} after
 * the service's rule has finished.
 */
final class FirestoreRideTxContext implements RideTxContext {

    private interface Write {
        void apply(Transaction tx);
    }

    private final FirebaseFirestore db;
    private final Transaction tx;
    private final DocumentReference rideRef;
    private final Clock clock;
    private final List<Write> writes = new ArrayList<>();
    private Ride ride;
    private boolean rideDirty;

    FirestoreRideTxContext(FirebaseFirestore db, Transaction tx, String rideId, Clock clock) {
        this.db = db;
        this.tx = tx;
        this.rideRef = db.collection(FirestorePaths.RIDES).document(rideId);
        this.clock = clock;
    }

    private DocumentSnapshot read(DocumentReference ref) throws AppException {
        try {
            return tx.get(ref);
        } catch (FirebaseFirestoreException e) {
            throw FirestoreErrors.map(e);
        }
    }

    private DocumentReference requestRef(String id) {
        return db.collection(FirestorePaths.JOIN_REQUESTS).document(id);
    }

    @Override
    public Ride getRide() throws AppException {
        if (ride == null) {
            DocumentSnapshot s = read(rideRef);
            if (!s.exists()) throw new AppException(ErrorCode.NOT_FOUND, "This ride no longer exists.");
            ride = FirestoreMapper.toRide(s);
        }
        return ride;
    }

    @Override
    public JoinRequest getRequest(String requestId) throws AppException {
        DocumentSnapshot s = read(requestRef(requestId));
        return s.exists() ? FirestoreMapper.toRequest(s) : null;
    }

    @Override
    public String newRequestId() {
        return db.collection(FirestorePaths.JOIN_REQUESTS).document().getId();
    }

    @Override
    public void putRide(Ride r) {
        ride = r;
        rideDirty = true;
    }

    @Override
    public void putRequest(JoinRequest q) {
        Map<String, Object> data = FirestoreMapper.toMap(q);
        DocumentReference ref = requestRef(q.getRequestId());
        writes.add(t -> t.set(ref, data));
    }

    @Override
    public void deleteRequest(String requestId) {
        DocumentReference ref = requestRef(requestId);
        writes.add(t -> t.delete(ref));
    }

    @Override
    public void rejectPending() throws AppException {
        Ride r = getRide();
        long now = FirestoreMapper.millis(clock.instant());
        for (String requestId : r.getPendingRequests().values()) {
            DocumentReference ref = requestRef(requestId);
            writes.add(t -> t.update(ref, "status", RequestStatus.REJECTED.name(), "respondedAt", now));
        }
        r.getPendingRequests().clear();
        rideDirty = true;
    }

    @Override
    public void reassignPending(String newOrganizerId) throws AppException {
        for (String requestId : getRide().getPendingRequests().values()) {
            DocumentReference ref = requestRef(requestId);
            writes.add(t -> t.update(ref, "organizerId", newOrganizerId));
        }
    }

    @Override
    public void putShares(List<CostShare> shares) throws AppException {
        getRide().setShares(shares);
        rideDirty = true;
    }

    @Override
    public void putFlag(NonPaymentFlag f) {
        Map<String, Object> data = FirestoreMapper.toMap(f);
        DocumentReference ref = db.collection(FirestorePaths.FLAGS)
                .document(FirestorePaths.flagId(f.getRideId(), f.getUserId()));
        writes.add(t -> t.set(ref, data));
    }

    @Override
    public void removeFlag(String userId) throws AppException {
        DocumentReference ref = db.collection(FirestorePaths.FLAGS)
                .document(FirestorePaths.flagId(rideRef.getId(), userId));
        if (read(ref).exists()) {
            writes.add(t -> t.delete(ref));
        }
    }

    /** Applies the buffered writes; called once the service's rule has succeeded. */
    void flush() {
        if (rideDirty && ride != null) {
            Map<String, Object> data = FirestoreMapper.toMap(ride);
            tx.set(rideRef, data);
        }
        for (Write w : writes) w.apply(tx);
    }
}
