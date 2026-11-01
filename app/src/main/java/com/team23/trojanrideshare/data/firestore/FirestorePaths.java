package com.team23.trojanrideshare.data.firestore;

/** Names of the Cloud Firestore collections (see firestore.rules). */
final class FirestorePaths {
    static final String USERS = "users";
    static final String USC_IDS = "uscIds";
    static final String RIDES = "rides";
    static final String JOIN_REQUESTS = "joinRequests";
    static final String FLAGS = "nonPaymentFlags";

    private FirestorePaths() {
    }

    static String flagId(String rideId, String userId) {
        return rideId + "_" + userId;
    }
}
