package com.nutomic.syncthingandroid.superuser;

import com.nutomic.syncthingandroid.service.execution.SyncthingCommand;
import com.nutomic.syncthingandroid.service.execution.SyncthingExecutionBackend;
import com.nutomic.syncthingandroid.service.execution.SyncthingExecutionException;
import com.nutomic.syncthingandroid.service.execution.SyncthingExecutionResult;

import java.util.Map;

/** Executes every closed Syncthing command through the verified UID-0 Binder capability. */
public final class SuperuserSyncthingExecutionBackend implements SyncthingExecutionBackend {

    private final SuperuserCoreClient mClient;

    public SuperuserSyncthingExecutionBackend(SuperuserCoreClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client must not be null");
        }
        mClient = client;
    }

    @Override
    public SyncthingExecutionResult execute(SyncthingCommand command,
                                            Map<String, String> environment,
                                            boolean captureStdout)
            throws SyncthingExecutionException {
        ensureVerifiedConnection();

        SuperuserOperationResult startResult = mClient.startCore(
                command, environment, captureStdout);
        requireSuccess(startResult, "Failed to start Syncthing as superuser");

        SuperuserCoreExitResult exitResult = mClient.waitForCoreExit();
        if (exitResult == null) {
            throw new SyncthingExecutionException(
                    "Superuser service returned no Syncthing exit result");
        }
        requireSuccess(exitResult, "Failed while waiting for Syncthing as superuser");
        return new SyncthingExecutionResult(exitResult.exitCode, exitResult.stdout);
    }

    @Override
    public void stopOwnedProcess() throws SyncthingExecutionException {
        requireSuccess(mClient.stopOwnedCore(), "Failed to stop Syncthing as superuser");
    }

    private void ensureVerifiedConnection() throws SyncthingExecutionException {
        if (!mClient.isConnected()) {
            SuperuserOperationResult connectResult = mClient.connectBlocking(
                    SuperuserClient.ROOT_BIND_TIMEOUT_MS);
            requireSuccess(connectResult, "Failed to connect to the superuser service");
        }
        if (!mClient.isConnected()) {
            throw new SyncthingExecutionException(
                    "Superuser service reported success without a live connection");
        }
        requireSuccess(mClient.verifySuperuser(),
                "Superuser UID verification failed");
        requireSuccess(mClient.recoverOrphanedCore(),
                "Superuser orphan recovery failed");
    }

    private static void requireSuccess(SuperuserOperationResult result, String message)
            throws SyncthingExecutionException {
        if (result == null) {
            throw new SyncthingExecutionException(message);
        }
        if (!result.isSuccess()) {
            throw new SyncthingExecutionException(message + ": " + result.diagnostic);
        }
    }
}
