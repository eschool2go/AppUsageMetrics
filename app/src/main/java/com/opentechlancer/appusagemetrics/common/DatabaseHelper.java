package com.opentechlancer.appusagemetrics.common;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.util.Log;

import com.opentechlancer.appusagemetrics.model.AppEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Created by sandeep on 13-03-2017.
 */

/**
 * A helper class for interacting with sqlite database
 */
public class DatabaseHelper {

    private static DatabaseHelper dbHelper;
    private DatabaseHelperInternal mDbHelperInternal;

    private DatabaseHelper(Context context) {
        mDbHelperInternal = new DatabaseHelperInternal(context);
    }

    public static void initialize(Context context) {
        dbHelper = new DatabaseHelper(context.getApplicationContext());
    }

    public static DatabaseHelper getInstance() {
        return dbHelper;
    }

    public void saveEventToDbAsync(String appName, String packageName, String durationString, String launchTimestamp, EventSaveListener listener) {
        new SaveToDbTask(mDbHelperInternal, listener).execute(
                Constants.PAYLOAD_VALUE_VERSION,
                Constants.PAYLOAD_VALUE_CATEGORY,
                Constants.PAYLOAD_VALUE_ACTION,
                appName,
                packageName,
                Constants.PAYLOAD_VALUE_LABEL,
                durationString,
                launchTimestamp);
    }

    public JSONArray retrieveEvents(int count) {
        return mDbHelperInternal.getEvents(count);
    }

    public List<AppEvent> retrieveCustomEvents(long laterThanTimestamp) {
        return mDbHelperInternal.getCustomEvents(laterThanTimestamp);
    }

    public int deleteOldSyncedEvents() {
        return mDbHelperInternal.deleteOldSyncedEvents();
    }

    public int updateSyncedEvents(List<Long> eventIds) {
        return mDbHelperInternal.updateSyncedEvents(eventIds);
    }

    public void release() {
        mDbHelperInternal.close();
    }

    private static class SaveToDbTask extends AsyncTask<String, Void, Long> {

        private WeakReference<DatabaseHelperInternal> mDbHelperInternalRef;
        private WeakReference<EventSaveListener> mListenerRef;

        private SaveToDbTask(DatabaseHelperInternal dbHelperInternal, EventSaveListener listener) {
            mDbHelperInternalRef = new WeakReference<>(dbHelperInternal);
            mListenerRef = new WeakReference<>(listener);
        }

        @Override
        protected Long doInBackground(String... params) {
            long rowId = -1;
            DatabaseHelperInternal dbHelperInternal = mDbHelperInternalRef.get();
            if (dbHelperInternal != null) {
                rowId = dbHelperInternal.insert(params[0], params[1], params[2], params[3], params[4], params[5],
                        Long.valueOf(params[6]), Long.valueOf(params[7]));
            }
            return rowId;
        }

        @Override
        protected void onPostExecute(Long result) {
            EventSaveListener listener = mListenerRef.get();
            if (listener != null) {
                listener.onEventSaved(result);
            }
        }
    }

    private static class DatabaseHelperInternal extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "app_usage_metrics";
        private static final String TABLE_NAME = "app_launch";
        private static final int VERSION = 1;

        private final static String[] columns = {
                Constants.PAYLOAD_KEY_ID,
                Constants.PAYLOAD_KEY_VERSION,
                Constants.PAYLOAD_KEY_CATEGORY,
                Constants.PAYLOAD_KEY_ACTION,
                Constants.PAYLOAD_KEY_LOCATION,
                Constants.COLUMN_PACKAGE_NAME,
                Constants.PAYLOAD_KEY_LABEL,
                Constants.PAYLOAD_KEY_VALUE,
                Constants.PAYLOAD_KEY_CREATED,
                Constants.COLUMN_SYNCED_WITH_SERVER
        };

        private class Column {
            private static final int ID = 0;
            private static final int VERSION = 1;
            private static final int CATEGORY = 2;
            private static final int ACTION = 3;
            private static final int LOCATION = 4;
            private static final int PACKAGE_NAME = 5;
            private static final int LABEL = 6;
            private static final int VALUE = 7;
            private static final int CREATED = 8;
            private static final int SYNCED_WITH_SERVER = 9;
        }

        private DatabaseHelperInternal(Context context) {
            super(context, DATABASE_NAME, null, VERSION, null);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            String createTableSql = "CREATE TABLE IF NOT EXISTS "
                    + TABLE_NAME
                    + " (" + Constants.PAYLOAD_KEY_ID + " INTEGER PRIMARY KEY, "
                    + Constants.PAYLOAD_KEY_VERSION + " TEXT, "
                    + Constants.PAYLOAD_KEY_CATEGORY + " TEXT, "
                    + Constants.PAYLOAD_KEY_ACTION + " TEXT, "
                    + Constants.PAYLOAD_KEY_LOCATION + " TEXT, "
                    + Constants.COLUMN_PACKAGE_NAME + " TEXT, "
                    + Constants.PAYLOAD_KEY_LABEL + " TEXT, "
                    + Constants.PAYLOAD_KEY_VALUE + " INTEGER, "
                    + Constants.PAYLOAD_KEY_CREATED + " INTEGER,"
                    + Constants.COLUMN_SYNCED_WITH_SERVER + " BOOLEAN)";

            db.execSQL(createTableSql);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Do nothing for now
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            // Do nothing for now
        }

        private long insert(String version, String category, String action, String location, String packageName, String label, long value, long created) {
            ContentValues data = new ContentValues();
            data.put(columns[Column.VERSION], version);
            data.put(columns[Column.CATEGORY], category);
            data.put(columns[Column.ACTION], action);
            data.put(columns[Column.LOCATION], location);
            data.put(columns[Column.PACKAGE_NAME], packageName);
            data.put(columns[Column.LABEL], label);
            data.put(columns[Column.VALUE], value);
            data.put(columns[Column.CREATED], created);
            data.put(columns[Column.SYNCED_WITH_SERVER], 0);
            return getWritableDatabase().insert(TABLE_NAME, null, data);
        }

        private JSONArray getEvents(int count) {
            JSONArray eventArray = new JSONArray();
            String[] projection = {
                    columns[Column.ID],
                    columns[Column.VERSION],
                    columns[Column.CATEGORY],
                    columns[Column.ACTION],
                    columns[Column.LOCATION],
                    columns[Column.LABEL],
                    columns[Column.VALUE],
                    columns[Column.CREATED]
            };
            String selection = columns[Column.SYNCED_WITH_SERVER] + " = 0";
            String orderBy = columns[Column.CREATED] + " ASC ";
            String limit = (count > 0) ? String.valueOf(count) : null;
            Cursor cursor = null;
            try {
                cursor = getReadableDatabase().query(true, TABLE_NAME, projection, selection, null, null, null, orderBy, limit);
                while (cursor.moveToNext()) {
                    try {
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put(columns[Column.ID], cursor.getLong(0));
                        jsonObject.put(columns[Column.VERSION], cursor.getString(1));
                        jsonObject.put(columns[Column.CATEGORY], cursor.getString(2));
                        jsonObject.put(columns[Column.ACTION], cursor.getString(3));
                        jsonObject.put(columns[Column.LOCATION], cursor.getString(4));
                        jsonObject.put(columns[Column.LABEL], cursor.getString(5));
                        jsonObject.put(columns[Column.VALUE], cursor.getLong(6));
                        jsonObject.put(columns[Column.CREATED], cursor.getLong(7));
                        eventArray.put(jsonObject);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return eventArray;
        }

        private List<AppEvent> getCustomEvents(long laterThanTimestamp) {
            List<AppEvent> events = new ArrayList<>();
            String orderBy = columns[Column.CREATED] + " DESC ";
            String[] projection = {columns[Column.LOCATION], columns[Column.PACKAGE_NAME], columns[Column.VALUE], columns[Column.CREATED]};
            String selection = columns[Column.CREATED] + " > " + laterThanTimestamp;
            Cursor cursor = getReadableDatabase().query(true, TABLE_NAME, projection, selection, null, null, null, orderBy, null);
            while (cursor.moveToNext()) {
                AppEvent event = new AppEvent();
                event.setName(cursor.getString(0));
                event.setPackageName(cursor.getString(1));
                event.setDuration(cursor.getLong(2));
                event.setLaunchTimestamp(cursor.getLong(3));
                events.add(event);
            }
            cursor.close();
            return events;
        }

        private int deleteOldSyncedEvents() {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MONTH, -1);
            long olderThanTimestamp = calendar.getTimeInMillis();
            String whereClause = columns[Column.CREATED] + " < " + olderThanTimestamp + " AND " + columns[Column.SYNCED_WITH_SERVER] + " = 1";
            return getWritableDatabase().delete(TABLE_NAME, whereClause, null);
        }

        private int updateSyncedEvents(List<Long> eventIds) {
            ContentValues contentValues = new ContentValues(1);
            contentValues.put(columns[Column.SYNCED_WITH_SERVER], 1);
            StringBuilder eventIdsSb = new StringBuilder();
            for (Long id : eventIds) {
                eventIdsSb.append(id).append(",");
            }
            if(eventIdsSb.length() > 0)
                eventIdsSb.deleteCharAt(eventIdsSb.length() - 1);
            String whereClause = columns[Column.ID] + " IN (" + eventIdsSb.toString() + ")";
            return getWritableDatabase().update(TABLE_NAME, contentValues, whereClause, null);
        }
    }

    public interface EventSaveListener {
        void onEventSaved(long id);
    }
}
