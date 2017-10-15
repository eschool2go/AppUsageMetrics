package com.opentechlancer.appusagemetrics.common;

import android.app.Application;
import android.content.Context;

/**
 * Created by sandeep on 13-03-2017.
 */

public class App extends Application {
    public static Context ctx;

    @Override
    public void onCreate() {
        super.onCreate();
        ctx = getApplicationContext();

        DatabaseHelper.initialize(this);
    }
}
