package com.team23.trojanrideshare.ui;

import android.app.Application;

import androidx.annotation.VisibleForTesting;

/** Creates the {@link AppContainer} once for the whole app. */
public class TrojanRideshareApp extends Application {

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
    }

    public AppContainer container() {
        return container;
    }

    /** Instrumented tests swap in a container backed by a test database (see AppContainer). */
    @VisibleForTesting
    public void replaceContainer(AppContainer testContainer) {
        container = testContainer;
    }
}
