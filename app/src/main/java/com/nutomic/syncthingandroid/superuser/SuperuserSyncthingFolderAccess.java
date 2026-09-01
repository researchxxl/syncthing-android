package com.nutomic.syncthingandroid.superuser;

import android.content.Context;

import com.nutomic.syncthingandroid.service.folder.SyncthingFolderAccess;
import com.nutomic.syncthingandroid.service.folder.SyncthingFolderAccessException;

/** Routes configured-folder probes through the verified UID-0 capability. */
public final class SuperuserSyncthingFolderAccess implements SyncthingFolderAccess {

    private final SuperuserClient mClient;

    public SuperuserSyncthingFolderAccess(Context context, SuperuserClient client) {
        if (context == null || client == null) {
            throw new IllegalArgumentException("Folder access dependencies must not be null");
        }
        mClient = client;
    }

    @Override
    public boolean canWrite(String absoluteFolderPath) throws SyncthingFolderAccessException {
        requireConnectedAndVerified();
        SuperuserBooleanResult result = mClient.testFolderWritable(absoluteFolderPath);
        requireSuccess(result, "Failed to test Syncthing folder writeability");
        return result.value;
    }

    @Override
    public String[] findSyncConflicts(String absoluteConfiguredFolderPath)
            throws SyncthingFolderAccessException {
        requireConnectedAndVerified();
        SuperuserStringListResult result = mClient.findSyncConflicts(
                absoluteConfiguredFolderPath);
        requireSuccess(result, "Failed to discover Syncthing conflict files");
        return result.values == null ? new String[0] : result.values.clone();
    }

    private void requireConnectedAndVerified() throws SyncthingFolderAccessException {
        SuperuserOperationResult connected = mClient.connectBlocking(
                SuperuserClient.ROOT_BIND_TIMEOUT_MS);
        requireSuccess(connected, "Failed to connect to superuser service");
        requireSuccess(mClient.verifySuperuser(), "Superuser UID verification failed");
    }

    private static void requireSuccess(SuperuserOperationResult result, String message)
            throws SyncthingFolderAccessException {
        if (result == null || !result.isSuccess()) {
            String diagnostic = result == null ? "" : result.diagnostic;
            throw new SyncthingFolderAccessException(message + ": " + diagnostic);
        }
    }

    private static void requireSuccess(SuperuserBooleanResult result, String message)
            throws SyncthingFolderAccessException {
        if (result == null || !result.isSuccess()) {
            String diagnostic = result == null ? "" : result.diagnostic;
            throw new SyncthingFolderAccessException(message + ": " + diagnostic);
        }
    }

    private static void requireSuccess(SuperuserStringListResult result, String message)
            throws SyncthingFolderAccessException {
        if (result == null || !result.isSuccess()) {
            String diagnostic = result == null ? "" : result.diagnostic;
            throw new SyncthingFolderAccessException(message + ": " + diagnostic);
        }
    }
}
