package com.team23.trojanrideshare.ui;

import android.app.Application;

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
}
