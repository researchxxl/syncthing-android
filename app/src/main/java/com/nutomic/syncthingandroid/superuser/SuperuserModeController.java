package com.nutomic.syncthingandroid.superuser;

import android.content.Context;
import android.content.SharedPreferences;
import android.system.Os;

import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.state.SyncthingStateAccess;
import com.nutomic.syncthingandroid.service.state.SyncthingStateAccessException;

/**
 * Owns the durable superuser-mode transition and its fail-closed ordering.
 *
 * <p>The controller never requests authorization as a side effect of reading settings. Callers
 * invoke {@link #enable(TransitionHost)} only for an explicit user action, while service startup
 * uses {@link #ensureReadyForConfiguredMode()} after existing run conditions permit a launch.</p>
 */
public final class SuperuserModeController {

    /** Lifecycle operations supplied by SyncthingService without exposing libsu to the UI. */
    public interface TransitionHost {
        boolean shouldRunAfterTransition();

        void stopForPrivilegeTransition() throws Exception;

        void startAfterPrivilegeTransition();
    }

    private final Context mContext;
    private final SharedPreferences mPreferences;
    private final SuperuserModeClient mClient;
    private final SyncthingStateAccess mNormalStateAccess;
    private volatile SuperuserRuntimeStatus mRuntimeStatus;

    /** Creates a controller backed by the production RootService client. */
    public SuperuserModeController(Context context, SharedPreferences preferences,
                                   SuperuserClient client,
                                   SyncthingStateAccess normalStateAccess) {
        this(context, preferences, (SuperuserModeClient) client, normalStateAccess);
    }

    /** Package-private constructor allowing deterministic JVM tests to provide fakes. */
    SuperuserModeController(Context context, SharedPreferences preferences,
                            SuperuserModeClient client,
                            SyncthingStateAccess normalStateAccess) {
        if (context == null || preferences == null || client == null || normalStateAccess == null) {
            throw new IllegalArgumentException("Superuser mode dependencies must not be null");
        }
        Context appContext = context.getApplicationContext();
        mContext = appContext == null ? context : appContext;
        mPreferences = preferences;
        mClient = client;
        mNormalStateAccess = normalStateAccess;
        mRuntimeStatus = isRootConfigured()
                ? SuperuserRuntimeStatus.unavailable(SuperuserErrorCode.SERVICE_BIND_FAILED,
                false, "Superuser readiness has not been checked")
                : SuperuserRuntimeStatus.normal();
    }

    /** Performs explicit normal-to-superuser transition in the required order. */
    public SuperuserOperationResult enable(TransitionHost host) {
        if (!offMainThread() || host == null) {
            return invalidRequest("Superuser transition must run off the main thread");
        }
        if (isRootConfigured()) {
            return ensureReadyForConfiguredMode();
        }
        mRuntimeStatus = SuperuserRuntimeStatus.authorizing();

        SuperuserOperationResult ready = connectVerifyAndRecover();
        if (!ready.isSuccess()) {
            return unavailable(ready);
        }

        try {
            host.stopForPrivilegeTransition();
        } catch (Exception exception) {
            SuperuserOperationResult failure = SuperuserOperationResult.failure(
                    SuperuserErrorCode.CORE_STOP_FAILED,
                    "Normal Syncthing could not be stopped for superuser mode");
            mClient.disconnect();
            return unavailable(failure);
        }

        if (!mPreferences.edit().putBoolean(Constants.PREF_USE_ROOT, true).commit()) {
            SuperuserOperationResult failure = SuperuserOperationResult.failure(
                    SuperuserErrorCode.STATE_ACCESS_FAILED,
                    "Superuser mode preference could not be saved");
            mClient.disconnect();
            tryRestartAfterCommitFailure(host);
            return unavailable(failure);
        }

        mRuntimeStatus = SuperuserRuntimeStatus.ready();
        if (!host.shouldRunAfterTransition()) {
            return SuperuserOperationResult.success();
        }
        try {
            host.startAfterPrivilegeTransition();
            return SuperuserOperationResult.success();
        } catch (RuntimeException exception) {
            SuperuserOperationResult failure = SuperuserOperationResult.failure(
                    SuperuserErrorCode.CORE_LAUNCH_FAILED,
                    "Syncthing could not be restarted as superuser");
            return unavailable(failure);
        }
    }

    /** Performs explicit superuser-to-normal transition only after state repair verification. */
    public SuperuserOperationResult disable(TransitionHost host) {
        if (!offMainThread() || host == null) {
            return invalidRequest("Superuser transition must run off the main thread");
        }
        if (!isRootConfigured()) {
            mRuntimeStatus = SuperuserRuntimeStatus.normal();
            return SuperuserOperationResult.success();
        }

        mRuntimeStatus = SuperuserRuntimeStatus.authorizing();

        SuperuserOperationResult ready = connectVerifyAndRecoverWithoutStarting();
        if (!ready.isSuccess()) {
            return unavailable(ready);
        }
        SuperuserOperationResult stopped = mClient.stopOwnedCore();
        if (stopped == null) {
            stopped = SuperuserOperationResult.failure(SuperuserErrorCode.CORE_STOP_FAILED,
                    "Root service returned no stop result");
        }
        if (!stopped.isSuccess()) {
            return unavailable(stopped);
        }
        try {
            host.stopForPrivilegeTransition();
        } catch (Exception exception) {
            SuperuserOperationResult failure = SuperuserOperationResult.failure(
                    SuperuserErrorCode.CORE_STOP_FAILED,
                    "Syncthing service could not be stopped for normal mode");
            return unavailable(failure);
        }

        SuperuserOperationResult repaired = mClient.repairAppPrivateState(
                mContext.getApplicationInfo().uid, Os.getgid());
        if (repaired == null) {
            repaired = SuperuserOperationResult.failure(
                    SuperuserErrorCode.OWNERSHIP_REPAIR_FAILED,
                    "Root service returned no ownership-repair result");
        }
        if (!repaired.isSuccess()) {
            return unavailable(repaired);
        }
        try {
            mNormalStateAccess.verifyNormalAccess();
        } catch (SyncthingStateAccessException exception) {
            SuperuserOperationResult failure = SuperuserOperationResult.failure(
                    SuperuserErrorCode.STATE_ACCESS_FAILED,
                    "Normal app-private state access could not be verified");
            return unavailable(failure);
        }

        if (!mPreferences.edit().putBoolean(Constants.PREF_USE_ROOT, false).commit()) {
            SuperuserOperationResult failure = SuperuserOperationResult.failure(
                    SuperuserErrorCode.STATE_ACCESS_FAILED,
                    "Superuser mode preference could not be cleared");
            return unavailable(failure);
        }
        mClient.disconnect();
        mRuntimeStatus = SuperuserRuntimeStatus.normal();
        if (host.shouldRunAfterTransition()) {
            try {
                host.startAfterPrivilegeTransition();
            } catch (RuntimeException exception) {
                return SuperuserOperationResult.failure(SuperuserErrorCode.CORE_LAUNCH_FAILED,
                        "Syncthing could not be restarted normally");
            }
        }
        return SuperuserOperationResult.success();
    }

    /**
     * Moves a configured installation back to normal mode for backup import without requesting
     * new root authorization. Import must not restore privileged state or retain the setting.
     */
    public SuperuserOperationResult prepareForNormalImport() {
        if (!offMainThread()) {
            return invalidRequest("Import mode transition must run off the main thread");
        }
        if (!isRootConfigured()) {
            mRuntimeStatus = SuperuserRuntimeStatus.normal();
            return SuperuserOperationResult.success();
        }
        if (!mClient.isConnected()) {
            return unavailable(SuperuserOperationResult.failure(
                    SuperuserErrorCode.SERVICE_BIND_FAILED,
                    "Root service is not already connected; import cannot authorize root"));
        }

        return disable(new TransitionHost() {
            @Override
            public boolean shouldRunAfterTransition() {
                return false;
            }

            @Override
            public void stopForPrivilegeTransition() {
                // SyncthingService has already stopped the service before this preflight.
            }

            @Override
            public void startAfterPrivilegeTransition() {
                // SyncthingService starts normal mode after the import completes.
            }
        });
    }

    /** Establishes verified UID-0 readiness for a previously configured root mode. */
    public SuperuserOperationResult ensureReadyForConfiguredMode() {
        if (!offMainThread()) {
            return invalidRequest("Superuser readiness must run off the main thread");
        }
        if (!isRootConfigured()) {
            mRuntimeStatus = SuperuserRuntimeStatus.normal();
            return SuperuserOperationResult.success();
        }
        mRuntimeStatus = SuperuserRuntimeStatus.authorizing();
        SuperuserOperationResult result = connectVerifyAndRecover();
        if (result.isSuccess()) {
            mRuntimeStatus = SuperuserRuntimeStatus.ready();
        } else {
            unavailable(result);
        }
        return result;
    }

    /** Marks the beginning of an explicit transition so observers can render it immediately. */
    public void markAuthorizing() {
        mRuntimeStatus = SuperuserRuntimeStatus.authorizing();
    }

    /** Returns the latest immutable runtime privilege status. */
    public SuperuserRuntimeStatus runtimeStatus() {
        return mRuntimeStatus;
    }

    /** Records a service-side loss of verified root supervision without changing the preference. */
    public void markUnavailable(SuperuserErrorCode errorCode, boolean orphanRisk,
                                String diagnostic) {
        mRuntimeStatus = SuperuserRuntimeStatus.unavailable(errorCode, orphanRisk, diagnostic);
    }

    private SuperuserOperationResult connectVerifyAndRecover() {
        SuperuserOperationResult connected = mClient.connectBlocking(
                SuperuserClient.ROOT_BIND_TIMEOUT_MS);
        if (connected == null) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.SERVICE_BIND_FAILED,
                    "Root service returned no connection result");
        }
        if (!connected.isSuccess()) {
            return connected;
        }
        SuperuserOperationResult verified = mClient.verifySuperuser();
        if (verified == null) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.UID_VERIFICATION_FAILED,
                    "Root service returned no UID-verification result");
        }
        if (!verified.isSuccess()) {
            return verified;
        }
        SuperuserOperationResult recovered = mClient.recoverOrphanedCore();
        return recovered == null
                ? SuperuserOperationResult.failure(SuperuserErrorCode.ORPHAN_RECOVERY_FAILED,
                "Root service returned no recovery result")
                : recovered;
    }

    private SuperuserOperationResult connectVerifyAndRecoverWithoutStarting() {
        if (!mClient.isConnected()) {
            SuperuserOperationResult connected = mClient.connectBlocking(
                    SuperuserClient.ROOT_BIND_TIMEOUT_MS);
            if (connected == null) {
                return SuperuserOperationResult.failure(SuperuserErrorCode.SERVICE_BIND_FAILED,
                        "Root service returned no connection result");
            }
            if (!connected.isSuccess()) {
                return connected;
            }
        }
        SuperuserOperationResult verified = mClient.verifySuperuser();
        if (verified == null) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.UID_VERIFICATION_FAILED,
                    "Root service returned no UID-verification result");
        }
        if (!verified.isSuccess()) {
            return verified;
        }
        SuperuserOperationResult recovered = mClient.recoverOrphanedCore();
        return recovered == null
                ? SuperuserOperationResult.failure(SuperuserErrorCode.ORPHAN_RECOVERY_FAILED,
                "Root service returned no recovery result")
                : recovered;
    }

    private boolean isRootConfigured() {
        return mPreferences.getBoolean(Constants.PREF_USE_ROOT, false);
    }

    private SuperuserOperationResult unavailable(SuperuserOperationResult failure) {
        boolean orphanRisk = failure.error() == SuperuserErrorCode.ORPHAN_RECOVERY_FAILED
                || failure.error() == SuperuserErrorCode.BINDER_DIED;
        mRuntimeStatus = SuperuserRuntimeStatus.unavailable(
                failure.error(), orphanRisk, failure.diagnostic);
        return failure;
    }

    private static SuperuserOperationResult invalidRequest(String diagnostic) {
        return SuperuserOperationResult.failure(SuperuserErrorCode.INVALID_REQUEST, diagnostic);
    }

    private static boolean offMainThread() {
        return !"main".equals(Thread.currentThread().getName());
    }

    private static void tryRestartAfterCommitFailure(TransitionHost host) {
        if (host.shouldRunAfterTransition()) {
            try {
                host.startAfterPrivilegeTransition();
            } catch (RuntimeException ignored) {
                // The preference remains false; the transition result is still the commit failure.
            }
        }
    }
}

/** Narrow client surface needed for mode transitions; it excludes arbitrary root operations. */
interface SuperuserModeClient {
    boolean isConnected();

    SuperuserOperationResult connectBlocking(long timeoutMs);

    SuperuserOperationResult verifySuperuser();

    SuperuserOperationResult recoverOrphanedCore();

    SuperuserOperationResult stopOwnedCore();

    SuperuserOperationResult repairAppPrivateState(int appUid, int appGid);

    void disconnect();
}
