package com.opentechlancer.appusagemetrics;

import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.opentechlancer.appusagemetrics.model.UsageMetrics;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

/**
 * Provide views to RecyclerView with the directory entries.
 */
public class UsageListAdapter extends RecyclerView.Adapter<UsageListAdapter.ViewHolder> {

    private List<UsageMetrics> mUsageMetricsList;
    private DateFormat mDateFormat;
    private DateFormat mTimeFormat;
    private Date mReusableDate;

    private static final String FORMAT_DURATION_HH_MM_SS = "%1$02d hours %2$02d minutes %3$02d seconds";
    private static final String FORMAT_DURATION_MM_SS = "%1$02d minutes %2$02d seconds";
    private static final String FORMAT_DURATION_SS = "%1$02d seconds";

    private static final int ROW_TYPE_USAGE_STAT = 100;
    private static final int ROW_TYPE_DATE = 101;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView mAppName;
        private final TextView mLastTimeUsed;
        private final TextView mDurationUsed;
        private final ImageView mAppIcon;
        private final TextView mDate;

        public ViewHolder(View v) {
            super(v);
            mAppName = (TextView) v.findViewById(R.id.textview_app_name);
            mLastTimeUsed = (TextView) v.findViewById(R.id.textview_last_time_used);
            mDurationUsed = (TextView) v.findViewById(R.id.textview_duration_used);
            mAppIcon = (ImageView) v.findViewById(R.id.app_icon);
            mDate = (TextView) v.findViewById(R.id.textview_date);
        }

        public TextView getLastTimeUsed() {
            return mLastTimeUsed;
        }

        public TextView getDurationUsed() {
            return mDurationUsed;
        }

        public TextView getAppName() {
            return mAppName;
        }

        public ImageView getAppIcon() {
            return mAppIcon;
        }

        public TextView getDate() {
            return mDate;
        }
    }

    public UsageListAdapter() {
        mUsageMetricsList = new ArrayList<>();
        mDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        mTimeFormat = new SimpleDateFormat("hh:mm aa", Locale.getDefault());
        mReusableDate = new Date();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View v;
        if (viewType == ROW_TYPE_DATE) {
            v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.date_row, viewGroup, false);
        } else {
            v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.usage_row, viewGroup, false);
        }
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        if (getItemViewType(position) == ROW_TYPE_USAGE_STAT) {
            UsageMetrics usageMetrics = mUsageMetricsList.get(position);
            viewHolder.getAppName().setText(usageMetrics.getAppName());
            long lastTimeUsed = usageMetrics.getTimeStamp();
            mReusableDate.setTime(lastTimeUsed);
            viewHolder.getLastTimeUsed().setText(mTimeFormat.format(mReusableDate));
            long timeInForeground = usageMetrics.getDuration();
            viewHolder.getDurationUsed().setText(formatDuration(timeInForeground));
            viewHolder.getAppIcon().setImageDrawable(usageMetrics.getAppIcon());
        } else {
            if (position == 0) {
                viewHolder.getDate().setText(viewHolder.itemView.getContext().getString(R.string.text_today));
                return;
            }
            UsageMetrics usageMetrics = mUsageMetricsList.get(position + 1);
            long lastTimeUsed = usageMetrics.getTimeStamp();
            mReusableDate.setTime(lastTimeUsed);
            viewHolder.getDate().setText(mDateFormat.format(mReusableDate));
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (TextUtils.isEmpty(mUsageMetricsList.get(position).getPackageName())) {
            return ROW_TYPE_DATE;
        }
        return ROW_TYPE_USAGE_STAT;
    }

    @Override
    public int getItemCount() {
        return mUsageMetricsList.size();
    }

    public void setUsageMetricsList(List<UsageMetrics> usageMetricsStats) {
        mUsageMetricsList = usageMetricsStats;
    }

    private String formatDuration(long durationInSeconds) {
        long hours = 0;
        long minutes = 0;
        long seconds = 0;
        if (durationInSeconds >= 3600) {
            hours = durationInSeconds / 3600;
            durationInSeconds -= hours * 3600;
        }
        if (durationInSeconds >= 60) {
            minutes = durationInSeconds / 60;
            durationInSeconds -= minutes * 60;
        }
        seconds = durationInSeconds;


        Formatter formatter = new Formatter(Locale.getDefault());
        if (hours > 0) {
            return formatter.format(FORMAT_DURATION_HH_MM_SS, hours, minutes, seconds).toString();
        } else if (minutes > 0) {
            return formatter.format(FORMAT_DURATION_MM_SS, minutes, seconds).toString();
        } else {
            return formatter.format(FORMAT_DURATION_SS, seconds).toString();
        }
    }
}