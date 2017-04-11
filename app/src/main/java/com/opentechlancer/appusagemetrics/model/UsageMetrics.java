package com.opentechlancer.appusagemetrics.model;

import android.graphics.drawable.Drawable;

/**
 * Entity class represents usage stats and app icon.
 */
public class UsageMetrics {
    private long mTimeStamp;
    private String mPackageName;
    private Drawable mAppIcon;
    private String mAppName;
    private long mDuration;

    public void setAppIcon(Drawable icon) {
        mAppIcon = icon;
    }

    public void setAppName(String name) {
        mAppName = name;
    }

    public Drawable getAppIcon() {
        return mAppIcon;
    }

    public String getAppName() {
        return mAppName;
    }

    public long getDuration() {
        return mDuration;
    }

    public void setDuration(long duration) {
        this.mDuration = duration;
    }

    public long getTimeStamp() {
        return mTimeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.mTimeStamp = timeStamp;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public void setPackageName(String packageName) {
        this.mPackageName = packageName;
    }
}
