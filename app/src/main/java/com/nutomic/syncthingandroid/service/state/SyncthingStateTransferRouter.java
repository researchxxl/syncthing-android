package com.nutomic.syncthingandroid.service.state;

import android.content.SharedPreferences;

import com.nutomic.syncthingandroid.service.AppPrefs;

import java.io.File;

/** Selects normal or privileged state transfer without a privilege fallback. */
public final class SyncthingStateTransferRouter implements SyncthingStateTransfer {

    private final SharedPreferences mPreferences;
    private final SyncthingStateTransfer mNormalTransfer;
    private final SyncthingStateTransfer mSuperuserTransfer;

    public SyncthingStateTransferRouter(SharedPreferences preferences,
                                        SyncthingStateTransfer normalTransfer,
                                        SyncthingStateTransfer superuserTransfer) {
        if (preferences == null || normalTransfer == null) {
            throw new IllegalArgumentException("State transfer dependencies must not be null");
        }
        mPreferences = preferences;
        mNormalTransfer = normalTransfer;
        mSuperuserTransfer = superuserTransfer;
    }

    @Override
    public File stageBackupState() throws SyncthingStateAccessException {
        return selected().stageBackupState();
    }

    @Override
    public void installBackupState(File stagedState) throws SyncthingStateAccessException {
        selected().installBackupState(stagedState);
    }

    private SyncthingStateTransfer selected() throws SyncthingStateAccessException {
        if (!AppPrefs.getUseRoot(mPreferences)) {
            return mNormalTransfer;
        }
        if (mSuperuserTransfer == null) {
            throw new SyncthingStateAccessException(
                    "Superuser mode is configured but state transfer is unavailable");
        }
        return mSuperuserTransfer;
    }
}
