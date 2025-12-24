package com.nutomic.syncthingandroid.service;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import com.nutomic.syncthingandroid.R;

import static com.nutomic.syncthingandroid.service.RunConditionMonitor.ACTION_SYNC_TRIGGER_FIRED;
import static com.nutomic.syncthingandroid.service.RunConditionMonitor.EXTRA_BEGIN_ACTIVE_TIME_WINDOW;

@RequiresApi(api = Build.VERSION_CODES.N)
public class QuickSettingsTileSchedule extends TileService implements ServiceConnection, SyncthingService.OnServiceStateChangeListener {

    private static final String TAG = "QuickSettingsTileSchedule";
    private static final boolean ENABLE_VERBOSE_LOG = false;

    private int mTilesAvailableState = Tile.STATE_INACTIVE;

    public QuickSettingsTileSchedule() {
    }

    private Context mContext;
    private SharedPreferences mPreferences; // Manually initialized - no injection needed

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String pref) {
            if (pref == null || !pref.equals(Constants.PREF_BTNSTATE_FORCE_START_STOP)) return;
            refreshTile();
        }
    };
    private SyncthingService mSyncthingService;

    @Override
    public void onDestroy() {
        LogV("onDestroy()");
        if (mSyncthingService != null) {
            mSyncthingService.unregisterOnServiceStateChangeListener(this);
            mSyncthingService = null;
        }
        if (mPreferences != null) {
            mPreferences.unregisterOnSharedPreferenceChangeListener(mPrefListener);
        }
        try {
            if (mContext != null) {
                mContext.unbindService(this);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            LogV("Service not bound or already unbound");
        }
        super.onDestroy();
    }

    @Override
    public void onStartListening() {
        LogV("onStartListening()");
        if (getQsTile() != null) {
            mContext = getApplication().getApplicationContext();
            mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
            mPreferences.registerOnSharedPreferenceChangeListener(mPrefListener);

            try {
                Intent bindIntent = new Intent(mContext, SyncthingService.class);
                mContext.bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
            } catch (Exception e) {
                Log.w(TAG, "Failed to bind to SyncthingService", e);
            }

            refreshTile();
        }
        super.onStartListening();
    }

    @Override
    public void onClick() {
        if (getQsTile().getState() == Tile.STATE_UNAVAILABLE) {
            return;
        }
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
        Intent intent = new Intent(ACTION_SYNC_TRIGGER_FIRED);
        intent.putExtra(EXTRA_BEGIN_ACTIVE_TIME_WINDOW, true);
        localBroadcastManager.sendBroadcast(intent);
    }

    private void refreshTile() {
        if (setTileUnavailable()) {
            return;
        }
        updateTile(mTilesAvailableState);
    }

    private boolean setTileUnavailable() {
        Tile tile = getQsTile();
        if (tile == null) return false;

        // look through running services to see whether the app is currently running
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        boolean syncthingRunning = false;
        for (ActivityManager.RunningServiceInfo service : am.getRunningServices(Integer.MAX_VALUE)) {
            if (SyncthingService.class.getName().equals(service.service.getClassName())) {
                syncthingRunning = true;
                break;
            }
        }

        // disable tile if app is not running, schedule is off, or syncthing is force-started/stopped
        if (syncthingRunning && mPreferences.getBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, false) && mPreferences.getInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_NO_FORCE_START_STOP) == Constants.BTNSTATE_NO_FORCE_START_STOP) {
            return false;
        }

        updateTile(Tile.STATE_UNAVAILABLE);
        return true;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        LogV(String.format("onServiceConnected(ComponentName=%s, IBinder=%s)", name.toString(), service.toString()));
        SyncthingServiceBinder syncthingServiceBinder = (SyncthingServiceBinder) service;
        mSyncthingService = syncthingServiceBinder.getService();
        mSyncthingService.registerOnServiceStateChangeListener(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mSyncthingService = null;
    }

    private void updateTile(int newState) {
        Tile tile = getQsTile();
        if (tile == null) return;
        if (newState == tile.getState()) return;

        tile.setState(newState);

        String label;
        Resources res = mContext.getResources();
        if (newState == Tile.STATE_INACTIVE || newState == Tile.STATE_ACTIVE) {
            label = res.getString(R.string.qs_schedule_label_minutes, Integer.parseInt(mPreferences.getString(Constants.PREF_SYNC_DURATION_MINUTES, "5")));
        } else {
            label = res.getString(R.string.qs_schedule_disabled);
        }
        tile.setLabel(label);

        tile.updateTile();
    }

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        LogV(String.format("onServiceStateChange: %s", currentState.toString()));

        mTilesAvailableState = Tile.STATE_INACTIVE;
        if (currentState == SyncthingService.State.STARTING || currentState == SyncthingService.State.ACTIVE) {
            mTilesAvailableState = Tile.STATE_ACTIVE;
        }
        refreshTile();
    }

    private void LogV(String logMessage) {
        if (!ENABLE_VERBOSE_LOG) return;
        Log.v(TAG, logMessage);
    }
}
