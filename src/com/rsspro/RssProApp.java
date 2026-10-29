package com.rsspro;

import android.app.Application;

import com.rsspro.notify.NotifyHelper;
import com.rsspro.notify.Scheduler;
import com.rsspro.sync.SyncManager;
import com.rsspro.ui.ImageLoader;

public class RssProApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NotifyHelper.createChannel(this);
        Scheduler.schedule(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                SyncManager.pruneCached(RssProApp.this);
                ImageLoader.pruneDiskCache(RssProApp.this,
                        30L * 24L * 60L * 60L * 1000L);
            }
        }, "rsspro-maintenance").start();
    }
}
