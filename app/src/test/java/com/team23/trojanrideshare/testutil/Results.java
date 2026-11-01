package com.team23.trojanrideshare.testutil;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;

/** Captures a callback's outcome. The fakes call back synchronously. */
public class Results<T> implements Callback<T> {

    public T value;
    public AppException error;
    public boolean called;

    @Override
    public void onSuccess(T result) {
        called = true;
        value = result;
    }

    @Override
    public void onError(AppException e) {
        called = true;
        error = e;
    }

    public T get() {
        if (error != null) throw new AssertionError("Expected success but got: " + error.getMessage(), error);
        if (!called) throw new AssertionError("Callback was not called");
        return value;
    }

    public AppException err() {
        if (error == null) throw new AssertionError("Expected an error but the call succeeded");
        return error;
    }
}
