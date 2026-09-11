package com.nutomic.syncthingandroid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.nutomic.syncthingandroid.service.AppPrefs;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.root.RootAccess;
import com.nutomic.syncthingandroid.util.Util;

import java.lang.SecurityException;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    /**
     * For testing purposes:
     * adb root & adb shell am broadcast -a android.intent.action.BOOT_COMPLETED
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Boolean bootCompleted = intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED);
        Boolean packageReplaced = intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED);
        if (!bootCompleted && !packageReplaced) {
            return;
        }

        if (packageReplaced) {
            if (AppPrefs.getUseRoot(context) && RootAccess.isRootAvailableBlocking()) {
                /**
                 * In Root mode, there will be a SyncthingNative process left running after app update.
                 */
                Log.d(TAG, "ACTION_MY_PACKAGE_REPLACED: Killing leftover SyncthingNative instance if present ...");
                Util.killProcess(Constants.FILENAME_SYNCTHING_BINARY, true);
            }
        }

        // Check if we should (re)start now.
        if (!AppPrefs.getStartServiceOnBoot(context)) {
            return;
        }

        startServiceCompat(context);
    }

    /**
     * Workaround for starting service from background on Android 8+.
     *
     * https://stackoverflow.com/a/44505719/1837158
     */
    public static void startServiceCompat(Context context) {
        Intent intent = new Intent(context, SyncthingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        }
        else {
            context.startService(intent);
        }
    }
}
