package com.opentechlancer.appusagemetrics.common;

/**
 * Created by sandeep on 13-03-2017.
 */

public class Constants {
    public static final String PAYLOAD_KEY_EVENTS = "events";
    public static final String PAYLOAD_KEY_ID = "id";
    public static final String PAYLOAD_KEY_VERSION = "version";
    public static final String PAYLOAD_KEY_CATEGORY = "category";
    public static final String PAYLOAD_KEY_ACTION = "action";
    public static final String PAYLOAD_KEY_LOCATION = "location";
    public static final String PAYLOAD_KEY_LABEL = "label";
    public static final String PAYLOAD_KEY_VALUE = "value";
    public static final String PAYLOAD_KEY_CREATED = "created";
    public static final String PAYLOAD_KEY_CLIENT = "client";

    public static final String COLUMN_PACKAGE_NAME = "package_name";
    public static final String COLUMN_SYNCED_WITH_SERVER = "synced_with_server";

    public static final String PAYLOAD_VALUE_VERSION = "1.0";
    public static final String PAYLOAD_VALUE_CATEGORY = "application";
    public static final String PAYLOAD_VALUE_ACTION = "launch";
    public static final String PAYLOAD_VALUE_LABEL = "duration";

    public static final String RESPONSE_KEY_STATUS = "status";
    public static final String RESPONSE_KEY_RECEIVED = "recieved";

    public static final String RESPONSE_VALUE_SUCCESS = "success";

    public static final String INTENT_APP_EVENT_ADDED = "com.opentechlancer.appusagemetrics.action.app_event_added";

    public static final int MAX_EVENTS_TO_SYNC = 15;

    public static final String API_ENDPOINT = "http://eschool2go.org/api/v1/events";
}
