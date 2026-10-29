package com.rsspro.notify;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import com.rsspro.store.Store;

public final class Scheduler {
    private static final String TAG = "RSSProScheduler";
    private static final int JOB_ID = 7331;

    private Scheduler() {
    }

    public static void schedule(Context context) {
        Store store = new Store(context);
        int minutes = Math.max(15, store.getSyncIntervalMinutes());
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        scheduler.cancel(JOB_ID);
        ComponentName component = new ComponentName(context, RefreshJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(minutes * 60L * 1000L)
                .setBackoffCriteria(30000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL);
        try {
            builder.setPersisted(true);
        } catch (Exception ignored) {
        }
        try {
            int result = scheduler.schedule(builder.build());
            Log.i(TAG, "Job programado cada " + minutes + " min: " + result);
        } catch (Exception ex) {
            Log.e(TAG, "No se pudo programar la sincronización", ex);
        }
    }
}
