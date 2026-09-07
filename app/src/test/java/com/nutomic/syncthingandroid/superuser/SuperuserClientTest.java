package com.nutomic.syncthingandroid.superuser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import com.nutomic.syncthingandroid.service.state.NormalSyncthingStateTransfer;
import com.nutomic.syncthingandroid.service.execution.SyncthingCommand;

import java.util.concurrent.atomic.AtomicInteger;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SuperuserClientTest {

    @Test
    public void successfulConnectionStoresServiceInterface() {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        binding.completeWith(rootBinder());
        SuperuserClient client = newClient(binding);

        assertEquals(SuperuserErrorCode.OK, client.connectBlocking(1000).error());
        assertTrue(client.isConnected());
        assertEquals(SuperuserErrorCode.OK, client.verifySuperuser().error());
    }

    @Test
    public void finalizedDeniedGrantReturnsBeforeBinding() {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        FakeRootPreflightAdapter preflight = new FakeRootPreflightAdapter(Boolean.FALSE);
        SuperuserClient client = newClient(binding, preflight);

        SuperuserOperationResult result = client.connectBlocking(1000);

        assertEquals(SuperuserErrorCode.ROOT_UNAVAILABLE, result.error());
        assertEquals(1, preflight.calls);
        assertEquals(0, preflight.shellAcquisitionCalls);
        assertEquals(0, binding.bindCalls);
    }

    @Test
    public void undeterminedDeniedGrantReturnsBeforeBinding() {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        FakeRootPreflightAdapter preflight = new FakeRootPreflightAdapter(null, Boolean.FALSE);
        SuperuserClient client = newClient(binding, preflight);

        SuperuserOperationResult result = client.connectBlocking(1000);

        assertEquals(SuperuserErrorCode.ROOT_UNAVAILABLE, result.error());
        assertEquals(1, preflight.calls);
        assertEquals(1, preflight.shellAcquisitionCalls);
        assertEquals(0, binding.bindCalls);
    }

    @Test
    public void undeterminedGrantedStateUsesRootServiceBinding() {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        binding.completeWith(rootBinder());
        FakeRootPreflightAdapter preflight = new FakeRootPreflightAdapter(null, Boolean.TRUE);
        SuperuserClient client = newClient(binding, preflight);

        SuperuserOperationResult result = client.connectBlocking(1000);

        assertTrue(result.isSuccess());
        assertEquals(1, preflight.calls);
        assertEquals(1, preflight.shellAcquisitionCalls);
        assertEquals(1, binding.bindCalls);
        assertEquals(SuperuserErrorCode.OK, client.verifySuperuser().error());
    }

    @Test
    public void undeterminedGrantRemainsPendingUntilPreflightCallback() throws Exception {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        FakeRootPreflightAdapter preflight = new FakeRootPreflightAdapter(null, null);
        SuperuserClient client = newClient(binding, preflight);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<SuperuserOperationResult> result = executor.submit(
                    () -> client.connectBlocking(1));

            assertTrue(preflight.resolutionRequested.await(1, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            assertEquals(0, binding.bindCalls);

            preflight.resolve(false);

            assertEquals(SuperuserErrorCode.ROOT_UNAVAILABLE,
                    result.get(1, TimeUnit.SECONDS).error());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void interruptedPreflightReturnsTimeoutAndClearsInProgress() throws Exception {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        binding.completeWith(rootBinder());
        FakeRootPreflightAdapter preflight = new FakeRootPreflightAdapter(null, null);
        SuperuserClient client = newClient(binding, preflight);
        AtomicReference<SuperuserOperationResult> firstResult = new AtomicReference<>();
        Thread worker = new Thread(
                () -> firstResult.set(client.connectBlocking(1000)),
                "superuser-preflight-interruption-test");

        worker.start();
        try {
            assertTrue(preflight.resolutionRequested.await(1, TimeUnit.SECONDS));
            assertFalse(worker.getState() == Thread.State.TERMINATED);

            worker.interrupt();
            worker.join(1_000L);

            assertFalse(worker.isAlive());
            assertEquals(SuperuserErrorCode.TIMEOUT, firstResult.get().error());
            assertEquals(0, binding.bindCalls);

            preflight.setResolvedRoot(Boolean.TRUE);

            assertEquals(SuperuserErrorCode.OK, client.connectBlocking(1000).error());
            assertEquals(2, preflight.shellAcquisitionCalls);
            assertEquals(1, binding.bindCalls);
        } finally {
            worker.interrupt();
            worker.join(1_000L);
        }
    }

    @Test
    public void undeterminedGrantResolutionFailureReturnsTypedFailure() {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        RootPreflightAdapter preflight = new RootPreflightAdapter() {
            @Override
            public Boolean appGrantedRoot() {
                return null;
            }

            @Override
            public void resolveRoot(RootPreflightCallback callback) {
                callback.onFailure();
            }
        };
        SuperuserClient client = newClient(binding, preflight);

        SuperuserOperationResult result = client.connectBlocking(1000);

        assertEquals(SuperuserErrorCode.ROOT_UNAVAILABLE, result.error());
        assertEquals(0, binding.bindCalls);
    }

    @Test
    public void undeterminedGrantResolutionExceptionReturnsTypedFailure() {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        RootPreflightAdapter preflight = new RootPreflightAdapter() {
            @Override
            public Boolean appGrantedRoot() {
                return null;
            }

            @Override
            public void resolveRoot(RootPreflightCallback callback) {
                throw new IllegalStateException("libsu acquisition failed");
            }
        };
        SuperuserClient client = newClient(binding, preflight);

        SuperuserOperationResult result = client.connectBlocking(1000);

        assertEquals(SuperuserErrorCode.ROOT_UNAVAILABLE, result.error());
        assertEquals(0, binding.bindCalls);
    }

    @Test
    public void concurrentPreflightResolutionCannotStartDuplicateBinds() throws Exception {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        binding.completeWith(rootBinder());
        FakeRootPreflightAdapter preflight = new FakeRootPreflightAdapter(null, null);
        SuperuserClient client = newClient(binding, preflight);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<SuperuserOperationResult> first = executor.submit(
                    () -> client.connectBlocking(1000));
            assertTrue(preflight.resolutionRequested.await(1, TimeUnit.SECONDS));

            Future<SuperuserOperationResult> second = executor.submit(
                    () -> client.connectBlocking(1000));
            assertEquals(SuperuserErrorCode.INVALID_REQUEST,
                    second.get(1, TimeUnit.SECONDS).error());

            preflight.resolve(true);

            assertEquals(SuperuserErrorCode.OK, first.get(1, TimeUnit.SECONDS).error());
            assertEquals(1, preflight.shellAcquisitionCalls);
            assertEquals(1, binding.bindCalls);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void failedPreflightClearsStateForLaterConnectionAttempt() {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        binding.completeWith(rootBinder());
        FakeRootPreflightAdapter preflight = new FakeRootPreflightAdapter(null, Boolean.FALSE);
        SuperuserClient client = newClient(binding, preflight);

        assertEquals(SuperuserErrorCode.ROOT_UNAVAILABLE,
                client.connectBlocking(1000).error());

        preflight.setResolvedRoot(Boolean.TRUE);

        assertEquals(SuperuserErrorCode.OK, client.connectBlocking(1000).error());
        assertEquals(2, preflight.shellAcquisitionCalls);
        assertEquals(1, binding.bindCalls);
    }

    @Test
    public void finalizedGrantedStateUsesRootServiceBinding() {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        binding.completeWith(rootBinder());
        FakeRootPreflightAdapter preflight = new FakeRootPreflightAdapter(Boolean.TRUE);
        SuperuserClient client = newClient(binding, preflight);

        SuperuserOperationResult result = client.connectBlocking(1000);

        assertTrue(result.isSuccess());
        assertEquals(1, preflight.calls);
        assertEquals(0, preflight.shellAcquisitionCalls);
        assertEquals(1, binding.bindCalls);
    }

    @Test
    public void preflightExceptionReturnsTypedFailure() {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        RootPreflightAdapter preflight = new RootPreflightAdapter() {
            @Override
            public Boolean appGrantedRoot() {
                throw new IllegalStateException("libsu preflight failed");
            }

            @Override
            public void resolveRoot(RootPreflightCallback callback) {
                throw new AssertionError("Root resolution should not be requested");
            }
        };
        SuperuserClient client = newClient(binding, preflight);

        SuperuserOperationResult result = client.connectBlocking(1000);

        assertEquals(SuperuserErrorCode.ROOT_UNAVAILABLE, result.error());
        assertEquals(0, binding.bindCalls);
    }

    @Test
    public void connectedClientDoesNotRepeatRootPreflight() {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        binding.completeWith(rootBinder());
        FakeRootPreflightAdapter preflight = new FakeRootPreflightAdapter(Boolean.TRUE);
        SuperuserClient client = newClient(binding, preflight);

        assertEquals(SuperuserErrorCode.OK, client.connectBlocking(1000).error());
        assertEquals(SuperuserErrorCode.OK, client.verifySuperuser().error());
        assertEquals(SuperuserErrorCode.OK, client.connectBlocking(1000).error());

        assertEquals(1, preflight.calls);
        assertEquals(1, binding.bindCalls);
    }

    @Test
    public void timeoutUnbindsAndDoesNotRemainConnected() {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        binding.connectOnBind = false;
        SuperuserClient client = newClient(binding);

        SuperuserOperationResult result = client.connectBlocking(10);

        assertEquals(SuperuserErrorCode.TIMEOUT, result.error());
        assertFalse(client.isConnected());
        assertEquals(1, binding.unbindCalls);
    }

    @Test
    public void bindingExceptionIsTypedAndUnbinds() {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        binding.bindFailure = new IllegalStateException("root manager denied request");
        SuperuserClient client = newClient(binding);

        SuperuserOperationResult result = client.connectBlocking(1000);

        assertEquals(SuperuserErrorCode.SERVICE_BIND_FAILED, result.error());
        assertFalse(client.isConnected());
        assertEquals(1, binding.unbindCalls);
    }

    @Test
    public void binderDeathClearsServiceAndNotifiesOnce() {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        FakeSuperuserBinder binder = new FakeSuperuserBinder(0);
        binding.completeWith(binder);
        SuperuserClient client = newClient(binding);
        AtomicInteger deathCount = new AtomicInteger();
        client.setDeathListener(deathCount::incrementAndGet);

        assertEquals(SuperuserErrorCode.OK, client.connectBlocking(1000).error());
        binder.die();
        binding.connection.onServiceDisconnected(new ComponentName("test", "root"));

        assertFalse(client.isConnected());
        assertEquals(1, deathCount.get());
    }

    @Test
    public void nonRootUidIsRejectedByClientVerification() {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        binding.completeWith(new FakeSuperuserBinder(2000));
        SuperuserClient client = newClient(binding);

        assertEquals(SuperuserErrorCode.OK, client.connectBlocking(1000).error());
        assertEquals(SuperuserErrorCode.UID_VERIFICATION_FAILED,
                client.verifySuperuser().error());
    }

    @Test
    public void stateStageRequestDoesNotPrecreateUuidDirectory() throws Exception {
        FakeRootBindingAdapter binding = new FakeRootBindingAdapter();
        binding.completeWith(rootBinder());
        SuperuserClient client = newClient(binding);
        File base = NormalSyncthingStateTransfer.stagingBase(RuntimeEnvironment.getApplication());

        try {
            try {
                new SuperuserSyncthingStateTransfer(RuntimeEnvironment.getApplication(), client)
                        .stageBackupState();
                throw new AssertionError("Expected the missing service-created directory to fail");
            } catch (com.nutomic.syncthingandroid.service.state.SyncthingStateAccessException
                     expected) {
                // The normal process must not create the UUID directory before the Binder call.
            }
            assertTrue(base.isDirectory());
            File[] children = base.listFiles();
            assertTrue(children != null);
            assertEquals(0, children.length);
        } finally {
            NormalSyncthingStateTransfer.deleteTree(base);
        }
    }

    private static SuperuserClient newClient(FakeRootBindingAdapter binding) {
        return newClient(binding, new FakeRootPreflightAdapter(Boolean.TRUE));
    }

    private static SuperuserClient newClient(FakeRootBindingAdapter binding,
                                             RootPreflightAdapter preflightAdapter) {
        ProcessIdentityStore identityStore = new ProcessIdentityStore(
                new File(context().getNoBackupFilesDir(), ProcessIdentityStore.RECORD_FILE_NAME),
                new JavaSecureFileAccess());
        return new SuperuserClient(context(), binding, preflightAdapter, identityStore);
    }

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    private static FakeSuperuserBinder rootBinder() {
        return new FakeSuperuserBinder(0);
    }

    private static final class FakeRootBindingAdapter implements RootBindingAdapter {
        private boolean connectOnBind = true;
        private RuntimeException bindFailure;
        private IBinder binder;
        private ServiceConnection connection;
        private int bindCalls;
        private int unbindCalls;

        private void completeWith(IBinder binder) {
            this.binder = binder;
        }

        @Override
        public void bind(Intent intent, ServiceConnection connection) {
            bindCalls++;
            this.connection = connection;
            if (bindFailure != null) {
                throw bindFailure;
            }
            if (connectOnBind) {
                connection.onServiceConnected(new ComponentName("test", "root"), binder);
            }
        }

        @Override
        public void unbind(ServiceConnection connection) {
            unbindCalls++;
        }
    }

    private static final class FakeRootPreflightAdapter implements RootPreflightAdapter {
        private final Boolean grantState;
        private volatile Boolean resolvedRoot;
        private volatile RootPreflightCallback callback;
        private final CountDownLatch resolutionRequested = new CountDownLatch(1);
        private int calls;
        private int shellAcquisitionCalls;

        private FakeRootPreflightAdapter(Boolean grantState) {
            this(grantState, null);
        }

        private FakeRootPreflightAdapter(Boolean grantState, Boolean resolvedRoot) {
            this.grantState = grantState;
            this.resolvedRoot = resolvedRoot;
        }

        @Override
        public Boolean appGrantedRoot() {
            calls++;
            return grantState;
        }

        @Override
        public void resolveRoot(RootPreflightCallback callback) {
            shellAcquisitionCalls++;
            this.callback = callback;
            resolutionRequested.countDown();
            if (resolvedRoot != null) {
                callback.onResolved(resolvedRoot);
            }
        }

        private void resolve(boolean root) {
            RootPreflightCallback pendingCallback = callback;
            if (pendingCallback == null) {
                throw new AssertionError("Root preflight callback was not registered");
            }
            pendingCallback.onResolved(root);
        }

        private void setResolvedRoot(Boolean resolvedRoot) {
            this.resolvedRoot = resolvedRoot;
        }
    }

    private static final class FakeSuperuserBinder extends ISyncthingSuperuserService.Stub {
        private final int uid;
        private IBinder.DeathRecipient deathRecipient;

        private FakeSuperuserBinder(int uid) {
            this.uid = uid;
        }

        @Override
        public void linkToDeath(IBinder.DeathRecipient recipient, int flags) {
            deathRecipient = recipient;
        }

        private void die() {
            if (deathRecipient != null) {
                deathRecipient.binderDied();
            }
        }

        @Override
        public SuperuserUidResult verifySuperuser() {
            return new SuperuserUidResult(SuperuserErrorCode.OK.wireCode(), "", uid);
        }

        @Override
        public SuperuserOperationResult recoverOrphanedCore() {
            return SuperuserOperationResult.success();
        }

        @Override
        public SuperuserOperationResult startCore(int commandId, Bundle environment,
                                                  boolean captureStdout) {
            return SuperuserOperationResult.success();
        }

        @Override
        public SuperuserCoreExitResult waitForCoreExit() {
            return new SuperuserCoreExitResult(SuperuserErrorCode.OK.wireCode(), "", 0, "");
        }

        @Override
        public SuperuserOperationResult stopOwnedCore() {
            return SuperuserOperationResult.success();
        }

        @Override
        public SuperuserCoreStatus getCoreStatus() {
            return new SuperuserCoreStatus(SuperuserErrorCode.OK.wireCode(), "", false, -1);
        }

        @Override
        public SuperuserBooleanResult testFolderWritable(String absolutePath) {
            return new SuperuserBooleanResult(SuperuserErrorCode.OK.wireCode(), "", true);
        }

        @Override
        public SuperuserStringListResult findSyncConflicts(String absoluteConfiguredFolderPath) {
            return new SuperuserStringListResult(SuperuserErrorCode.OK.wireCode(), "",
                    new String[0]);
        }

        @Override
        public SuperuserStateFileResult openStateFileForRead(int stateFileId) {
            return new SuperuserStateFileResult(SuperuserErrorCode.OK.wireCode(), "", null);
        }

        @Override
        public SuperuserOperationResult writeStateFileAtomic(int stateFileId,
                                                              ParcelFileDescriptor source) {
            return SuperuserOperationResult.success();
        }

        @Override
        public SuperuserOperationResult deleteStateFile(int stateFileId) {
            return SuperuserOperationResult.success();
        }

        @Override
        public SuperuserOperationResult stageBackupState(String transferId, int appUid,
                                                          int appGid) {
            return SuperuserOperationResult.success();
        }

        @Override
        public SuperuserOperationResult installBackupState(String transferId) {
            return SuperuserOperationResult.success();
        }

        @Override
        public SuperuserOperationResult repairAppPrivateState(int appUid, int appGid) {
            return SuperuserOperationResult.success();
        }
    }
}
