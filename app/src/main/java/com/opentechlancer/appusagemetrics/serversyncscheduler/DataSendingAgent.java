package com.opentechlancer.appusagemetrics.serversyncscheduler;

import android.util.Log;

import com.opentechlancer.appusagemetrics.common.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

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

    public String syncEvents(JSONArray eventArray, String clientCode) throws JSONException, IOException {
        //Log.d(TAG, "#### inside syncEvents");
        String responseBody = null;
        String postBody = buildFullPayload(eventArray, clientCode);
        //Log.d(TAG, "#### payload:" + postBody);
        final Request request = new Request.Builder()
                .url(Constants.API_ENDPOINT)
                .post(RequestBody.create(MEDIA_TYPE_JSON, postBody))
                .build();

        Response response = null;
        try {
            response = mHttpClient.newCall(request).execute();
            //Log.d(TAG, "#### response code:" + response.code());
            responseBody = response.body().string();
            //Log.d(TAG, "#### response body:" + responseBody);
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return responseBody;
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
