package com.opentechlancer.appusagemetrics.serversyncscheduler;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

/**
 * Created by sandeep on 16/03/2017.
 */

/**
 * A helper for scheduling server sync job
 */
public class JobSchedulerHelper {

    private static final String TAG = "AppUsageMetrics";

    private static final int SERVER_SYNC_JOB_ID = 10;
    private static final int JOB_RUNNING_INTERVAL = 15 * 60 * 1000; // attempt to run the sync task every 15 mins

    private Context mContext;
    private JobScheduler mJobScheduler;

    public JobSchedulerHelper(Context context) {
        mContext = context;
        mJobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    public void scheduleServerSyncJob() {
        JobInfo.Builder jobInfoBuilder = new JobInfo.Builder(SERVER_SYNC_JOB_ID, new ComponentName(mContext,
                ServerSyncService.class));
        jobInfoBuilder.setPeriodic(JOB_RUNNING_INTERVAL);
        jobInfoBuilder.setPersisted(true);
        jobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        mJobScheduler.schedule(jobInfoBuilder.build());
    }

    public void cancelServerSyncJob() {
        mJobScheduler.cancel(SERVER_SYNC_JOB_ID);
    }
}
