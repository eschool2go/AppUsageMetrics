package com.opentechlancer.appusagemetrics.serversyncscheduler;

import android.os.Build;
import android.util.Log;

import com.opentechlancer.appusagemetrics.Constant.SharedPreferencesDB;
import com.opentechlancer.appusagemetrics.common.App;
import com.opentechlancer.appusagemetrics.common.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by sandeep on 11-03-2017.
 */

/**
 * A convenience class for connecting to server, sending data and receiving response
 */
class DataSendingAgent {

    private static final String TAG = "AppUsageMetrics";
    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient mHttpClient;

    DataSendingAgent() {
        mHttpClient = new OkHttpClient();
    }

    public List<String> syncEvents(JSONArray eventArray, String clientCode) throws JSONException, IOException {
        //Log.d(TAG, "#### inside syncEvents");
        String responseBody = null;
        String postBody = buildFullPayload(eventArray, clientCode);
        //Log.d(TAG, "#### payload:" + postBody);
        List<String> responses = new ArrayList<>();

        List<String> addresses = SharedPreferencesDB.getInstance(App.ctx).
                getPreferenceListValue("ips");

        if(addresses.isEmpty()) {
            Log.e("DATASENDAGENT", "NO SERVER");
            addresses.add(Constants.API_ENDPOINT);
        }

        for (String serverAdd : addresses) {
            Log.e("sending  update", "to " + serverAdd);

            final Request request = new Request.Builder()
                    .url(serverAdd)
                    .post(RequestBody.create(MEDIA_TYPE_JSON, postBody))
                    .build();

            Response response = null;
            try {
                response = mHttpClient.newCall(request).execute();
                //Log.d(TAG, "#### response code:" + response.code());
                responseBody = response.body().string();
                Log.e("responcsr", responseBody);
                responses.add(responseBody);
                //Log.d(TAG, "#### response body:" + responseBody);
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }

        return responses;
    }

    private String buildFullPayload(JSONArray events, String clientCode) throws JSONException {
        JSONObject client = new JSONObject();
        client.put(Constants.PAYLOAD_KEY_ID, clientCode);

        JSONObject payload = new JSONObject();
        payload.put(Constants.PAYLOAD_KEY_EVENTS, events);
        payload.put(Constants.PAYLOAD_KEY_CLIENT, client);

        return payload.toString();
    }
}
