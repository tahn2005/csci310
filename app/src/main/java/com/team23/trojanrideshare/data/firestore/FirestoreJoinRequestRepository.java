package com.team23.trojanrideshare.data.firestore;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.team23.trojanrideshare.data.JoinRequestRepository;
import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.ErrorCode;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.RequestStatus;

import java.util.ArrayList;
import java.util.List;

/** joinRequests/{requestId}. Each request stores its ride's current organizer for Security Rules. */
public class FirestoreJoinRequestRepository implements JoinRequestRepository {

    private final FirebaseFirestore db;

    public FirestoreJoinRequestRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void getRequest(String requestId, Callback<JoinRequest> cb) {
        db.collection(FirestorePaths.JOIN_REQUESTS).document(requestId).get(Source.SERVER)
                .addOnSuccessListener(s -> {
                    if (!s.exists()) {
                        cb.onError(new AppException(ErrorCode.NOT_FOUND, "This request was withdrawn."));
                    } else {
                        cb.onSuccess(FirestoreMapper.toRequest(s));
                    }
                })
                .addOnFailureListener(e -> cb.onError(FirestoreErrors.map(e)));
    }

    @Override
    public void getRequestsBy(String uid, Callback<List<JoinRequest>> cb) {
        db.collection(FirestorePaths.JOIN_REQUESTS).whereEqualTo("requesterId", uid).get(Source.SERVER)
                .addOnSuccessListener(qs -> {
                    List<JoinRequest> out = new ArrayList<>();
                    for (DocumentSnapshot s : qs.getDocuments()) out.add(FirestoreMapper.toRequest(s));
                    cb.onSuccess(out);
                })
                .addOnFailureListener(e -> cb.onError(FirestoreErrors.map(e)));
    }

    @Override
    public void getPending(String rideId, String organizerId, Callback<List<JoinRequest>> cb) {
        db.collection(FirestorePaths.JOIN_REQUESTS)
                .whereEqualTo("rideId", rideId)
                .whereEqualTo("organizerId", organizerId)
                .whereEqualTo("status", RequestStatus.PENDING.name())
                .get(Source.SERVER)
                .addOnSuccessListener(qs -> {
                    List<JoinRequest> out = new ArrayList<>();
                    for (DocumentSnapshot s : qs.getDocuments()) out.add(FirestoreMapper.toRequest(s));
                    cb.onSuccess(out);
                })
                .addOnFailureListener(e -> cb.onError(FirestoreErrors.map(e)));
    }
}
