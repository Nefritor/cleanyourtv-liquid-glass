package com.gathin.photodream;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Keeps the online pictures stocked: about every 6 hours on an unmetered network, and once right away when asked. */
public class SyncService extends JobService {
    private static final int PERIODIC_ID = 41;
    private static final int NOW_ID = 42;

    private volatile Thread worker;

    static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        ComponentName service = new ComponentName(context, SyncService.class);
        scheduler.schedule(new JobInfo.Builder(PERIODIC_ID, service)
                .setPeriodic(6L * 60 * 60 * 1000)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPersisted(true)
                .build());
    }

    /** One run as soon as the network allows (the picker calls this after a feed is ticked). */
    static void runSoon(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        scheduler.schedule(new JobInfo.Builder(NOW_ID, new ComponentName(context, SyncService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(1000)
                .build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Context context = getApplicationContext();
        worker = new Thread(() -> {
            try {
                Online.sync(context, null);
            } finally {
                jobFinished(params, false);
            }
        }, "online-sync");
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Thread t = worker;
        if (t != null) t.interrupt();
        return true;
    }
}
