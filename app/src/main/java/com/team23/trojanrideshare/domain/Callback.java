package com.team23.trojanrideshare.domain;

/**
 * Asynchronous result of a service, repository, or gateway call.
 * Exactly one of the two methods is called, on the main thread in the app.
 */
public interface Callback<T> {
    void onSuccess(T result);

    void onError(AppException e);
}
