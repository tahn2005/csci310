package com.team23.trojanrideshare.data;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.CostShare;
import com.team23.trojanrideshare.domain.JoinRequest;
import com.team23.trojanrideshare.domain.NonPaymentFlag;
import com.team23.trojanrideshare.domain.Ride;

import java.util.List;

/**
 * What a {@link RideTransaction} may read and write. Reads return fresh data from inside
 * the transaction; writes are committed together only if the whole transaction succeeds.
 */
public interface RideTxContext {

    /** The ride this transaction is about. Fails with NOT_FOUND if it was deleted. */
    Ride getRide() throws AppException;

    /** The request, or null if it no longer exists (withdrawn). */
    JoinRequest getRequest(String requestId) throws AppException;

    /** A fresh id for a new join request. */
    String newRequestId();

    void putRide(Ride r);

    void putRequest(JoinRequest q);

    /** Deletes a request (cancelling a pending request, FR-3.9). */
    void deleteRequest(String requestId);

    /** FR-3.11.2: marks every pending request of the ride Rejected and clears the list. */
    void rejectPending() throws AppException;

    /** FR-3.10.2: hands the ride's pending requests to a new organizer. */
    void reassignPending(String newOrganizerId) throws AppException;

    /** Stores the cost shares of the ride (saved with the ride by {@link #putRide}). */
    void putShares(List<CostShare> shares) throws AppException;

    void putFlag(NonPaymentFlag f);

    /** Removes the flag on {@code userId}'s share, if there is one. */
    void removeFlag(String userId) throws AppException;
}
