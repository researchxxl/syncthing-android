package com.nutomic.syncthingandroid.superuser;

import android.content.Context;
import android.system.Os;

import com.nutomic.syncthingandroid.service.state.NormalSyncthingStateTransfer;
import com.nutomic.syncthingandroid.service.state.SyncthingStateAccessException;
import com.nutomic.syncthingandroid.service.state.SyncthingStateTransfer;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Routes the fixed backup snapshot through the verified UID-0 state-transfer capability. */
public final class SuperuserSyncthingStateTransfer implements SyncthingStateTransfer {

    private final Context mContext;
    private final SuperuserStateTransferClient mClient;

    public SuperuserSyncthingStateTransfer(Context context, SuperuserClient client) {
        this(context, (SuperuserStateTransferClient) client);
    }

    SuperuserSyncthingStateTransfer(Context context, SuperuserStateTransferClient client) {
        if (context == null || client == null) {
            throw new IllegalArgumentException("State transfer dependencies must not be null");
        }
        Context appContext = context.getApplicationContext();
        mContext = appContext == null ? context : appContext;
        mClient = client;
    }

    @Override
    public File stageBackupState() throws SyncthingStateAccessException {
        File base = NormalSyncthingStateTransfer.stagingBase(mContext);
        File staging = null;
        boolean serviceCreatedStaging = false;
        try {
            NormalSyncthingStateTransfer.ensureStagingBase(base.getParentFile(), base);
            String transferId = UUID.randomUUID().toString();
            staging = new File(base, transferId);
            requireConnectedAndVerified();
            SuperuserOperationResult result = mClient.stageBackupState(
                    transferId, mContext.getApplicationInfo().uid, Os.getgid());
            requireSuccess(result, "Failed to stage Syncthing state");
            serviceCreatedStaging = true;
            NormalSyncthingStateTransfer.validateStagingDirectory(base, staging);
            NormalSyncthingStateTransfer.ensureSafeTree(staging, base);
            NormalSyncthingStateTransfer.validateSnapshotEntries(staging);
            return staging;
        } catch (IOException | SecurityException exception) {
            if (serviceCreatedStaging) {
                deleteQuietly(base, staging);
            }
            throw new SyncthingStateAccessException("Failed to stage Syncthing state", exception);
        } catch (SyncthingStateAccessException exception) {
            if (serviceCreatedStaging) {
                deleteQuietly(base, staging);
            }
            throw exception;
        }
    }

    @Override
    public void installBackupState(File stagedState) throws SyncthingStateAccessException {
        File base = NormalSyncthingStateTransfer.stagingBase(mContext);
        NormalSyncthingStateTransfer.validateStagingDirectory(base, stagedState);
        try {
            NormalSyncthingStateTransfer.ensureSafeTree(stagedState, base);
            requireConnectedAndVerified();
            SuperuserOperationResult result = mClient.installBackupState(stagedState.getName());
            requireSuccess(result, "Failed to install Syncthing state");
            NormalSyncthingStateTransfer.deleteTree(stagedState);
        } catch (IOException | SecurityException exception) {
            throw new SyncthingStateAccessException("Failed to install Syncthing state", exception);
        }
    }

    private void requireConnectedAndVerified() throws SyncthingStateAccessException {
        SuperuserOperationResult connected = mClient.connectBlocking(
                SuperuserClient.ROOT_BIND_TIMEOUT_MS);
        requireSuccess(connected, "Failed to connect to superuser service");
        requireSuccess(mClient.verifySuperuser(), "Superuser UID verification failed");
    }

    private static void requireSuccess(SuperuserOperationResult result, String message)
            throws SyncthingStateAccessException {
        if (result == null || !result.isSuccess()) {
            String diagnostic = result == null ? "" : result.diagnostic;
            throw new SyncthingStateAccessException(message + ": " + diagnostic);
        }
    }

    private static void deleteQuietly(File base, File directory) {
        if (base == null || directory == null) {
            return;
        }
        try {
            NormalSyncthingStateTransfer.validateStagingDirectory(base, directory);
            NormalSyncthingStateTransfer.deleteTree(directory);
        } catch (IOException | SyncthingStateAccessException | RuntimeException ignored) {
            // The caller receives the operation failure; an unsafe entry is never followed.
        }
    }
}
