package com.team23.trojanrideshare.data;

import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.NonPaymentFlag;
import com.team23.trojanrideshare.domain.Ride;

import java.time.LocalDateTime;
import java.util.List;

/** The only way the app reads or writes rides, cost shares, and non-payment flags. */
public interface RideRepository {

    /** Stores a new ride and returns it with its generated id. */
    void createRide(Ride r, Callback<Ride> cb);

    void getRide(String rideId, Callback<Ride> cb);

    /** Rides departing after {@code now} (any status; the search engine filters further). */
    void getUpcomingRides(LocalDateTime now, Callback<List<Ride>> cb);

    /** Every ride in which {@code uid} is currently a rider (organizer or accepted). */
    void getRidesOf(String uid, Callback<List<Ride>> cb);

    /** The rides with the given ids that still exist, in no particular order. */
    void getRidesByIds(List<String> rideIds, Callback<List<Ride>> cb);

    /** Every cost share assigned to {@code uid}. */
    void getSharesOf(String uid, Callback<List<CostShare>> cb);

    /** Flags an organizer has placed on riders of one ride (organizer only). */
    void getFlagsForRide(String rideId, String organizerId, Callback<List<NonPaymentFlag>> cb);

    /** Flags placed on {@code uid} by organizers (shown on that user's own profile). */
    void getFlagsAgainst(String uid, Callback<List<NonPaymentFlag>> cb);

    /**
     * Runs {@code t} as one all-or-nothing transaction on the ride and its related
     * documents (NFR-3). If {@code t} throws, nothing is written and the error is returned.
     */
    <T> void runTransaction(String rideId, RideTransaction<T> t, Callback<T> cb);
}
