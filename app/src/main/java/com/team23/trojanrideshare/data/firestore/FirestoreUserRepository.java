package com.team23.trojanrideshare.data.firestore;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.team23.trojanrideshare.data.UserRepository;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** users/{uid} holds profiles; uscIds/{uscId} reserves each USC ID for one account. */
public class FirestoreUserRepository implements UserRepository {

    private final FirebaseFirestore db;
    private final NetworkMonitor network;

    public FirestoreUserRepository(FirebaseFirestore db, NetworkMonitor network) {
        this.db = db;
        this.network = network;
    }

    @Override
    public void createUser(User u, Callback<Void> cb) {
        if (!network.isOnline()) {
            cb.onError(AppException.offline());
            return;
        }
        DocumentReference userRef = db.collection(FirestorePaths.USERS).document(u.getUid());
        DocumentReference idRef = db.collection(FirestorePaths.USC_IDS).document(u.getUscId());
        db.runTransaction(tx -> {
            DocumentSnapshot existing = tx.get(idRef);
            if (existing.exists()) {
                throw new FirestoreErrors.TxAbort(new AppException(ErrorCode.DUPLICATE,
                        "An account with this USC ID already exists."));
            }
            Map<String, Object> reservation = new HashMap<>();
            reservation.put("uid", u.getUid());
            tx.set(idRef, reservation);
            tx.set(userRef, FirestoreMapper.toMap(u));
            return null;
        }).addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError(FirestoreErrors.map(e)));
    }

    @Override
    public void getUser(String uid, Callback<User> cb) {
        db.collection(FirestorePaths.USERS).document(uid).get(Source.SERVER)
                .addOnSuccessListener(s -> {
                    if (!s.exists()) {
                        cb.onError(new AppException(ErrorCode.NOT_FOUND, "This user no longer exists."));
                    } else {
                        cb.onSuccess(FirestoreMapper.toUser(s));
                    }
                })
                .addOnFailureListener(e -> cb.onError(FirestoreErrors.map(e)));
    }

    @Override
    public void updateUser(User u, Callback<Void> cb) {
        if (!network.isOnline()) {
            cb.onError(AppException.offline());
            return;
        }
        Map<String, Object> changes = new HashMap<>();
        changes.put("nickname", u.getNickname() == null ? "" : u.getNickname());
        changes.put("publicRideIds", new ArrayList<>(u.getPublicRideIds()));
        db.collection(FirestorePaths.USERS).document(u.getUid()).update(changes)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError(FirestoreErrors.map(e)));
    }
}
