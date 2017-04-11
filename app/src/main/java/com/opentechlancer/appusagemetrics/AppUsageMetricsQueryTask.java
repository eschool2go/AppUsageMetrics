package com.opentechlancer.appusagemetrics;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;

import com.opentechlancer.appusagemetrics.common.DatabaseHelper;
import com.opentechlancer.appusagemetrics.model.AppEvent;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by sandeep on 08-03-2017.
 */

/**
 * This class has APIs to query usage events and data from sqlite db asynchronously
 */
public class AppUsageMetricsQueryTask {

    private UsageStatsManager mUsageStatsManager;
    private WeakReference<AppUsageMetricsQueryTaskListener> mListenerRef;
    private EventTimestampComparatorDesc mEventTimestampComparatorDesc;
    private long mStartTime;
    private long mEndTime;
    private AppUsageEventsQueryTask mUsageEventsQueryTask;
    private AppUsageEventListQueryTask mUsageEventListQueryTask;

    public AppUsageMetricsQueryTask(Context context) {
        String usageStatsServiceName = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) ? Context.USAGE_STATS_SERVICE : "usagestats";
        mUsageStatsManager = (UsageStatsManager) context.getSystemService(usageStatsServiceName);
        mEventTimestampComparatorDesc = new EventTimestampComparatorDesc();
        mStartTime = -1;
        mEndTime = -1;
    }

    public void setAppUsageMetricsQueryTaskListener(AppUsageMetricsQueryTaskListener listener) {
        mListenerRef = new WeakReference<>(listener);
    }

    public void queryUsageEvents(long startTime, long endTime) {
        mStartTime = startTime;
        mEndTime = endTime;
        mUsageEventsQueryTask = new AppUsageEventsQueryTask();
        mUsageEventsQueryTask.execute();
    }

    public void queryUsageEventList(long startTime) {
        mStartTime = startTime;
        mUsageEventListQueryTask = new AppUsageEventListQueryTask();
        mUsageEventListQueryTask.execute();
    }

    public void cancelAllTasks() {
        if (mUsageEventsQueryTask != null) {
            mUsageEventsQueryTask.cancel(true);
        }

        if (mUsageEventListQueryTask != null) {
            mUsageEventListQueryTask.cancel(true);
        }
    }

    /**
     * Returns the usage events between mStartTime and mEndTime
     *
     * @return A list of {@link android.app.usage.UsageEvents}.
     */
    private UsageEvents getUsageEvents() {
        if (mStartTime == -1 || mEndTime == -1) {
            // Get the app statistics since 3 months ago from the current time.
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MONTH, -3);
            mStartTime = cal.getTimeInMillis();
            mEndTime = System.currentTimeMillis();
        }

        UsageEvents usageEvents = mUsageStatsManager.queryEvents(mStartTime, mEndTime);
        return usageEvents;
    }

    private List<AppEvent> getCustomEvents() {
        if (mStartTime == -1) {
            // Get the app statistics since 1 month ago from the current time.
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MONTH, -1);
            mStartTime = cal.getTimeInMillis();
        }

        return DatabaseHelper.getInstance().retrieveCustomEvents(mStartTime);
    }

    private class AppUsageEventsQueryTask extends AsyncTask<Void, Void, UsageEvents> {
        @Override
        protected UsageEvents doInBackground(Void... params) {
            return getUsageEvents();
        }

        @Override
        protected void onPostExecute(UsageEvents usageEvents) {
            if (mListenerRef == null) {
                return;
            }
            AppUsageMetricsQueryTaskListener listener = mListenerRef.get();
            if (listener != null) {
                listener.onAppUsageEventsQueryTaskCompleted(usageEvents);
            }
        }
    }

    private class AppUsageEventListQueryTask extends AsyncTask<Void, Void, List<AppEvent>> {
        @Override
        protected List<AppEvent> doInBackground(Void... params) {
            List<AppEvent> eventList = getCustomEvents();

            if (eventList.size() > 0) {
                Collections.sort(eventList, mEventTimestampComparatorDesc);
            }
            return eventList;
        }

        @Override
        protected void onPostExecute(List<AppEvent> eventList) {
            if (mListenerRef == null) {
                return;
            }
            AppUsageMetricsQueryTaskListener listener = mListenerRef.get();
            if (listener != null) {
                listener.onCustomEventListQueryTaskCompleted(eventList);
            }
        }
    }

    /**
     * The {@link Comparator} to sort a collection of {@link AppEvent} sorted by the launch timestamp
     * in the descendant order.
     */
    private static class EventTimestampComparatorDesc implements Comparator<AppEvent> {
        @Override
        public int compare(AppEvent left, AppEvent right) {
            return Long.compare(right.getLaunchTimestamp(), left.getLaunchTimestamp());
        }
    }

    public interface AppUsageMetricsQueryTaskListener {
        void onAppUsageEventsQueryTaskCompleted(UsageEvents usageEvents);

        void onCustomEventListQueryTaskCompleted(List<AppEvent> eventList);
    }
}
