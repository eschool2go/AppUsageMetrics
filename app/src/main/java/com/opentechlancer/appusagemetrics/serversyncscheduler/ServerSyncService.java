package com.opentechlancer.appusagemetrics.serversyncscheduler;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import com.opentechlancer.appusagemetrics.R;
import com.opentechlancer.appusagemetrics.common.Constants;
import com.opentechlancer.appusagemetrics.common.DatabaseHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by sandeep on 16/03/2017.
 */

/**
 * This service manages periodic syncing of events from sqlite db
 * to server
 */
public class ServerSyncService extends JobService {

    private static final String TAG = "AppUsageMetrics";
    private ServerSyncTask mServerSyncTask;
    private JobParameters mJobParameters;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        mJobParameters = jobParameters;
        if (mServerSyncTask == null ||
                (mServerSyncTask.getStatus() != AsyncTask.Status.RUNNING && mServerSyncTask.getStatus() != AsyncTask.Status.PENDING)) {
            String clientCode = PreferenceManager.getDefaultSharedPreferences(this)
                    .getString(getString(R.string.pref_key_client_code), getString(R.string.pref_default_client_code));
            mServerSyncTask = new ServerSyncTask(this);
            mServerSyncTask.execute(clientCode);
        }
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        if (mServerSyncTask != null && mServerSyncTask.getStatus() == AsyncTask.Status.RUNNING) {
            mServerSyncTask.cancel(true);
        }
        return false;
    }

    private void serverSyncTaskFinished() {
        jobFinished(mJobParameters, false);
    }

    private static class ServerSyncTask extends AsyncTask<String, Void, Void> {
        private WeakReference<ServerSyncService> mServiceRef;

        private ServerSyncTask(ServerSyncService serverSyncService) {
            mServiceRef = new WeakReference<>(serverSyncService);
        }

        @Override
        protected Void doInBackground(String... params) {
            DatabaseHelper dbHelper = DatabaseHelper.getInstance();

            try {
                // get the specified number of records sorted by increasing order of timestamp
                JSONArray jsonizedEvents = dbHelper.retrieveEvents(Constants.MAX_EVENTS_TO_SYNC);
                if (jsonizedEvents.length() == 0) {
                    //Log.d(TAG, "#### no pending events to sync");
                    return null;
                }

                // send to server

                DataSendingAgent agent = new DataSendingAgent();
                String clientCode = params[0];
                List<String> responseBody = agent.syncEvents(jsonizedEvents, clientCode);

                // parse server response
                List<Long> eventIds = parseResponse(responseBody);

                // update synced status as true in db for ids in server response
                int count = dbHelper.updateSyncedEvents(eventIds);
                //Log.d(TAG, "#### records with sync status updated:" + count);

                // delete synced records older than 1 month from db
                count = dbHelper.deleteOldSyncedEvents();
                //Log.d(TAG, "#### records deleted:" + count);
            } catch (JSONException | IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            ServerSyncService service = mServiceRef.get();
            if (service != null) {
                service.serverSyncTaskFinished();
            }
        }

        private List<Long> parseResponse(List<String> responseBodies) throws JSONException {
            ArrayList<Long> receivedEvents = new ArrayList<>();

            //Log.d(TAG, "#### inside parseResponse");
            for(String responseBody: responseBodies) {
                JSONObject responseObj = new JSONObject(responseBody);
                String status = responseObj.getString(Constants.RESPONSE_KEY_STATUS);
                if (Constants.RESPONSE_VALUE_SUCCESS.equals(status)) {
                    JSONArray events = responseObj.getJSONArray(Constants.RESPONSE_KEY_RECEIVED);
                    if (events != null && events.length() > 0) {
                        for (int i = 0; i < events.length(); i++) {
                            receivedEvents.add(events.getLong(i));
                        }
                    }
                }
                return receivedEvents;
            }

            return new ArrayList<>();
        }
    }
}
