package com.team23.trojanrideshare.service;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;

import java.util.ArrayList;
import java.util.List;

/** Collects the results of several asynchronous calls and reports once (first error wins). */
final class Gather<T> {

    private final List<T> results = new ArrayList<>();
    private final Callback<List<T>> done;
    private int remaining;
    private boolean failed;

    Gather(int count, Callback<List<T>> done) {
        this.remaining = count;
        this.done = done;
        if (count == 0) done.onSuccess(results);
    }

    Callback<T> part() {
        return new Callback<T>() {
            @Override
            public void onSuccess(T result) {
                synchronized (Gather.this) {
                    if (failed) return;
                    results.add(result);
                    if (--remaining > 0) return;
                }
                done.onSuccess(results);
            }

            @Override
            public void onError(AppException e) {
                synchronized (Gather.this) {
                    if (failed) return;
                    failed = true;
                }
                done.onError(e);
            }
        };
    }
}
