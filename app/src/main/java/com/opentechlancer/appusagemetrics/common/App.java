package com.opentechlancer.appusagemetrics.common;

import android.app.Application;

/**
 * Created by sandeep on 13-03-2017.
 */

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        DatabaseHelper.initialize(this);
    }
}
