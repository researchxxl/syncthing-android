package com.nutomic.syncthingandroid.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.zxing.common.BitMatrix;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.RecentChangesActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.WebGuiActivity;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.Util;

import java.net.URL;


/**
 * Displays information about the local device.
 */
public class DrawerFragment extends Fragment implements SyncthingService.OnServiceStateChangeListener,
        View.OnClickListener {

    private SyncthingService.State mServiceState = SyncthingService.State.INIT;

    private static final String TAG = "DrawerFragment";

    private static final int SETTINGS_SCREEN_REQUEST = 3460;

    /**
     * These buttons might be accessible if the screen is big enough
     * or the user can scroll the drawer to access them.
     */
    private TextView mDrawerActionShowQrCode;
    private TextView mDrawerRecentChanges;
    private TextView mDrawerActionWebGui;
    private TextView mDrawerActionImportExport;
    private TextView mDrawerActionRestart;

    /**
     * These buttons are always visible.
     */
    private TextView mDrawerActionSettings;
    private TextView mDrawerActionExit;

    private TextView mStatusRamUsage;
    private TextView mStatusDownload;
    private TextView mStatusUpload;
    private TextView mStatusAnnounceServer;
    private TextView mStatusSyncthingVersion;

    private MainActivity mActivity;
    private SharedPreferences sharedPreferences = null;

    private Boolean mRunningOnTV = false;

    private final Handler mRestApiQueryHandler = new Handler();
    private Runnable mRestApiQueryRunnable = new Runnable() {
        @Override
        public void run() {
            onTimerEvent();
            mRestApiQueryHandler.postDelayed(this, Constants.REST_UPDATE_INTERVAL);
        }
    };

    /**
     * Object that must be locked upon accessing the status holders.
     */
    private final Object mStatusHolderLock = new Object();

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        mServiceState = currentState;
        updateUI();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
        if (mServiceState == SyncthingService.State.ACTIVE) {
            startRestApiQueryHandler();
        }
    }

    @Override
    public void onPause() {
        stopRestApiQueryHandler();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        stopRestApiQueryHandler();
        super.onDestroy();
    }

    /**
     * Populates views and menu.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_drawer, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mActivity = (MainActivity) getActivity();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        mRunningOnTV = Util.isRunningOnTV(mActivity);

        mStatusRamUsage             = view.findViewById(R.id.statusRamUsage);
        mStatusDownload             = view.findViewById(R.id.statusDownload);
        mStatusUpload               = view.findViewById(R.id.statusUpload);
        mStatusAnnounceServer       = view.findViewById(R.id.statusAnnounceServer);
        mStatusSyncthingVersion     = view.findViewById(R.id.statusSyncthingVersion);

        mDrawerActionShowQrCode     = view.findViewById(R.id.drawerActionShowQrCode);
        mDrawerRecentChanges        = view.findViewById(R.id.drawerActionRecentChanges);
        mDrawerActionWebGui         = view.findViewById(R.id.drawerActionWebGui);
        mDrawerActionImportExport   = view.findViewById(R.id.drawerActionImportExport);
        mDrawerActionRestart        = view.findViewById(R.id.drawerActionRestart);
        mDrawerActionSettings       = view.findViewById(R.id.drawerActionSettings);
        mDrawerActionExit           = view.findViewById(R.id.drawerActionExit);

        // Add listeners to buttons.
        mDrawerActionShowQrCode.setOnClickListener(this);
        mDrawerRecentChanges.setOnClickListener(this);
        mDrawerActionWebGui.setOnClickListener(this);
        mDrawerActionImportExport.setOnClickListener(this);
        mDrawerActionRestart.setOnClickListener(this);
        mDrawerActionSettings.setOnClickListener(this);
        mDrawerActionExit.setOnClickListener(this);

        // Handle window insets to ensure padding is respected in edge-to-edge mode
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int originalLeft = v.getPaddingLeft();
            int originalTop = v.getPaddingTop();
            int originalRight = v.getPaddingRight();
            int originalBottom = v.getPaddingBottom();
            v.setPadding(
                    originalLeft + systemBars.left,
                    originalTop + systemBars.top,
                    originalRight + systemBars.right,
                    originalBottom + systemBars.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        // Initially fill UI elements.
        updateUI();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    private void updateUI() {
        Boolean syncthingRunning = mServiceState == SyncthingService.State.ACTIVE;

        if (syncthingRunning) {
            startRestApiQueryHandler();
        } else {
            stopRestApiQueryHandler();
            // Reset status text when not running
            if (mStatusRamUsage != null)
                mStatusRamUsage.setText("-");
            if (mStatusDownload != null)
                mStatusDownload.setText("-");
            if (mStatusUpload != null)
                mStatusUpload.setText("-");
            if (mStatusAnnounceServer != null)
                mStatusAnnounceServer.setText("-");
            if (mStatusSyncthingVersion != null)
                mStatusSyncthingVersion.setText("-");
        }

        /**
         * Show Web UI menu item on Android TV for debug builds only.
         * Reason: SyncthingNative's Web UI is not approved by Google because
         *          it is lacking full DPAD navigation support.
         */
        mDrawerActionWebGui.setVisibility((!mRunningOnTV || Constants.isDebuggable(getContext())) ? View.VISIBLE : View.GONE);

        // Enable buttons if syncthing is running.
        mDrawerRecentChanges.setEnabled(syncthingRunning);
        mDrawerActionWebGui.setEnabled(syncthingRunning);
        mDrawerActionRestart.setEnabled(syncthingRunning);
    }

    private void startRestApiQueryHandler() {
        mRestApiQueryHandler.removeCallbacks(mRestApiQueryRunnable);
        mRestApiQueryHandler.post(mRestApiQueryRunnable);
    }

    private void stopRestApiQueryHandler() {
        mRestApiQueryHandler.removeCallbacks(mRestApiQueryRunnable);
    }

    /**
     * Invokes status callbacks via syncthing's REST API.
     */
    private void onTimerEvent() {
        if (mActivity == null || mActivity.isFinishing()) {
            return;
        }
        if (mServiceState != SyncthingService.State.ACTIVE) {
            return;
        }
        com.nutomic.syncthingandroid.service.RestApi restApi = mActivity.getApi();
        if (restApi == null) {
            return;
        }

        // Force a cache-miss to query status of all devices asynchronously.
        restApi.getRemoteDeviceStatus("");

        // onReceiveSystemStatus will update UI
        restApi.getSystemStatus(this::onReceiveSystemStatus);

        // Also update version if available
        String version = restApi.getVersion();
        if (mStatusSyncthingVersion != null && version != null) {
            mStatusSyncthingVersion.setText(version);
        }
    }

    private void onReceiveSystemStatus(com.nutomic.syncthingandroid.model.SystemStatus systemStatus) {
        if (mActivity == null || isDetached() || getContext() == null) {
            return;
        }

        synchronized (mStatusHolderLock) {
            // RAM
            String ramUsage = Util.readableFileSize(mActivity, systemStatus.sys);
            mStatusRamUsage.setText(ramUsage);

            // Announce Server
            int announceTotal = systemStatus.discoveryMethods;
            int announceConnected = announceTotal
                    - com.google.common.base.Optional.fromNullable(systemStatus.discoveryErrors)
                            .transform(java.util.Map::size).or(0);
            String announceServerText = (announceTotal == 0) ? ""
                    : String.format(java.util.Locale.getDefault(), "%1$d/%2$d", announceConnected, announceTotal);
            mStatusAnnounceServer.setText(announceServerText);

            // Download / Upload
            com.nutomic.syncthingandroid.model.Connection total = new com.nutomic.syncthingandroid.model.Connection();
            com.nutomic.syncthingandroid.service.RestApi restApi = mActivity.getApi();
            if (restApi != null && restApi.isConfigLoaded()) {
                total = restApi.getTotalConnectionStatistic();
            }

            String download = (total.inBits / 8 < 1024) ? "0 B/s" : Util.readableTransferRate(mActivity, total.inBits);
            mStatusDownload.setText(download);

            String upload = (total.outBits / 8 < 1024) ? "0 B/s" : Util.readableTransferRate(mActivity, total.outBits);
            mStatusUpload.setText(upload);
        }
    }

    /**
     * Gets QRCode and displays it in a Dialog.
     */
    private void showQrCode() {
        String localDeviceID = PreferenceManager.getDefaultSharedPreferences(mActivity).getString(Constants.PREF_LOCAL_DEVICE_ID, "");
        if (TextUtils.isEmpty(localDeviceID)) {
            Toast.makeText(mActivity, R.string.could_not_access_deviceid, Toast.LENGTH_SHORT).show();
            return;
        }
        mActivity.showQrCodeDialog(localDeviceID);
        mActivity.closeDrawer();
    }

    @Override
    public void onClick(View v) {
        Intent intent;
        int id = v.getId();
        if (id == R.id.drawerActionShowQrCode) {
            showQrCode();
        } else if (id == R.id.drawerActionRecentChanges) {
            startActivity(new Intent(mActivity, RecentChangesActivity.class));
            mActivity.closeDrawer();
        } else if (id == R.id.drawerActionWebGui) {
            startActivity(new Intent(mActivity, WebGuiActivity.class));
            mActivity.closeDrawer();
        } else if (id == R.id.drawerActionImportExport) {
            intent = new Intent(mActivity, SettingsActivity.class);
            intent.putExtra(SettingsActivity.EXTRA_OPEN_SUB_PREF_SCREEN, "category_import_export");
            startActivity(intent);
            mActivity.closeDrawer();
        } else if (id == R.id.drawerActionRestart) {
            mActivity.showRestartDialog();
            mActivity.closeDrawer();
        } else if (id == R.id.drawerActionSettings) {
            startActivityForResult(new Intent(mActivity, SettingsActivity.class), SETTINGS_SCREEN_REQUEST);
            mActivity.closeDrawer();
        } else if (id == R.id.drawerActionExit) {
            if (sharedPreferences != null && sharedPreferences.getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false)) {
                /**
                 * App is running as a service. Show an explanation why exiting syncthing is an
                 * extraordinary request, then ask the user to confirm.
                 */
                new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.dialog_exit_while_running_as_service_title)
                        .setMessage(R.string.dialog_exit_while_running_as_service_message)
                        .setPositiveButton(R.string.yes, (d, i) -> {
                            doExit();
                        })
                        .setNegativeButton(R.string.no, (d, i) -> {
                        })
                        .show();
            } else {
                // App is not running as a service.
                doExit();
            }
            mActivity.closeDrawer();
        }
    }

    private Boolean doExit() {
        if (mActivity == null || mActivity.isFinishing()) {
            return false;
        }
        Log.i(TAG, "Exiting app on user request");
        mActivity.stopService(new Intent(mActivity, SyncthingService.class));
        mActivity.finishAndRemoveTask();
        return true;
    }

    /**
     * Receives result of SettingsActivity.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == SETTINGS_SCREEN_REQUEST && resultCode == SettingsActivity.RESULT_RESTART_APP) {
            Log.d(TAG, "Got request to restart MainActivity");
            if (doExit()) {
                startActivity(new Intent(getActivity(), MainActivity.class));
            }
        }
    }
}
