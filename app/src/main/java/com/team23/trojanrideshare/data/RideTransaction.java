package com.team23.trojanrideshare.data;

import com.team23.trojanrideshare.domain.AppException;

/**
 * A business rule that reads and writes a ride and its related documents atomically.
 * Services implement it; the repository runs it (and may retry it) inside a transaction.
 */
public interface RideTransaction<T> {

    T apply(RideTxContext ctx) throws AppException;
}
