package com.team23.trojanrideshare.ui.common;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team23.trojanrideshare.domain.AppException;
import com.team23.trojanrideshare.domain.Callback;
import com.team23.trojanrideshare.ui.AppContainer;
import com.team23.trojanrideshare.ui.TrojanRideshareApp;

import java.util.function.Consumer;

/** Shared plumbing for ViewModels: access to services, a busy flag, errors, and messages. */
public abstract class BaseViewModel extends AndroidViewModel {

    protected final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    protected final MutableLiveData<Event<AppException>> error = new MutableLiveData<>();
    protected final MutableLiveData<Event<String>> message = new MutableLiveData<>();
    private int running;

    protected BaseViewModel(@NonNull Application app) {
        super(app);
    }

    protected AppContainer app() {
        return ((TrojanRideshareApp) getApplication()).container();
    }

    public LiveData<Boolean> busy() {
        return busy;
    }

    public LiveData<Event<AppException>> error() {
        return error;
    }

    public LiveData<Event<String>> message() {
        return message;
    }

    protected void say(String text) {
        message.setValue(new Event<>(text));
    }

    /** Marks the screen busy until the returned callback fires; errors go to {@link #error()}. */
    protected <T> Callback<T> call(Consumer<T> onSuccess) {
        return call(onSuccess, null);
    }

    protected <T> Callback<T> call(Consumer<T> onSuccess, Consumer<AppException> onFailure) {
        running++;
        busy.setValue(true);
        return new Callback<T>() {
            @Override
            public void onSuccess(T result) {
                done();
                onSuccess.accept(result);
            }

            @Override
            public void onError(AppException e) {
                done();
                if (onFailure != null) onFailure.accept(e);
                else error.setValue(new Event<>(e));
            }
        };
    }

    private void done() {
        running = Math.max(0, running - 1);
        busy.setValue(running > 0);
    }
}
