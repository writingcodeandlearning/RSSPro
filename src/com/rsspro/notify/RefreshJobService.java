package com.rsspro.notify;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.util.Log;

import com.rsspro.store.Store;
import com.rsspro.sync.SyncManager;

public class RefreshJobService extends JobService {
    private static final String TAG = "RSSProJob";

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context context = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    NotifyHelper.createChannel(context);
                    Store store = new Store(context);
                    SyncManager.Result result = SyncManager.refreshAll(context);
                    if (store.notificationsEnabled()) {
                        NotifyHelper.postNew(context, result.newArticles);
                    }
                } catch (Throwable throwable) {
                    Log.e(TAG, "Error en la sincronización de fondo", throwable);
                } finally {
                    jobFinished(params, false);
                }
            }
        }, "rsspro-background-sync").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
