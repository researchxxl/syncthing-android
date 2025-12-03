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

    public QuickSettingsTileSchedule() {

    }

    private Context mContext;
    private SharedPreferences mPreferences; // Manually initialized - no injection needed
    private SyncthingService mSyncthingService;

    private int tile_active_state = Tile.STATE_INACTIVE;

    @Override
    public void onStartListening() {
        Log.d(TAG, "onStartListening()");
        Tile tile = getQsTile();
        if (tile != null) {
            mContext = getApplication().getApplicationContext();
            Resources res = mContext.getResources();
            mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);

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
            if (!syncthingRunning || !mPreferences.getBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, false)
                    || mPreferences.getInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_NO_FORCE_START_STOP) != Constants.BTNSTATE_NO_FORCE_START_STOP) {
                tile.setState(Tile.STATE_UNAVAILABLE);
                tile.setLabel(res.getString(R.string.qs_schedule_disabled));
                tile.updateTile();
                return;
            }

            // try to bind to SyncthingService if it's running and not already bound
            if (mSyncthingService == null) {
                try {
                    Intent bindIntent = new Intent(mContext, SyncthingService.class);
                    mContext.bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to bind to SyncthingService", e);
                }
            }

            tile.setState(tile_active_state);
            tile.setLabel(res.getString(
                    R.string.qs_schedule_label_minutes,
                    Integer.parseInt(mPreferences.getString(Constants.PREF_SYNC_DURATION_MINUTES, "5"))
            ));
            tile.updateTile();
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


    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, String.format("onServiceConnected(ComponentName=%s, IBinder=%s)", name.toString(), service.toString()));
        SyncthingServiceBinder syncthingServiceBinder = (SyncthingServiceBinder) service;
        mSyncthingService = syncthingServiceBinder.getService();
        mSyncthingService.registerOnServiceStateChangeListener(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mSyncthingService = null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        if (mSyncthingService != null) {
            mSyncthingService.unregisterOnServiceStateChangeListener(this);
        }
        try {
            if (mContext != null) {
                mContext.unbindService(this);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.d(TAG, "Service not bound or already unbound");
        }
        mSyncthingService = null;
        tile_active_state = Tile.STATE_INACTIVE;
        super.onDestroy();
    }

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        Log.d(TAG, String.format("onServiceStateChange: %s", currentState.toString()));

        int tile_active_state = Tile.STATE_INACTIVE;
        if (currentState == SyncthingService.State.STARTING || currentState == SyncthingService.State.ACTIVE) {
            tile_active_state = Tile.STATE_ACTIVE;
        }
        if (tile_active_state == this.tile_active_state) return;

        Tile tile = getQsTile();
        this.tile_active_state = tile_active_state;
        tile.setState(tile_active_state);
        tile.updateTile();
    }

}
