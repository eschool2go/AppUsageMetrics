package com.opentechlancer.appusagemetrics.model;

/**
 * Created by sandeep on 01-04-2017.
 */

public class AppEvent {
    private String mName;
    private String mPackageName;
    private long mDuration;
    private long mLaunchTimestamp;

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public void setPackageName(String name) {
        mPackageName = name;
    }

    public long getDuration() {
        return mDuration;
    }

    public void setDuration(long duration) {
        mDuration = duration;
    }

    public long getLaunchTimestamp() {
        return mLaunchTimestamp;
    }

    public void setLaunchTimestamp(long launchTimestamp) {
        mLaunchTimestamp = launchTimestamp;
    }
}
