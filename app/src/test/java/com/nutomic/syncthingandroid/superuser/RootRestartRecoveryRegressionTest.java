package com.nutomic.syncthingandroid.superuser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.execution.SyncthingCommand;
import com.nutomic.syncthingandroid.service.execution.SyncthingExecutionException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Regression coverage for configured-root cold starts and persisted orphan recovery. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RootRestartRecoveryRegressionTest {

    private static final ProcessIdentity IDENTITY = new ProcessIdentity(
            321, 123456L, "/data/app/libsyncthingnative.so");

    @Test
    public void configuredRestartEstablishesReadinessBeforeReportingReady() {
        SharedPreferences preferences = freshPreferences();
        preferences.edit().putBoolean(Constants.PREF_USE_ROOT, true).commit();
        RecordingModeClient client = new RecordingModeClient();
        SuperuserModeController controller = new SuperuserModeController(
                RuntimeEnvironment.getApplication(), preferences, client,
                new NoOpStateAccess());

        SuperuserOperationResult result = controller.ensureReadyForConfiguredMode();

        assertTrue(result.isSuccess());
        assertTrue(preferences.getBoolean(Constants.PREF_USE_ROOT, false));
        assertEquals(List.of("connect", "verify", "recover"), client.events);
        assertEquals(SuperuserRuntimeStatus.State.SUPERUSER_READY,
                controller.runtimeStatus().state());
    }

    @Test
    public void rootUnavailableRestartPreservesPreferenceAndNeverFallsBack() {
        SharedPreferences preferences = freshPreferences();
        preferences.edit().putBoolean(Constants.PREF_USE_ROOT, true).commit();
        RecordingModeClient client = new RecordingModeClient();
        client.connectResult = SuperuserOperationResult.failure(
                SuperuserErrorCode.ROOT_UNAVAILABLE, "root unavailable");
        SuperuserModeController controller = new SuperuserModeController(
                RuntimeEnvironment.getApplication(), preferences, client,
                new NoOpStateAccess());

        SuperuserOperationResult result = controller.ensureReadyForConfiguredMode();

        assertEquals(SuperuserErrorCode.ROOT_UNAVAILABLE, result.error());
        assertTrue(preferences.getBoolean(Constants.PREF_USE_ROOT, false));
        assertEquals(SuperuserRuntimeStatus.State.SUPERUSER_UNAVAILABLE,
                controller.runtimeStatus().state());
        assertEquals(List.of("connect"), client.events);
    }

    @Test
    public void executionRecoversBeforeStartingTheNextRootCore() throws Exception {
        RecordingCoreClient client = new RecordingCoreClient();
        SuperuserSyncthingExecutionBackend backend =
                new SuperuserSyncthingExecutionBackend(client);

        backend.execute(SyncthingCommand.MAIN, Map.of(), false);

        assertEquals(List.of("verify", "recover", "start"), client.events);
        assertEquals(1, client.startCalls);
    }

    @Test
    public void failedRecoveryPreventsAnyReplacementCoreFromStarting() throws Exception {
        RecordingCoreClient client = new RecordingCoreClient();
        client.recoverResult = SuperuserOperationResult.failure(
                SuperuserErrorCode.ORPHAN_RECOVERY_FAILED, "orphan identity is unsafe");
        SuperuserSyncthingExecutionBackend backend =
                new SuperuserSyncthingExecutionBackend(client);

        try {
            backend.execute(SyncthingCommand.MAIN, Map.of(), false);
        } catch (SyncthingExecutionException expected) {
            assertEquals(List.of("verify", "recover"), client.events);
            assertEquals(0, client.startCalls);
            return;
        }
        throw new AssertionError("Expected unsafe orphan recovery to stop the launch");
    }

    @Test
    public void unknownOrphanIdentityFailsClosedWithoutSignaling() throws Exception {
        Path directory = Files.createTempDirectory("orphan-unknown");
        File recordFile = directory.resolve("identity").toFile();
        ProcessIdentityStore store = new ProcessIdentityStore(recordFile,
                new JavaSecureFileAccess());
        store.ensureExists();
        store.writeRunning(IDENTITY);
        List<Integer> signals = new ArrayList<>();

        OrphanRecoveryPolicy.Result result = OrphanRecoveryPolicy.recover(
                store.read(), store, ignored -> OrphanRecoveryPolicy.ProbeState.UNKNOWN,
                (identity, signal) -> signals.add(signal), ignored -> { }, 100, 1);

        assertEquals(SuperuserErrorCode.ORPHAN_RECOVERY_FAILED, result.operation().error());
        assertTrue(result.orphanRisk());
        assertTrue(signals.isEmpty());
        assertEquals(ProcessIdentityRecordState.RUNNING, store.read().state());
    }

    @Test
    public void serviceDefersPrivilegedConfigAccessUntilRootReadinessCompletes()
            throws IOException {
        String source = Files.readString(repoPath().resolve(
                "app/src/main/java/com/nutomic/syncthingandroid/service/SyncthingService.java"),
                StandardCharsets.UTF_8);
        int launchStart = source.indexOf("private void launchStartupTask");
        int readinessGuard = source.indexOf(
                "if (isRootConfigured() && !isSuperuserReady())", launchStart);
        int readinessCall = source.indexOf("beginRootReadiness(srCommand)", readinessGuard);
        int configAccess = source.indexOf("mConfig = new ConfigXml(this)", readinessGuard);
        int readinessStart = source.indexOf("private void beginRootReadiness");
        int ensureCall = source.indexOf("ensureReadyForConfiguredMode()", readinessStart);
        int guardedLaunch = source.indexOf("launchStartupTask(command)", ensureCall);

        assertTrue(launchStart >= 0);
        assertTrue(readinessGuard > launchStart);
        assertTrue(readinessCall > readinessGuard);
        assertTrue(configAccess > readinessCall);
        assertTrue(readinessStart > launchStart);
        assertTrue(ensureCall > readinessStart);
        assertTrue(guardedLaunch > ensureCall);
    }

    private static SharedPreferences freshPreferences() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().clear().commit();
        return preferences;
    }

    private static Path repoPath() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null && !Files.isDirectory(path.resolve("app/src"))) {
            path = path.getParent();
        }
        if (path == null) {
            throw new IllegalStateException("Unable to locate repository root from test directory");
        }
        return path;
    }

    private static final class RecordingModeClient implements SuperuserModeClient {
        private final List<String> events = new ArrayList<>();
        private SuperuserOperationResult connectResult = SuperuserOperationResult.success();
        private SuperuserOperationResult verifyResult = SuperuserOperationResult.success();
        private SuperuserOperationResult recoverResult = SuperuserOperationResult.success();
        private boolean connected;

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public SuperuserOperationResult connectBlocking(long timeoutMs) {
            events.add("connect");
            connected = connectResult.isSuccess();
            return connectResult;
        }

        @Override
        public SuperuserOperationResult verifySuperuser() {
            events.add("verify");
            return verifyResult;
        }

        @Override
        public SuperuserOperationResult recoverOrphanedCore() {
            events.add("recover");
            return recoverResult;
        }

        @Override
        public SuperuserOperationResult stopOwnedCore() {
            return SuperuserOperationResult.success();
        }

        @Override
        public SuperuserOperationResult repairAppPrivateState(int appUid, int appGid) {
            return SuperuserOperationResult.success();
        }

        @Override
        public void disconnect() {
            connected = false;
        }
    }

    private static final class RecordingCoreClient implements SuperuserCoreClient {
        private final List<String> events = new ArrayList<>();
        private SuperuserOperationResult recoverResult = SuperuserOperationResult.success();
        private int startCalls;

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public SuperuserOperationResult connectBlocking(long timeoutMs) {
            return SuperuserOperationResult.success();
        }

        @Override
        public SuperuserOperationResult verifySuperuser() {
            events.add("verify");
            return SuperuserOperationResult.success();
        }

        @Override
        public SuperuserOperationResult recoverOrphanedCore() {
            events.add("recover");
            return recoverResult;
        }

        @Override
        public SuperuserOperationResult startCore(SyncthingCommand command,
                                                  Map<String, String> environment,
                                                  boolean captureStdout) {
            events.add("start");
            startCalls++;
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
    }

    private static final class NoOpStateAccess
            implements com.nutomic.syncthingandroid.service.state.SyncthingStateAccess {
        @Override
        public byte[] read(com.nutomic.syncthingandroid.service.state.SyncthingStateFile file) {
            return new byte[0];
        }

        @Override
        public boolean exists(com.nutomic.syncthingandroid.service.state.SyncthingStateFile file) {
            return false;
        }

        @Override
        public void writeAtomic(
                com.nutomic.syncthingandroid.service.state.SyncthingStateFile file, byte[] data) {
        }

        @Override
        public void delete(com.nutomic.syncthingandroid.service.state.SyncthingStateFile file) {
        }

        @Override
        public void verifyNormalAccess() {
        }
    }
}
