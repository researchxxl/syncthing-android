package com.nutomic.syncthingandroid.service.state;

import android.content.SharedPreferences;

import com.nutomic.syncthingandroid.service.AppPrefs;

/** Selects normal or privileged state access from the durable mode preference. */
public final class SyncthingStateAccessRouter implements SyncthingStateAccess {

    private final SharedPreferences mPreferences;
    private final SyncthingStateAccess mNormalAccess;
    private final SyncthingStateAccess mSuperuserAccess;

    public SyncthingStateAccessRouter(SharedPreferences preferences,
                                      SyncthingStateAccess normalAccess,
                                      SyncthingStateAccess superuserAccess) {
        if (preferences == null || normalAccess == null) {
            throw new IllegalArgumentException("State access dependencies must not be null");
        }
        mPreferences = preferences;
        mNormalAccess = normalAccess;
        mSuperuserAccess = superuserAccess;
    }

    @Override
    public byte[] read(SyncthingStateFile file) throws SyncthingStateAccessException {
        return selected().read(file);
    }

    @Override
    public boolean exists(SyncthingStateFile file) throws SyncthingStateAccessException {
        return selected().exists(file);
    }

    @Override
    public void writeAtomic(SyncthingStateFile file, byte[] data)
            throws SyncthingStateAccessException {
        selected().writeAtomic(file, data);
    }

    @Override
    public void delete(SyncthingStateFile file) throws SyncthingStateAccessException {
        selected().delete(file);
    }

    @Override
    public void verifyNormalAccess() throws SyncthingStateAccessException {
        selected().verifyNormalAccess();
    }

    private SyncthingStateAccess selected() throws SyncthingStateAccessException {
        if (!AppPrefs.getUseRoot(mPreferences)) {
            return mNormalAccess;
        }
        if (mSuperuserAccess == null) {
            throw new SyncthingStateAccessException(
                    "Superuser mode is configured but state access is unavailable");
        }
        return mSuperuserAccess;
    }
}
