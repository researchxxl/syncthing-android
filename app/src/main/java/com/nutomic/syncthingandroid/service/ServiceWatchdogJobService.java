package com.nutomic.syncthingandroid.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.util.Log;

import com.nutomic.syncthingandroid.receiver.BootReceiver;
import com.nutomic.syncthingandroid.util.JobUtils;

/**
 * Restarts {@link SyncthingService} after Android killed the app process.
 *
 * A killed process never reaches {@link SyncthingService#onDestroy}, so this job, scheduled
 * while the service runs, survives the kill. START_STICKY alone is not enough: Android may
 * never honor it, leaving the service dead until the user opens the app or reboots.
 */
public class ServiceWatchdogJobService extends JobService {
    private static final String TAG = "ServiceWatchdogJob";

    @Override
    public boolean onStartJob(JobParameters params) {
        Context context = getApplicationContext();
        if (!AppPrefs.getStartServiceOnBoot(context)) {
            // The user turned off running in the background since the job was scheduled.
            JobUtils.cancelServiceWatchdogJob(context);
            return false;
        }
        if (SyncthingService.isRunning()) {
            return false;
        }
        Log.i(TAG, "SyncthingService is not running, restarting it.");
        try {
            BootReceiver.startServiceCompat(context);
        } catch (IllegalStateException e) {
            // ForegroundServiceStartNotAllowedException: the app is not exempt from
            // background start restrictions (e.g. battery optimization is on).
            Log.w(TAG, "Failed to restart SyncthingService from the background", e);
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
