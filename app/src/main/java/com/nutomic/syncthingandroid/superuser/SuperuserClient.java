package com.nutomic.syncthingandroid.superuser;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import com.nutomic.syncthingandroid.service.execution.SyncthingCommand;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ipc.RootService;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the normal-process side of the bounded libsu RootService connection.
 *
 * <p>The client deliberately exposes the typed AIDL calls only through future capability
 * adapters. Binding and verification are kept here so every caller gets the same timeout,
 * caller-death, and UID-proof behavior.</p>
 */
public final class SuperuserClient implements SuperuserCoreClient, SuperuserModeClient,
        SuperuserStateTransferClient {

    /** Maximum time a normal-process caller may wait for libsu to establish the root service. */
    public static final long ROOT_BIND_TIMEOUT_MS = 60_000L;

    private final Context mContext;
    private final RootBindingAdapter mBindingAdapter;
    private final RootPreflightAdapter mRootPreflightAdapter;
    private final Object mLock = new Object();

    private ISyncthingSuperuserService mService;
    private IBinder mBinder;
    private ServiceConnection mConnection;
    private boolean mPreflightInProgress;
    private IBinder.DeathRecipient mDeathRecipient;
    private Runnable mDeathListener;
    private boolean mDeathNotified;

    public SuperuserClient(Context context) {
        this(context, new LibsuRootBindingAdapter(), new LibsuRootPreflightAdapter());
    }

    SuperuserClient(Context context, RootBindingAdapter bindingAdapter) {
        this(context, bindingAdapter, new LibsuRootPreflightAdapter());
    }

    SuperuserClient(Context context, RootBindingAdapter bindingAdapter,
                    RootPreflightAdapter rootPreflightAdapter) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (bindingAdapter == null) {
            throw new IllegalArgumentException("bindingAdapter must not be null");
        }
        if (rootPreflightAdapter == null) {
            throw new IllegalArgumentException("rootPreflightAdapter must not be null");
        }
        Context applicationContext = context.getApplicationContext();
        mContext = applicationContext == null ? context : applicationContext;
        mBindingAdapter = bindingAdapter;
        mRootPreflightAdapter = rootPreflightAdapter;
    }

    /**
     * Resolves an undetermined root grant and binds to the root service without blocking the
     * Android main thread.
     *
     * <p>A pending root-manager authorization remains unresolved until libsu invokes the
     * preflight callback. The timeout applies only after root authorization has resolved
     * successfully and the RootService bind has been requested.</p>
     *
     * @param timeoutMs requested RootService bind wait time; it is capped at
     *        {@link #ROOT_BIND_TIMEOUT_MS}
     * @return a typed result describing connection success or failure
     */
    public SuperuserOperationResult connectBlocking(long timeoutMs) {
        if ("main".equals(Thread.currentThread().getName())) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.INVALID_REQUEST,
                    "Root service binding must run off the main thread");
        }
        if (timeoutMs <= 0) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.TIMEOUT,
                    "Root service binding timeout must be positive");
        }
        if (!new ProcessIdentityStore(mContext).ensureExists()) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.STATE_ACCESS_FAILED,
                    "Root process identity record is unavailable");
        }

        synchronized (mLock) {
            if (mService != null) {
                return SuperuserOperationResult.success();
            }
            if (mConnection != null || mPreflightInProgress) {
                return SuperuserOperationResult.failure(SuperuserErrorCode.INVALID_REQUEST,
                        "Root service connection is already in progress");
            }
            mPreflightInProgress = true;
        }

        final Boolean granted;
        try {
            granted = mRootPreflightAdapter.appGrantedRoot();
        } catch (RuntimeException exception) {
            clearPreflight();
            return SuperuserOperationResult.failure(SuperuserErrorCode.ROOT_UNAVAILABLE,
                    "Root availability check failed");
        }
        if (Boolean.FALSE.equals(granted)) {
            clearPreflight();
            return SuperuserOperationResult.failure(SuperuserErrorCode.ROOT_UNAVAILABLE,
                    "Root access is unavailable");
        }

        if (granted == null) {
            final CountDownLatch preflightLatch = new CountDownLatch(1);
            final AtomicReference<SuperuserOperationResult> preflightResult =
                    new AtomicReference<>();
            final AtomicBoolean preflightCompleted = new AtomicBoolean();
            RootPreflightCallback callback = new RootPreflightCallback() {
                @Override
                public void onResolved(boolean isRoot) {
                    if (!preflightCompleted.compareAndSet(false, true)) {
                        return;
                    }
                    preflightResult.set(isRoot
                            ? SuperuserOperationResult.success()
                            : SuperuserOperationResult.failure(
                            SuperuserErrorCode.ROOT_UNAVAILABLE,
                            "Root shell is not running as root"));
                    preflightLatch.countDown();
                }

                @Override
                public void onFailure() {
                    if (!preflightCompleted.compareAndSet(false, true)) {
                        return;
                    }
                    preflightResult.set(SuperuserOperationResult.failure(
                            SuperuserErrorCode.ROOT_UNAVAILABLE,
                            "Root shell acquisition failed"));
                    preflightLatch.countDown();
                }
            };
            try {
                mRootPreflightAdapter.resolveRoot(callback);
            } catch (RuntimeException exception) {
                callback.onFailure();
            }
            try {
                preflightLatch.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                clearPreflight();
                return SuperuserOperationResult.failure(SuperuserErrorCode.TIMEOUT,
                        "Root access resolution was interrupted");
            }
            SuperuserOperationResult result = preflightResult.get();
            if (result == null || !result.isSuccess()) {
                clearPreflight();
                return result == null
                        ? SuperuserOperationResult.failure(
                        SuperuserErrorCode.ROOT_UNAVAILABLE,
                        "Root shell acquisition returned no result")
                        : result;
            }
        }

        final long boundedTimeoutMs = Math.min(timeoutMs, ROOT_BIND_TIMEOUT_MS);
        final CountDownLatch connectedLatch = new CountDownLatch(1);
        final AtomicReference<SuperuserOperationResult> callbackResult = new AtomicReference<>();
        final ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                acceptConnection(this, binder, connectedLatch, callbackResult);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                connectionLost(this, connectedLatch, callbackResult,
                        "Root service disconnected");
            }

            @Override
            public void onBindingDied(ComponentName name) {
                connectionLost(this, connectedLatch, callbackResult,
                        "Root service binding died");
            }

            @Override
            public void onNullBinding(ComponentName name) {
                connectionLost(this, connectedLatch, callbackResult,
                        "Root service returned no Binder");
            }
        };

        synchronized (mLock) {
            if (mService != null) {
                mPreflightInProgress = false;
                return SuperuserOperationResult.success();
            }
            if (mConnection != null) {
                mPreflightInProgress = false;
                return SuperuserOperationResult.failure(SuperuserErrorCode.INVALID_REQUEST,
                        "Root service connection is already in progress");
            }
            mPreflightInProgress = false;
            mConnection = connection;
            mDeathNotified = false;
        }

        try {
            mBindingAdapter.bind(new Intent(mContext, SyncthingSuperuserService.class), connection);
        } catch (RuntimeException exception) {
            clearConnection(connection);
            safeUnbind(connection);
            return SuperuserOperationResult.failure(SuperuserErrorCode.SERVICE_BIND_FAILED,
                    "Root service bind request failed");
        }

        try {
            if (!connectedLatch.await(boundedTimeoutMs, TimeUnit.MILLISECONDS)) {
                clearConnection(connection);
                safeUnbind(connection);
                return SuperuserOperationResult.failure(SuperuserErrorCode.TIMEOUT,
                        "Root service bind timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            clearConnection(connection);
            safeUnbind(connection);
            return SuperuserOperationResult.failure(SuperuserErrorCode.TIMEOUT,
                    "Root service bind was interrupted");
        }

        SuperuserOperationResult failure = callbackResult.get();
        if (failure != null) {
            safeUnbind(connection);
            return failure;
        }
        synchronized (mLock) {
            if (mConnection == connection && mService != null) {
                return SuperuserOperationResult.success();
            }
        }
        safeUnbind(connection);
        return SuperuserOperationResult.failure(SuperuserErrorCode.SERVICE_BIND_FAILED,
                "Root service did not provide a usable Binder");
    }

    /** Releases the current Binder connection without notifying the death listener. */
    public void disconnect() {
        ServiceConnection connection;
        IBinder binder;
        IBinder.DeathRecipient deathRecipient;
        synchronized (mLock) {
            connection = mConnection;
            binder = mBinder;
            deathRecipient = mDeathRecipient;
            mConnection = null;
            mService = null;
            mBinder = null;
            mDeathRecipient = null;
            mDeathNotified = false;
        }
        unlinkDeathRecipient(binder, deathRecipient);
        if (connection != null) {
            safeUnbind(connection);
        }
    }

    private void clearPreflight() {
        synchronized (mLock) {
            mPreflightInProgress = false;
        }
    }

    /** Returns whether a verified Binder interface is currently available. */
    public boolean isConnected() {
        synchronized (mLock) {
            return mService != null;
        }
    }

    /**
     * Verifies that the connected service reports UID 0.
     *
     * <p>A successful Binder connection is not itself authorization. The UID proof is required
     * before any privileged adapter may use the connection.</p>
     */
    public SuperuserOperationResult verifySuperuser() {
        ISyncthingSuperuserService service;
        synchronized (mLock) {
            service = mService;
        }
        if (service == null) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.SERVICE_BIND_FAILED,
                    "Root service is not connected");
        }

        try {
            SuperuserUidResult result = service.verifySuperuser();
            if (result == null) {
                return SuperuserOperationResult.failure(SuperuserErrorCode.UID_VERIFICATION_FAILED,
                        "Root service returned no UID proof");
            }
            if (!result.isSuccess()) {
                return new SuperuserOperationResult(result.errorCode, result.diagnostic);
            }
            if (result.uid != 0) {
                return SuperuserOperationResult.failure(
                        SuperuserErrorCode.UID_VERIFICATION_FAILED,
                        "Root service did not run as UID 0");
            }
            return SuperuserOperationResult.success();
        } catch (RemoteException exception) {
            connectionLost(service, "Root service verification failed");
            return SuperuserOperationResult.failure(SuperuserErrorCode.BINDER_DIED,
                    "Root service verification failed");
        }
    }

    @Override
    public SuperuserOperationResult recoverOrphanedCore() {
        ISyncthingSuperuserService service = connectedService();
        if (service == null) {
            return notConnected();
        }
        try {
            return operationResult(service.recoverOrphanedCore(),
                    "Root service returned no orphan-recovery result");
        } catch (RemoteException exception) {
            connectionLost(service, "Root service orphan recovery failed");
            return SuperuserOperationResult.failure(SuperuserErrorCode.BINDER_DIED,
                    "Root service orphan recovery failed");
        }
    }

    @Override
    public SuperuserOperationResult startCore(SyncthingCommand command,
                                              Map<String, String> environment,
                                              boolean captureStdout) {
        if (command == null) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.INVALID_REQUEST,
                    "Syncthing command must not be null");
        }
        if (environment == null) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.INVALID_REQUEST,
                    "Syncthing environment must not be null");
        }

        Bundle environmentBundle = new Bundle();
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isEmpty() || key.indexOf('=') >= 0
                    || key.indexOf('\0') >= 0 || value == null || value.indexOf('\0') >= 0) {
                return SuperuserOperationResult.failure(SuperuserErrorCode.INVALID_REQUEST,
                        "Syncthing environment contains an invalid entry");
            }
            environmentBundle.putString(key, value);
        }

        ISyncthingSuperuserService service = connectedService();
        if (service == null) {
            return notConnected();
        }
        try {
            return operationResult(service.startCore(command.wireId(), environmentBundle,
                            captureStdout), "Root service returned no start result");
        } catch (RemoteException exception) {
            connectionLost(service, "Root service core start failed");
            return SuperuserOperationResult.failure(SuperuserErrorCode.BINDER_DIED,
                    "Root service core start failed");
        }
    }

    @Override
    public SuperuserCoreExitResult waitForCoreExit() {
        if ("main".equals(Thread.currentThread().getName())) {
            return new SuperuserCoreExitResult(SuperuserErrorCode.INVALID_REQUEST.wireCode(),
                    "Root core wait must run off the main thread", -1, "");
        }
        ISyncthingSuperuserService service = connectedService();
        if (service == null) {
            return new SuperuserCoreExitResult(SuperuserErrorCode.SERVICE_BIND_FAILED.wireCode(),
                    "Root service is not connected", -1, "");
        }
        try {
            SuperuserCoreExitResult result = service.waitForCoreExit();
            return result == null
                    ? new SuperuserCoreExitResult(SuperuserErrorCode.CORE_LAUNCH_FAILED.wireCode(),
                    "Root service returned no exit result", -1, "")
                    : result;
        } catch (RemoteException exception) {
            connectionLost(service, "Root service core wait failed");
            return new SuperuserCoreExitResult(SuperuserErrorCode.BINDER_DIED.wireCode(),
                    "Root service core wait failed", -1, "");
        }
    }

    @Override
    public SuperuserOperationResult stopOwnedCore() {
        ISyncthingSuperuserService service = connectedService();
        if (service == null) {
            return notConnected();
        }
        try {
            return operationResult(service.stopOwnedCore(),
                    "Root service returned no stop result");
        } catch (RemoteException exception) {
            connectionLost(service, "Root service core stop failed");
            return SuperuserOperationResult.failure(SuperuserErrorCode.BINDER_DIED,
                    "Root service core stop failed");
        }
    }

    @Override
    public SuperuserCoreStatus getCoreStatus() {
        ISyncthingSuperuserService service = connectedService();
        if (service == null) {
            return new SuperuserCoreStatus(SuperuserErrorCode.SERVICE_BIND_FAILED.wireCode(),
                    "Root service is not connected", false, -1);
        }
        try {
            SuperuserCoreStatus result = service.getCoreStatus();
            return result == null
                    ? new SuperuserCoreStatus(SuperuserErrorCode.BINDER_DIED.wireCode(),
                    "Root service returned no core status", false, -1)
                    : result;
        } catch (RemoteException exception) {
            connectionLost(service, "Root service core status failed");
            return new SuperuserCoreStatus(SuperuserErrorCode.BINDER_DIED.wireCode(),
                    "Root service core status failed", false, -1);
        }
    }

    public SuperuserStateFileResult openStateFileForRead(int stateFileId) {
        ISyncthingSuperuserService service = connectedService();
        if (service == null) {
            return new SuperuserStateFileResult(SuperuserErrorCode.SERVICE_BIND_FAILED.wireCode(),
                    "Root service is not connected", null);
        }
        try {
            SuperuserStateFileResult result = service.openStateFileForRead(stateFileId);
            return result == null
                    ? new SuperuserStateFileResult(SuperuserErrorCode.STATE_ACCESS_FAILED.wireCode(),
                    "Root service returned no state-file result", null)
                    : result;
        } catch (RemoteException exception) {
            connectionLost(service, "Root service state read failed");
            return new SuperuserStateFileResult(SuperuserErrorCode.BINDER_DIED.wireCode(),
                    "Root service state read failed", null);
        }
    }

    public SuperuserOperationResult writeStateFileAtomic(int stateFileId,
                                                          ParcelFileDescriptor source) {
        ISyncthingSuperuserService service = connectedService();
        if (service == null) {
            return notConnected();
        }
        try {
            SuperuserOperationResult result = service.writeStateFileAtomic(stateFileId, source);
            return operationResult(result, "Root service returned no state-write result");
        } catch (RemoteException exception) {
            connectionLost(service, "Root service state write failed");
            return SuperuserOperationResult.failure(SuperuserErrorCode.BINDER_DIED,
                    "Root service state write failed");
        }
    }

    public SuperuserOperationResult deleteStateFile(int stateFileId) {
        ISyncthingSuperuserService service = connectedService();
        if (service == null) {
            return notConnected();
        }
        try {
            SuperuserOperationResult result = service.deleteStateFile(stateFileId);
            return operationResult(result, "Root service returned no state-delete result");
        } catch (RemoteException exception) {
            connectionLost(service, "Root service state delete failed");
            return SuperuserOperationResult.failure(SuperuserErrorCode.BINDER_DIED,
                    "Root service state delete failed");
        }
    }

    /** Requests root to stage the fixed backup snapshot into the caller-readable cache. */
    public SuperuserOperationResult stageBackupState(String transferId, int appUid, int appGid) {
        if (!com.nutomic.syncthingandroid.service.state.NormalSyncthingStateTransfer
                .isValidTransferId(transferId) || appUid <= 0 || appGid <= 0) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.INVALID_REQUEST,
                    "Invalid Syncthing state transfer request");
        }
        ISyncthingSuperuserService service = connectedService();
        if (service == null) {
            return notConnected();
        }
        try {
            SuperuserOperationResult result = service.stageBackupState(transferId, appUid, appGid);
            return result == null
                    ? SuperuserOperationResult.failure(SuperuserErrorCode.STATE_TRANSFER_FAILED,
                    "Root service returned no state-stage result")
                    : result;
        } catch (RemoteException exception) {
            connectionLost(service, "Root service state staging failed");
            return SuperuserOperationResult.failure(SuperuserErrorCode.BINDER_DIED,
                    "Root service state staging failed");
        }
    }

    /** Requests root to install a previously validated fixed backup snapshot. */
    public SuperuserOperationResult installBackupState(String transferId) {
        if (!com.nutomic.syncthingandroid.service.state.NormalSyncthingStateTransfer
                .isValidTransferId(transferId)) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.INVALID_REQUEST,
                    "Invalid Syncthing state transfer request");
        }
        ISyncthingSuperuserService service = connectedService();
        if (service == null) {
            return notConnected();
        }
        try {
            SuperuserOperationResult result = service.installBackupState(transferId);
            return result == null
                    ? SuperuserOperationResult.failure(SuperuserErrorCode.STATE_TRANSFER_FAILED,
                    "Root service returned no state-install result")
                    : result;
        } catch (RemoteException exception) {
            connectionLost(service, "Root service state installation failed");
            return SuperuserOperationResult.failure(SuperuserErrorCode.BINDER_DIED,
                    "Root service state installation failed");
        }
    }

    /** Tests a user-selected folder using the root folder capability. */
    public SuperuserBooleanResult testFolderWritable(String absoluteFolderPath) {
        if (absoluteFolderPath == null || absoluteFolderPath.isEmpty()
                || !new java.io.File(absoluteFolderPath).isAbsolute()) {
            return new SuperuserBooleanResult(SuperuserErrorCode.INVALID_REQUEST.wireCode(),
                    "Folder path must be absolute", false);
        }
        ISyncthingSuperuserService service = connectedService();
        if (service == null) {
            return new SuperuserBooleanResult(SuperuserErrorCode.SERVICE_BIND_FAILED.wireCode(),
                    "Root service is not connected", false);
        }
        try {
            SuperuserBooleanResult result = service.testFolderWritable(absoluteFolderPath);
            return result == null
                    ? new SuperuserBooleanResult(SuperuserErrorCode.FOLDER_ACCESS_FAILED.wireCode(),
                    "Root service returned no folder-write result", false)
                    : result;
        } catch (RemoteException exception) {
            connectionLost(service, "Root service folder-write test failed");
            return new SuperuserBooleanResult(SuperuserErrorCode.BINDER_DIED.wireCode(),
                    "Root service folder-write test failed", false);
        }
    }

    /** Discovers conflict files below a configured folder using the root folder capability. */
    public SuperuserStringListResult findSyncConflicts(String absoluteConfiguredFolderPath) {
        if (absoluteConfiguredFolderPath == null || absoluteConfiguredFolderPath.isEmpty()
                || !new java.io.File(absoluteConfiguredFolderPath).isAbsolute()) {
            return new SuperuserStringListResult(SuperuserErrorCode.INVALID_REQUEST.wireCode(),
                    "Folder path must be absolute", new String[0]);
        }
        ISyncthingSuperuserService service = connectedService();
        if (service == null) {
            return new SuperuserStringListResult(SuperuserErrorCode.SERVICE_BIND_FAILED.wireCode(),
                    "Root service is not connected", new String[0]);
        }
        try {
            SuperuserStringListResult result = service.findSyncConflicts(
                    absoluteConfiguredFolderPath);
            return result == null
                    ? new SuperuserStringListResult(SuperuserErrorCode.FOLDER_ACCESS_FAILED.wireCode(),
                    "Root service returned no conflict result", new String[0])
                    : result;
        } catch (RemoteException exception) {
            connectionLost(service, "Root service conflict discovery failed");
            return new SuperuserStringListResult(SuperuserErrorCode.BINDER_DIED.wireCode(),
                    "Root service conflict discovery failed", new String[0]);
        }
    }

    /** Requests root to repair only root-owned entries below the app-private state directory. */
    public SuperuserOperationResult repairAppPrivateState(int appUid, int appGid) {
        if (appUid <= 0 || appGid <= 0) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.INVALID_REQUEST,
                    "Invalid app ownership repair request");
        }
        ISyncthingSuperuserService service = connectedService();
        if (service == null) {
            return notConnected();
        }
        try {
            SuperuserOperationResult result = service.repairAppPrivateState(appUid, appGid);
            return result == null
                    ? SuperuserOperationResult.failure(SuperuserErrorCode.OWNERSHIP_REPAIR_FAILED,
                    "Root service returned no ownership-repair result")
                    : result;
        } catch (RemoteException exception) {
            connectionLost(service, "Root service ownership repair failed");
            return SuperuserOperationResult.failure(SuperuserErrorCode.BINDER_DIED,
                    "Root service ownership repair failed");
        }
    }

    /** Sets the callback invoked once when the active Binder dies. */
    public void setDeathListener(Runnable listener) {
        synchronized (mLock) {
            mDeathListener = listener;
        }
    }

    private ISyncthingSuperuserService connectedService() {
        synchronized (mLock) {
            return mService;
        }
    }

    private static SuperuserOperationResult notConnected() {
        return SuperuserOperationResult.failure(SuperuserErrorCode.SERVICE_BIND_FAILED,
                "Root service is not connected");
    }

    private static SuperuserOperationResult operationResult(SuperuserOperationResult result,
                                                            String missingDiagnostic) {
        return result == null
                ? SuperuserOperationResult.failure(SuperuserErrorCode.BINDER_DIED,
                missingDiagnostic)
                : result;
    }

    private void acceptConnection(ServiceConnection connection, IBinder binder,
                                  CountDownLatch connectedLatch,
                                  AtomicReference<SuperuserOperationResult> callbackResult) {
        if (binder == null) {
            callbackResult.set(SuperuserOperationResult.failure(
                    SuperuserErrorCode.SERVICE_BIND_FAILED, "Root service returned no Binder"));
            clearConnection(connection);
            connectedLatch.countDown();
            return;
        }

        final IBinder.DeathRecipient deathRecipient = () -> connectionLost(
                connection, "Root service Binder died");
        try {
            binder.linkToDeath(deathRecipient, 0);
        } catch (RemoteException exception) {
            callbackResult.set(SuperuserOperationResult.failure(
                    SuperuserErrorCode.SERVICE_BIND_FAILED, "Root service Binder is unavailable"));
            clearConnection(connection);
            connectedLatch.countDown();
            return;
        }

        boolean accepted;
        synchronized (mLock) {
            accepted = mConnection == connection;
            if (accepted) {
                mBinder = binder;
                mDeathRecipient = deathRecipient;
                mService = ISyncthingSuperuserService.Stub.asInterface(binder);
                if (mService == null) {
                    mBinder = null;
                    mDeathRecipient = null;
                    mConnection = null;
                }
            }
        }
        if (!accepted || !isConnected()) {
            unlinkDeathRecipient(binder, deathRecipient);
            if (accepted) {
                callbackResult.set(SuperuserOperationResult.failure(
                        SuperuserErrorCode.SERVICE_BIND_FAILED,
                        "Root service Binder interface is unavailable"));
            }
        }
        connectedLatch.countDown();
    }

    private void connectionLost(ServiceConnection connection, CountDownLatch latch,
                                AtomicReference<SuperuserOperationResult> callbackResult,
                                String diagnostic) {
        boolean connected;
        synchronized (mLock) {
            if (mConnection != connection) {
                return;
            }
            connected = mService != null;
        }
        if (connected) {
            connectionLost(diagnostic);
        } else {
            callbackResult.set(SuperuserOperationResult.failure(
                    SuperuserErrorCode.SERVICE_BIND_FAILED, diagnostic));
            clearConnection(connection);
        }
        latch.countDown();
    }

    private void connectionLost(ISyncthingSuperuserService service, String diagnostic) {
        synchronized (mLock) {
            if (mService != service) {
                return;
            }
        }
        connectionLost(diagnostic);
    }

    private void connectionLost(ServiceConnection connection, String diagnostic) {
        synchronized (mLock) {
            if (mConnection != connection) {
                return;
            }
        }
        connectionLost(diagnostic);
    }

    private void connectionLost(String diagnostic) {
        Runnable listener = null;
        ServiceConnection connection;
        IBinder binder;
        IBinder.DeathRecipient deathRecipient;
        synchronized (mLock) {
            connection = mConnection;
            binder = mBinder;
            deathRecipient = mDeathRecipient;
            mConnection = null;
            mService = null;
            mBinder = null;
            mDeathRecipient = null;
            if (!mDeathNotified) {
                mDeathNotified = true;
                listener = mDeathListener;
            }
        }
        unlinkDeathRecipient(binder, deathRecipient);
        if (connection != null) {
            safeUnbind(connection);
        }
        if (listener != null) {
            listener.run();
        }
    }

    private void clearConnection(ServiceConnection connection) {
        synchronized (mLock) {
            if (mConnection == connection) {
                mConnection = null;
                mService = null;
                mBinder = null;
                mDeathRecipient = null;
            }
        }
    }

    private void safeUnbind(ServiceConnection connection) {
        try {
            mBindingAdapter.unbind(connection);
        } catch (RuntimeException ignored) {
            // A failed bind or a dead root provider may already have removed the connection.
        }
    }

    private static void unlinkDeathRecipient(IBinder binder, IBinder.DeathRecipient recipient) {
        if (binder != null && recipient != null) {
            try {
                binder.unlinkToDeath(recipient, 0);
            } catch (RuntimeException ignored) {
                // Binder death has already made unlinking impossible.
            }
        }
    }

    private static final class LibsuRootBindingAdapter implements RootBindingAdapter {
        private final Handler mMainHandler = new Handler(Looper.getMainLooper());

        @Override
        public void bind(Intent intent, ServiceConnection connection) {
            runOnMain(() -> RootService.bind(intent, connection));
        }

        @Override
        public void unbind(ServiceConnection connection) {
            runOnMain(() -> RootService.unbind(connection));
        }

        private void runOnMain(Runnable action) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                action.run();
            } else if (!mMainHandler.post(action)) {
                throw new IllegalStateException("Android main looper is unavailable");
            }
        }
    }

    /** Resolves libsu's grant state without issuing shell commands before the service bind. */
    private static final class LibsuRootPreflightAdapter implements RootPreflightAdapter {
        @Override
        public @Nullable Boolean appGrantedRoot() {
            return Shell.isAppGrantedRoot();
        }

        @Override
        public void resolveRoot(RootPreflightCallback callback) {
            try {
                Shell.getShell(Shell.EXECUTOR, shell -> {
                    try {
                        if (shell == null) {
                            callback.onFailure();
                        } else {
                            callback.onResolved(shell.isRoot());
                        }
                    } catch (RuntimeException exception) {
                        callback.onFailure();
                    }
                });
            } catch (RuntimeException exception) {
                callback.onFailure();
            }
        }
    }
}

/** Package-private seam for deterministic tests of asynchronous RootService binding. */
interface RootBindingAdapter {
    void bind(Intent intent, ServiceConnection connection);

    void unbind(ServiceConnection connection);
}

/** Package-private seam for deterministic tests of libsu root grant-state preflight. */
interface RootPreflightAdapter {
    @Nullable Boolean appGrantedRoot();

    void resolveRoot(RootPreflightCallback callback);
}

/** Receives the result of resolving libsu's undetermined root grant state. */
interface RootPreflightCallback {
    void onResolved(boolean isRoot);

    void onFailure();
}

/** Typed core operations used by the execution backend and replaceable in JVM tests. */
interface SuperuserCoreClient {
    boolean isConnected();

    SuperuserOperationResult connectBlocking(long timeoutMs);

    SuperuserOperationResult verifySuperuser();

    SuperuserOperationResult recoverOrphanedCore();

    SuperuserOperationResult startCore(SyncthingCommand command, Map<String, String> environment,
                                       boolean captureStdout);

    SuperuserCoreExitResult waitForCoreExit();

    SuperuserOperationResult stopOwnedCore();

    SuperuserCoreStatus getCoreStatus();
}

/** Narrow privileged capability used by the fixed Syncthing backup state transfer. */
interface SuperuserStateTransferClient {
    SuperuserOperationResult connectBlocking(long timeoutMs);

    SuperuserOperationResult verifySuperuser();

    SuperuserOperationResult stageBackupState(String transferId, int appUid, int appGid);

    SuperuserOperationResult installBackupState(String transferId);
}
