package com.nutomic.syncthingandroid.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.base.Charsets;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.service.execution.SyncthingCommand;
import com.nutomic.syncthingandroid.service.execution.SyncthingExecutionController;
import com.nutomic.syncthingandroid.service.execution.SyncthingExecutionException;
import com.nutomic.syncthingandroid.service.execution.SyncthingExecutionResult;
import com.nutomic.syncthingandroid.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.inject.Inject;

import static com.nutomic.syncthingandroid.service.SyncthingService.EXTRA_STOP_AFTER_CRASHED_NATIVE;

/**
 * Runs the syncthing binary from command line, and prints its output to logcat.
 *
 * @see <a href="http://docs.syncthing.net/users/syncthing.html">Command Line Docs</a>
 */
public class SyncthingRunnable implements Runnable {

    private static final String TAG = "SyncthingRunnable";
    private Boolean ENABLE_VERBOSE_LOG = false;
    private static final int LOG_FILE_MAX_LINES = 200000;
    private static final int LOG_FILE_BUFFER_SIZE = 1024 * 1024;

    private final Context mContext;
    private final SyncthingCommand mCommand;
    private final File mSyncthingLogFile;

    @Inject
    SharedPreferences mPreferences;

    @Inject
    NotificationHandler mNotificationHandler;

    @Inject
    SyncthingExecutionController mExecutionController;

    private Runnable mExecutionFailureListener;

    public enum Command {
        deviceid,           // Output the device ID to the command line.
        generate,           // Generate keys, a config file and immediately exit.
        main,               // Run the main Syncthing application.
        resetdatabase,      // Reset Syncthing's database
        resetdeltas;        // Reset Syncthing's delta indexes

        SyncthingCommand toSyncthingCommand() {
            return SyncthingCommand.values()[ordinal()];
        }
    }

    /**
     * Constructs instance.
     *
     * @param command Which type of Syncthing command to execute.
     */
    public SyncthingRunnable(Context context, Command command) {
        this(context, command.toSyncthingCommand());
    }

    /** Constructs a runnable for one of the closed Syncthing command modes. */
    public SyncthingRunnable(Context context, SyncthingCommand command) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
        mContext = context;
        mSyncthingLogFile = Constants.getSyncthingLogFile(mContext);
        if (command == null) {
            throw new InvalidParameterException("Unknown command option");
        }
        mCommand = command;
    }

    /** Registers the service callback used to surface typed execution failures. */
    void setExecutionFailureListener(Runnable listener) {
        mExecutionFailureListener = listener;
    }

    @Override
    public void run() {
        try {
            run(false);
        } catch (ExecutableNotFoundException e) {
            notifyExecutionFailure();
            throw new RuntimeException(e.getMessage());
        }
    }

    public String run(boolean returnStdOut) throws ExecutableNotFoundException {
        Boolean sendStopToService = false;
        Boolean restartSyncthingNative = false;
        int exitCode;
        String capturedStdOut = "";

        // Trim Syncthing log.
        trimSyncthingLogFile();

        MulticastLock multicastLock = null;
        try {
            // Android 11 blocks local discovery if we did not acquire MulticastLock.
            WifiManager wifi = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            multicastLock = wifi.createMulticastLock("multicastLock");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();

            /**
             * Setup and run a new syncthing instance
             */
            HashMap<String, String> targetEnv = buildEnvironment();
            SyncthingExecutionResult result = mExecutionController.execute(
                    mCommand, targetEnv, returnStdOut);
            exitCode = result.exitCode();
            capturedStdOut = result.stdout();
            LogV("Syncthing exited with code " + exitCode);

            switch (exitCode) {
                case 0:
                case 137:
                    Log.i(TAG, "Syncthing was shut down normally via API or SIGKILL. Exit code = " + exitCode);
                    break;
                case 1:
                    Log.w(TAG, "exit reason = exitError. Another Syncthing instance may be already running.");
                    mNotificationHandler.showCrashedNotification(R.string.notification_crash_title, Integer.toString(exitCode));
                    sendStopToService = true;
                    break;
                case 2:
                    // This should not happen as STNOUPGRADE is set.
                    Log.w(TAG, "exit reason = exitNoUpgradeAvailable. Another Syncthing instance may be already running.");
                    mNotificationHandler.showCrashedNotification(R.string.notification_crash_title, Integer.toString(exitCode));
                    sendStopToService = true;
                    break;
                case 3:
                    // Restart was requested via Rest API call.
                    Log.i(TAG, "exit reason = exitRestarting. Restarting syncthing.");
                    restartSyncthingNative = true;
                    break;
                case 9:
                    // Native was force killed.
                    Log.w(TAG, "exit reason = exitForceKill.");
                    mNotificationHandler.showCrashedNotification(R.string.notification_crash_title, Integer.toString(exitCode));
                    sendStopToService = true;
                    break;
                case 64:
                    Log.w(TAG, "exit reason = exitInvalidCommandLine.");
                    mNotificationHandler.showCrashedNotification(R.string.notification_crash_title, Integer.toString(exitCode));
                    sendStopToService = true;
                    break;
                default:
                    Log.w(TAG, "Syncthing exited unexpectedly. Exit code = " + exitCode);
                    mNotificationHandler.showCrashedNotification(R.string.notification_crash_title, Integer.toString(exitCode));
                    sendStopToService = true;
            }
        } catch (InterruptedException | SyncthingExecutionException e) {
            Log.e(TAG, "Failed to execute syncthing binary or read output", e);
            notifyExecutionFailure();
        } finally {
            if (multicastLock != null) {
                multicastLock.release();
                multicastLock = null;
            }
        }

        // Restart syncthing if it exited unexpectedly while running on a separate thread.
        if (!returnStdOut && restartSyncthingNative) {
            mContext.startService(new Intent(mContext, SyncthingService.class)
                    .setAction(SyncthingService.ACTION_RESTART));
        }

        // Notify {@link SyncthingService} that service state State.ACTIVE is no longer valid.
        if (!returnStdOut && sendStopToService) {
            Intent intent = new Intent(mContext, SyncthingService.class);
            intent.setAction(SyncthingService.ACTION_STOP);
            intent.putExtra(EXTRA_STOP_AFTER_CRASHED_NATIVE, true);
            mContext.startService(intent);
        }

        // Return captured command line output.
        return capturedStdOut;
    }

    private void notifyExecutionFailure() {
        if (mExecutionFailureListener != null) {
            mExecutionFailureListener.run();
        }
    }

    private void putCustomEnvironmentVariables(Map<String, String> environment, SharedPreferences sp) {
        String customEnvironment = sp.getString(Constants.PREF_ENVIRONMENT_VARIABLES, null);
        if (TextUtils.isEmpty(customEnvironment))
            return;

        for (String e : customEnvironment.split(" ")) {
            String[] e2 = e.split("=", 2);
            environment.put(e2[0], e2[1]);
        }
    }

    // If the nth last newline is found within this buffer, then the offset of that newline within
    // the buffer is returned. Otherwise, the negative of (nth - newlines consumed) is returned for
    // use with the new search.
    private static int findNthLastNewline(byte[] data, int size, int nth) {
        if (nth <= 0) {
            throw new IllegalArgumentException("nth must be positive: " + nth);
        }

        for (int i = size - 1; i >= 0; i--) {
            if (data[i] == '\n') {
                nth--;

                if (nth == 0) {
                    return i;
                }
            }
        }

        return -nth;
    }

    /** Trims the log and creates its app-owned inode before a normal or root launch. */
    private void trimSyncthingLogFile() {
        if (!mSyncthingLogFile.exists()) {
            try {
                if (!mSyncthingLogFile.createNewFile()) {
                    Log.w(TAG, "Failed to create Syncthing log file");
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to create Syncthing log file", e);
            }
            return;
        }

        try (RandomAccessFile input = new RandomAccessFile(mSyncthingLogFile, "r")) {
            // Find the offset of the (n + 1)th newline with constant memory. The last n lines is
            // everything after that point. This will read in block-aligned chunks if
            // LOG_FILE_BUFFER_SIZE is a multiple of the filesystem block size.
            byte[] buf = new byte[LOG_FILE_BUFFER_SIZE];
            long length = input.length();
            long chunks = Math.ceilDiv(length, buf.length);
            int newlinesRemaining = LOG_FILE_MAX_LINES + 1;
            long truncationOffset = -1;

            for (long chunk = chunks - 1; chunk >= 0; chunk--) {
                long offset = buf.length * chunk;
                input.seek(offset);

                // Last chunk can be smaller than the whole buffer.
                int n = (int) Math.min(length - offset, buf.length);
                input.readFully(buf, 0, n);

                int ret = findNthLastNewline(buf, n, newlinesRemaining);
                if (ret >= 0) {
                    truncationOffset = offset + ret + 1;
                    break;
                } else {
                    newlinesRemaining = -ret;
                }
            }

            if (truncationOffset < 0) {
                // The file already contains fewer than maximum lines.
                return;
            }

            input.seek(truncationOffset);

            File tempFile = new File(mContext.getFilesDir().toString(), "syncthing.log.tmp");
            long remain = length - truncationOffset;

            try (FileOutputStream output = new FileOutputStream(tempFile)) {
                while (remain > 0) {
                    int n = (int) Math.min(remain, buf.length);

                    input.readFully(buf, 0, n);
                    output.write(buf, 0, n);

                    remain -= n;
                }
            }

            tempFile.renameTo(mSyncthingLogFile);
        } catch (IOException e) {
            Log.w(TAG, "Failed to trim log file", e);
        }
    }

    private HashMap<String, String> buildEnvironment() {
        HashMap<String, String> targetEnv = new HashMap<>();

        // Set home directory to data folder for web GUI folder picker.
        targetEnv.put("HOME", FileUtils.getSyncthingTildeAbsolutePath());

        // Set config, key and database directory.
        targetEnv.put("STHOMEDIR", mContext.getFilesDir().toString());
        targetEnv.put("STTRACE", TextUtils.join(" ",
                mPreferences.getStringSet(Constants.PREF_DEBUG_FACILITIES_ENABLED, new HashSet<>())));
        targetEnv.put("STMONITORED", "1");
        targetEnv.put("STNOUPGRADE", "1");
        targetEnv.put("STVERSIONEXTRA", mContext.getString(R.string.app_name));

        // Database tuning against slowness.
        targetEnv.put("SQLITE_TMPDIR", mContext.getCacheDir().getAbsolutePath());

        // Workaround SyncthingNativeCode denied to read gatewayIP by Android 14+ restriction.
        final String gatewayIpV4 = getGatewayIpV4(mContext);
        if (gatewayIpV4 != null) {
            targetEnv.put("FALLBACK_NET_GATEWAY_IPV4", gatewayIpV4);
        }

        if (mPreferences.getBoolean(Constants.PREF_USE_TOR, false)) {
            targetEnv.put("all_proxy", "socks5://localhost:9050");
            targetEnv.put("ALL_PROXY_NO_FALLBACK", "1");
        } else {
            String socksProxyAddress = mPreferences.getString(Constants.PREF_SOCKS_PROXY_ADDRESS, "");
            if (!socksProxyAddress.equals("")) {
                targetEnv.put("all_proxy", socksProxyAddress);
            }

            String httpProxyAddress = mPreferences.getString(Constants.PREF_HTTP_PROXY_ADDRESS, "");
            if (!httpProxyAddress.equals("")) {
                targetEnv.put("http_proxy", httpProxyAddress);
                targetEnv.put("https_proxy", httpProxyAddress);
            }
        }

        // Optimize memory usage for older devices.
        int gogc = 100;         // GO default
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            gogc = 75;
        }
        LogV("Setting env var: [GOGC]=[" + Integer.toString(gogc) + "]");
        targetEnv.put("GOGC", Integer.toString(gogc));

        putCustomEnvironmentVariables(targetEnv, mPreferences);
        return targetEnv;
    }


    public class ExecutableNotFoundException extends Exception {

        public ExecutableNotFoundException(String message) {
            super(message);
        }

        public ExecutableNotFoundException(String message, Throwable throwable) {
            super(message, throwable);
        }

    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }

    public static String getGatewayIpV4(final Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return null;

        LinkProperties props = cm.getLinkProperties(activeNetwork);
        if (props == null) return null;

        for (RouteInfo route : props.getRoutes()) {
            InetAddress gateway = route.getGateway();
            if (route.isDefaultRoute() && gateway instanceof Inet4Address) {
                return gateway.getHostAddress();
            }
        }
        return null;
    }
}
