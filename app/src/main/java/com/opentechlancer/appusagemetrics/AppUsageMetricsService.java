package com.opentechlancer.appusagemetrics;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.opentechlancer.appusagemetrics.Constant.ConnectionUtils;
import com.opentechlancer.appusagemetrics.Constant.CreateStreamServer;
import com.opentechlancer.appusagemetrics.Constant.NsdHelper;
import com.opentechlancer.appusagemetrics.Constant.SharedPreferencesDB;
import com.opentechlancer.appusagemetrics.common.App;
import com.opentechlancer.appusagemetrics.common.Constants;
import com.opentechlancer.appusagemetrics.common.DatabaseHelper;
import com.opentechlancer.appusagemetrics.model.AppEvent;
import com.opentechlancer.appusagemetrics.serversyncscheduler.JobSchedulerHelper;

import java.io.IOException;
import java.util.List;

/**
 * This service is responsible for periodic querying of usage events and saving relevant details
 * about foreground apps in sqlite db
 */
public class AppUsageMetricsService extends Service {

    private PowerManager mPowerManager;
    private NotificationManager mNotificationManager;
    private Notification mNotification;
    private Handler mUiThreadHandler;
    private Handler mMonitorThreadHandler;
    private HandlerThread mMonitorThread;

    private String MASTER_SERVICE_TYPE = "_master._tcp";

    /**
     * The last launched (i.e. foreground) app's package name
     */
    private String mLastLaunchedPackageName;

    private long mLastLaunchedPackageTime;
    private UsageEvents.Event mUsageEvent;
    private AppUsageMetricsQueryTask mAppUsageMetricsQueryTask;
    private boolean mIsMonitoring;
    private JobSchedulerHelper mJobSchedulerHelper;
    private static final int NOTIFICATION_ID = 100;
    private static final long DELAY = 6 * 1000;  // in milliseconds

    private static final String IGNORED_PACKAGES_REGEX = "com\\.android\\.systemui";
    NsdHelper helper;

    @Override
    public void onCreate() {
        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mLastLaunchedPackageName = null;
        mLastLaunchedPackageTime = 0;
        mUiThreadHandler = new Handler();
        mMonitorThread = new HandlerThread("AppUsageMonitorThread") {
            @Override
            protected void onLooperPrepared() {
                mMonitorThreadHandler = new Handler(getLooper());
            }
        };
        mMonitorThread.start();
        mUsageEvent = new UsageEvents.Event();
        mAppUsageMetricsQueryTask = new AppUsageMetricsQueryTask(AppUsageMetricsService.this);
        mAppUsageMetricsQueryTask.setAppUsageMetricsQueryTaskListener(mListener);
        mIsMonitoring = false;
        mJobSchedulerHelper = new JobSchedulerHelper(this);

        helper = new NsdHelper(this);
        helper.initializeNsd();

        if(SharedPreferencesDB.getInstance(this).getPreferenceBooleanValue("isMaster"
                , true)) {
            try {
                startServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        registerScreenStateReceiver();
        buildNotification();
        showNotification();
        startAppEventMonitoring();
        scheduleServerSyncJob();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterScreenStateReceiver();
        mAppUsageMetricsQueryTask.cancelAllTasks();
        mUiThreadHandler.removeCallbacksAndMessages(null);
        stopAppEventMonitoring();
        mMonitorThread.quit();
        cancelServerSyncJob();
        DatabaseHelper.getInstance().release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void buildNotification() {
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
        notificationBuilder.setSmallIcon(R.drawable.ic_launcher_round);
        notificationBuilder.setContentTitle(getString(R.string.app_name));
        notificationBuilder.setContentIntent(getAppLaunchIntent());
        notificationBuilder.setShowWhen(false);
        notificationBuilder.setPriority(NotificationCompat.PRIORITY_MIN);
        notificationBuilder.setOngoing(true);
        mNotification = notificationBuilder.build();
    }

    private PendingIntent getAppLaunchIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void showNotification() {
        if (mNotification != null) {
            mNotificationManager.notify(NOTIFICATION_ID, mNotification);
        }
    }

    private void registerScreenStateReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, intentFilter);
    }

    private void unregisterScreenStateReceiver() {
        unregisterReceiver(mScreenStateReceiver);
    }

    private void startAppEventMonitoring() {
        if (!mIsMonitoring) {
            mIsMonitoring = true;
            mMonitorThreadHandler.post(mAppUsageQueryRunnable);
        }
    }

    private void stopAppEventMonitoring() {
        if (mIsMonitoring) {
            mIsMonitoring = false;
        }
    }

    private void continueAppEventMonitoring() {
        // check if device is interactive mode
        mIsMonitoring = isInteractive();
        if (mIsMonitoring) {
            mMonitorThreadHandler.postDelayed(mAppUsageQueryRunnable, DELAY);
        }
    }

    private void restartAppEventMonitoring() {
        stopAppEventMonitoring();
        startAppEventMonitoring();
    }

    private void scheduleServerSyncJob() {
        mJobSchedulerHelper.scheduleServerSyncJob();
    }

    private void cancelServerSyncJob() {
        mJobSchedulerHelper.cancelServerSyncJob();
    }

    private boolean isInteractive() {
        return mPowerManager.isInteractive();
    }

    private void processAppEvent() {
        try {
            //Log.d("AppUsageMetrics", "#### processAppEvent().package:" + mUsageEvent.getPackageName() + ",event:" + mUsageEvent.getEventType());
            String packageName = mUsageEvent.getPackageName();

            /** When device is no more interactive i.e., screen is off, the current app in foreground goes to background.
             We check if if the current event being processed {@link mUsageEvent} corresponds to {@link mLastLaunchedPackageName}.
             If true, then the current app in foreground has moved to background. Hence, we save its timestamp and duration in database
             */
            if (!isInteractive() && packageName.equals(mLastLaunchedPackageName) &&
                    mUsageEvent.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                long duration = (mLastLaunchedPackageTime > 0) ? Math.abs(mUsageEvent.getTimeStamp() - mLastLaunchedPackageTime) / 1000 : 0;
                if (!TextUtils.isEmpty(mLastLaunchedPackageName)) {
                    ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(mLastLaunchedPackageName,
                            PackageManager.GET_META_DATA);
                    final CharSequence label = getPackageManager().getApplicationLabel(applicationInfo);
                    String appName = (label == null) ? mLastLaunchedPackageName : label.toString();
                    DatabaseHelper.getInstance().saveEventToDbAsync(appName, mLastLaunchedPackageName, String.valueOf(duration),
                            String.valueOf(mLastLaunchedPackageTime), mEventSaveListener);
                }
                mLastLaunchedPackageTime = 0;
                mLastLaunchedPackageName = null;
            }
            /** Foreground and background transitions happen in pairs. That is, when one app moves to foreground,
             * the current foreground app moves to background. When this happens, we will save the last launched app's launch timestamp
             * and duration to the database. We do not yet know the duration of the app that just moved to foreground because it is still
             * in foreground. Its details will be saved the next time some other app moves to foreground and this app moves to background.
             * We will update {@link mLastLaunchedPackageName} after the last app's details are saved in database
             */
            else if (/*!packageName.matches(IGNORED_PACKAGES_REGEX) &&*/ !packageName.equals(mLastLaunchedPackageName) &&
                    mUsageEvent.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                long duration = (mLastLaunchedPackageTime > 0) ? Math.abs(mUsageEvent.getTimeStamp() - mLastLaunchedPackageTime) / 1000 : 0;
                mLastLaunchedPackageTime = mUsageEvent.getTimeStamp();
                if (!TextUtils.isEmpty(mLastLaunchedPackageName)) {
                    ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(mLastLaunchedPackageName,
                            PackageManager.GET_META_DATA);
                    final CharSequence label = getPackageManager().getApplicationLabel(applicationInfo);
                    String appName = (label == null) ? mLastLaunchedPackageName : label.toString();
                    DatabaseHelper.getInstance().saveEventToDbAsync(appName, mLastLaunchedPackageName, String.valueOf(duration),
                            String.valueOf(mLastLaunchedPackageTime), mEventSaveListener);
                }
                mLastLaunchedPackageName = packageName;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private DatabaseHelper.EventSaveListener mEventSaveListener = new DatabaseHelper.EventSaveListener() {
        @Override
        public void onEventSaved(long id) {
            LocalBroadcastManager.getInstance(AppUsageMetricsService.this).sendBroadcast(new Intent(Constants.INTENT_APP_EVENT_ADDED));
        }
    };

    private Runnable mAppUsageQueryRunnable = new Runnable() {
        @Override
        public void run() {
            mUiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    long currentTime = System.currentTimeMillis();
                    mAppUsageMetricsQueryTask.queryUsageEvents(currentTime - DELAY, currentTime);
                }
            });
        }
    };

    private BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                restartAppEventMonitoring();
                return;
            }

            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                stopAppEventMonitoring();
                return;
            }
        }
    };

    private AppUsageMetricsQueryTask.AppUsageMetricsQueryTaskListener mListener = new AppUsageMetricsQueryTask.AppUsageMetricsQueryTaskListener() {
        @Override
        public void onCustomEventListQueryTaskCompleted(List<AppEvent> eventList) {
        }

        @Override
        public void onAppUsageEventsQueryTaskCompleted(UsageEvents usageEvents) {
            // Process the usage events one by one.
            while (usageEvents != null && usageEvents.getNextEvent(mUsageEvent)) {
                processAppEvent();
            }

            /** After all events are processed, schedule the next query for usage events after {@link DELAY}
             */
            continueAppEventMonitoring();
        }
    };

    private void startServer() throws IOException {
        CreateStreamServer server = new CreateStreamServer(this);
        helper.registerService(ConnectionUtils.getPort(this));
    }
}
