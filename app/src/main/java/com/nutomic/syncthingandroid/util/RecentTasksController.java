package com.nutomic.syncthingandroid.util;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import com.nutomic.syncthingandroid.service.Constants;

/**
 * Applies the recents preference to all tasks rooted in this app, without finishing activities or
 * changing the lifetime of the sync service. Launcher, notification and camera entries are handled
 * through application lifecycle callbacks instead of per-screen hooks.
 */
public final class RecentTasksController implements Application.ActivityLifecycleCallbacks,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "RecentTasksController";

    private final Application mApplication;
    private final SharedPreferences mPreferences;
    private final ActivityManager mActivityManager;
    private final PackageManager mPackageManager;
    private final String mPackageName;

    public RecentTasksController(Application application, SharedPreferences preferences) {
        mApplication = application;
        mPreferences = preferences;
        mActivityManager = (ActivityManager) application.getSystemService(Context.ACTIVITY_SERVICE);
        mPackageManager = application.getPackageManager();
        mPackageName = application.getPackageName();
    }

    public void start() {
        mApplication.registerActivityLifecycleCallbacks(this);
        mPreferences.registerOnSharedPreferenceChangeListener(this);
        applyPreference();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        // Android also delivers a null key when preferences are cleared. This callback and the
        // activity callbacks run on the main thread, after the in-memory preferences are updated.
        if (key == null || Constants.PREF_HIDE_FROM_RECENTS.equals(key)) {
            applyPreference();
        }
    }

    private void applyPreference() {
        if (mActivityManager == null) {
            return;
        }
        boolean hide = mPreferences.getBoolean(Constants.PREF_HIDE_FROM_RECENTS, false);
        try {
            for (ActivityManager.AppTask task : mActivityManager.getAppTasks()) {
                applyToTask(task, hide);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not enumerate app tasks; will retry on the next lifecycle event", e);
        }
    }

    private void applyToTask(ActivityManager.AppTask task, boolean hide) {
        try {
            ActivityManager.RecentTaskInfo info = task.getTaskInfo();
            if (info == null || info.baseIntent == null) {
                return;
            }
            ComponentName root = info.baseIntent.getComponent();
            if (root == null) {
                root = info.baseActivity;
            }
            // A share or external picker can involve another app's task. Never change its policy.
            if (root == null || !mPackageName.equals(root.getPackageName())) {
                return;
            }

            ActivityInfo activityInfo = mPackageManager.getActivityInfo(root, 0);
            // Keep manifest exclusions (e.g. ShareActivity) when the user disables this preference.
            boolean exclude = hide
                    || (activityInfo.flags & ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS) != 0;
            boolean currentlyExcluded = (info.baseIntent.getFlags()
                    & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0;
            if (exclude == currentlyExcluded) {
                return;
            }

            // Change only the exclusion bit. Do not remove the task: it may be locked in recents,
            // and its navigation state and the foreground sync service must remain untouched.
            task.setExcludeFromRecents(exclude);
            ActivityManager.RecentTaskInfo updated = task.getTaskInfo();
            boolean applied = updated != null && updated.baseIntent != null
                    && ((updated.baseIntent.getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    != 0) == exclude;
            // taskId requires API 29; persistentId is also available on our older supported APIs.
            if (applied) {
                Log.i(TAG, "Recents visibility updated: taskId=" + info.persistentId
                        + ", exclude=" + exclude);
            } else {
                Log.w(TAG, "Recents visibility not confirmed: taskId=" + info.persistentId
                        + ", exclude=" + exclude);
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            // A task can disappear during enumeration. Do not skip the remaining tasks or crash.
            Log.w(TAG, "Could not update a task's recents visibility", e);
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        applyPreference();
    }

    @Override
    public void onActivityResumed(Activity activity) {
        applyPreference();
    }

    @Override
    public void onActivityStopped(Activity activity) {
        // Reconcile after leaving the UI as well; this does not finish a task or affect pickers.
        applyPreference();
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
