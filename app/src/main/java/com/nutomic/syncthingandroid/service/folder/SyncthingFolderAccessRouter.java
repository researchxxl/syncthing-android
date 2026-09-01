package com.nutomic.syncthingandroid.service.folder;

import android.content.SharedPreferences;

import com.nutomic.syncthingandroid.service.AppPrefs;

/** Selects the configured folder capability without falling back across privilege boundaries. */
public final class SyncthingFolderAccessRouter implements SyncthingFolderAccess {

    private final SharedPreferences mPreferences;
    private final SyncthingFolderAccess mNormalAccess;
    private final SyncthingFolderAccess mSuperuserAccess;

    public SyncthingFolderAccessRouter(SharedPreferences preferences,
                                       SyncthingFolderAccess normalAccess,
                                       SyncthingFolderAccess superuserAccess) {
        if (preferences == null || normalAccess == null) {
            throw new IllegalArgumentException("Folder access dependencies must not be null");
        }
        mPreferences = preferences;
        mNormalAccess = normalAccess;
        mSuperuserAccess = superuserAccess;
    }

    @Override
    public boolean canWrite(String absoluteFolderPath) throws SyncthingFolderAccessException {
        return selected().canWrite(absoluteFolderPath);
    }

    @Override
    public String[] findSyncConflicts(String absoluteConfiguredFolderPath)
            throws SyncthingFolderAccessException {
        return selected().findSyncConflicts(absoluteConfiguredFolderPath);
    }

    private SyncthingFolderAccess selected() throws SyncthingFolderAccessException {
        if (!AppPrefs.getUseRoot(mPreferences)) {
            return mNormalAccess;
        }
        if (mSuperuserAccess == null) {
            throw new SyncthingFolderAccessException(
                    "Superuser mode is configured but folder access is unavailable");
        }
        return mSuperuserAccess;
    }
}
