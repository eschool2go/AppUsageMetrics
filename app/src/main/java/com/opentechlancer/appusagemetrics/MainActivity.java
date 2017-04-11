package com.opentechlancer.appusagemetrics;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.opentechlancer.appusagemetrics.model.AppEvent;
import com.opentechlancer.appusagemetrics.model.UsageMetrics;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static com.opentechlancer.appusagemetrics.common.Constants.INTENT_APP_EVENT_ADDED;

public class MainActivity extends AppCompatActivity implements AppUsageMetricsQueryTask.AppUsageMetricsQueryTaskListener {

    private static final String TAG = "AppUsageMetrics";

    private UsageListAdapter mUsageListAdapter;
    private RecyclerView mRecyclerView;
    private ProgressBar mProgressBar;
    private TextView mNoStatsTextView;
    private AppUsageMetricsQueryTask mAppUsageMetricsQueryTask;
    private long mCurrentAppLaunchTimestamp;
    private AlertDialog mGrantAppUsageAccessDialog;

    private static final int REQUEST_ACTIVITY_ALLOW_USAGE_ACCESS = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);

        mNoStatsTextView = (TextView) findViewById(R.id.no_usage_stats_text_view);

        mUsageListAdapter = new UsageListAdapter();
        mRecyclerView = (RecyclerView) findViewById(R.id.recyclerview_app_usage);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.scrollToPosition(0);
        mRecyclerView.setAdapter(mUsageListAdapter);

        Intent intent = new Intent(this, AppUsageMetricsService.class);
        startService(intent);

        mAppUsageMetricsQueryTask = new AppUsageMetricsQueryTask(this);
        mAppUsageMetricsQueryTask.setAppUsageMetricsQueryTaskListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mCurrentAppLaunchTimestamp = System.currentTimeMillis();

        registerAppEventAddedBroadcast();

        if (isAppUsageAccessGranted()) {
            queryUsageMetrics();
        } else {
            showDialogToGrantAppUsageAccess();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterAppEventAddedBroadcast();
        hideGrantAppUsageAccessDialog();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_item_refresh) {
            queryUsageMetrics();
            return true;
        }
        if (id == R.id.menu_item_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onAppUsageEventsQueryTaskCompleted(UsageEvents usageEvents) {
    }

    @Override
    public void onCustomEventListQueryTaskCompleted(List<AppEvent> eventList) {
        customEventListQueryCompleted(eventList);
    }

    private void showAppUsageAccessSettings() {
        startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), REQUEST_ACTIVITY_ALLOW_USAGE_ACCESS);
    }

    private void showDialogToGrantAppUsageAccess() {
        if (mGrantAppUsageAccessDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.app_usage_access_dialog_title).setMessage(R.string.app_usage_access_dialog_message);
            builder.setPositiveButton(R.string.text_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    showAppUsageAccessSettings();
                }
            });
            builder.setNegativeButton(R.string.text_exit, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            mGrantAppUsageAccessDialog = builder.create();
        }
        mGrantAppUsageAccessDialog.show();
    }

    private void hideGrantAppUsageAccessDialog() {
        if (mGrantAppUsageAccessDialog != null) {
            mGrantAppUsageAccessDialog.dismiss();
        }
    }

    private boolean isAppUsageAccessGranted() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -2);
        List<UsageStats> statses = ((UsageStatsManager) getSystemService("usagestats")).queryUsageStats(UsageStatsManager.INTERVAL_DAILY, calendar.getTimeInMillis(), System.currentTimeMillis());
        return statses.size() != 0;
    }

    private void queryUsageMetrics() {
        hideNoStatsText();
        invalidateOptionsMenu();
        showProgressBar();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -1);
        mAppUsageMetricsQueryTask.queryUsageEventList(calendar.getTimeInMillis());
    }

    private void customEventListQueryCompleted(List<AppEvent> eventList) {
        invalidateOptionsMenu();
        hideProgressBar();
        if (eventList == null || eventList.isEmpty()) {
            showNoStatsText();
            return;
        }
        updateCustomEventsList(eventList);
    }

    private void updateCustomEventsList(List<AppEvent> eventList) {
        List<UsageMetrics> usageMetricsList = new ArrayList<>();
        usageMetricsList.add(new UsageMetrics());

        // Hack to add current app to the display list. This is a workaround because db does not yet
        // have the current app's duration of use
        AppEvent currentAppEvent = new AppEvent();
        currentAppEvent.setPackageName(getPackageName());
        currentAppEvent.setName(getString(R.string.app_name));
        currentAppEvent.setLaunchTimestamp(mCurrentAppLaunchTimestamp);
        currentAppEvent.setDuration((System.currentTimeMillis() - mCurrentAppLaunchTimestamp) / 1000);
        eventList.add(0, currentAppEvent);

        for (int i = 0; i < eventList.size(); i++) {
            UsageMetrics usageMetrics = new UsageMetrics();
            AppEvent event = eventList.get(i);
            String packageName = event.getPackageName();
            usageMetrics.setPackageName(packageName);
            usageMetrics.setAppName(event.getName());
            try {
                ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA);
                Drawable appIcon = getPackageManager().getApplicationIcon(packageName);
                usageMetrics.setAppIcon(appIcon);
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, String.format("App Icon is not found for %s", packageName));
                usageMetrics.setAppIcon(getDrawable(R.mipmap.ic_launcher_round));
            }

            usageMetrics.setDuration(event.getDuration());
            usageMetrics.setTimeStamp(event.getLaunchTimestamp());
            usageMetricsList.add(usageMetrics);

            if (i < eventList.size() - 1) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(usageMetrics.getTimeStamp());
                int currentDay = calendar.get(Calendar.DAY_OF_MONTH);
                long nextItemLastTimeUsed = eventList.get(i + 1).getLaunchTimestamp();
                calendar.setTimeInMillis(nextItemLastTimeUsed);
                int nextDay = calendar.get(Calendar.DAY_OF_MONTH);
                if (currentDay != nextDay) {
                    usageMetricsList.add(new UsageMetrics());
                }
            }
        }
        mUsageListAdapter.setUsageMetricsList(usageMetricsList);
        mUsageListAdapter.notifyDataSetChanged();
        mRecyclerView.scrollToPosition(0);
    }

    private void showProgressBar() {
        mProgressBar.setVisibility(View.VISIBLE);
    }

    private void hideProgressBar() {
        mProgressBar.setVisibility(View.GONE);
    }

    private void showNoStatsText() {
        mNoStatsTextView.setVisibility(View.VISIBLE);
    }

    private void hideNoStatsText() {
        mNoStatsTextView.setVisibility(View.GONE);
    }

    private void registerAppEventAddedBroadcast() {
        IntentFilter filter = new IntentFilter(INTENT_APP_EVENT_ADDED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mAppEventAddedReceiver, filter);
    }

    private void unregisterAppEventAddedBroadcast() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mAppEventAddedReceiver);
    }

    private BroadcastReceiver mAppEventAddedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    queryUsageMetrics();
                }
            });
        }
    };
}
