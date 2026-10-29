package com.nutomic.syncthingandroid.util;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;

import com.nutomic.syncthingandroid.service.ServiceWatchdogJobService;
import com.nutomic.syncthingandroid.service.SyncTriggerJobService;

import static com.nutomic.syncthingandroid.service.RunConditionMonitor.EXTRA_BEGIN_ACTIVE_TIME_WINDOW;

public class JobUtils {

    private static final String TAG = "JobUtils";

    private static final int SYNC_TRIGGER_JOB_ID = 0;
    private static final int SERVICE_WATCHDOG_JOB_ID = 1;

    // JobScheduler's minimum period for periodic jobs.
    private static final long SERVICE_WATCHDOG_INTERVAL_MS = 15 * 60 * 1000;

    public static void scheduleSyncTriggerServiceJob(Context context, int delayInSeconds, boolean startRun) {
        if (delayInSeconds < 0) {
            delayInSeconds = 0;
        }

        ComponentName serviceComponent = new ComponentName(context, SyncTriggerJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(SYNC_TRIGGER_JOB_ID, serviceComponent);

        // Wait at least "delayInSeconds".
        builder.setMinimumLatency(delayInSeconds * 1000);

        // Syncthing should start after the delay if startRun is true, and otherwise stop
        // The PersistableBundle is used to forward this information to the SyncTriggerJobService
        if (startRun) {
            PersistableBundle extraBundle = new PersistableBundle();
            extraBundle.putInt(EXTRA_BEGIN_ACTIVE_TIME_WINDOW, 1); // must be int, because boolean needs API 22
            builder.setExtras(extraBundle);
        }

        // Schedule the start of "SyncTriggerJobService" in "X" seconds.
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(builder.build());
        Log.i(TAG, "Scheduled SyncTriggerJobService to run in " +
                Integer.toString(delayInSeconds) +
                " seconds.");
    }

    public static void cancelSyncTriggerServiceJob(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(SYNC_TRIGGER_JOB_ID);
    }

    /**
     * Periodically checks that SyncthingService is alive, see {@link ServiceWatchdogJobService}.
     * Persisted so it also survives a reboot that happens while the service is dead.
     */
    public static void scheduleServiceWatchdogJob(Context context) {
        ComponentName serviceComponent = new ComponentName(context, ServiceWatchdogJobService.class);
        JobInfo jobInfo = new JobInfo.Builder(SERVICE_WATCHDOG_JOB_ID, serviceComponent)
                .setPeriodic(SERVICE_WATCHDOG_INTERVAL_MS)
                .setPersisted(true)
                .build();
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(jobInfo);
    }

    public static void cancelServiceWatchdogJob(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(SERVICE_WATCHDOG_JOB_ID);
    }
}
