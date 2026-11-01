package com.team23.trojanrideshare.data;

import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.JoinRequest;

import java.util.List;

/** Reads join requests. All writes go through {@link RideRepository#runTransaction}. */
public interface JoinRequestRepository {

    /** Fails with NOT_FOUND if the request was withdrawn. */
    void getRequest(String requestId, Callback<JoinRequest> cb);

    /** Every request {@code uid} has sent. */
    void getRequestsBy(String uid, Callback<List<JoinRequest>> cb);

    /** Pending requests for a ride, readable by its current organizer. */
    void getPending(String rideId, String organizerId, Callback<List<JoinRequest>> cb);
}
