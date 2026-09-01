package com.nutomic.syncthingandroid.superuser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.state.SyncthingStateAccess;
import com.nutomic.syncthingandroid.service.state.SyncthingStateAccessException;
import com.nutomic.syncthingandroid.service.state.SyncthingStateFile;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SuperuserModeControllerTest {

    @Test
    public void enableCommitsOnlyAfterVerifiedRootAndRestartsSelectedMode() {
        SharedPreferences preferences = preferences();
        FakeModeClient client = new FakeModeClient();
        RecordingHost host = new RecordingHost(true);
        SuperuserModeController controller = newController(preferences, client);

        SuperuserOperationResult result = controller.enable(host);

        assertTrue(result.isSuccess());
        assertTrue(preferences.getBoolean(Constants.PREF_USE_ROOT, false));
        assertEquals(List.of("connect", "verify", "recover"), client.events);
        assertEquals(List.of("stop", "start"), host.events);
    }

    @Test
    public void deniedEnableLeavesNormalServiceUntouchedAndPreferenceOff() {
        SharedPreferences preferences = preferences();
        FakeModeClient client = new FakeModeClient();
        client.connectResult = SuperuserOperationResult.failure(
                SuperuserErrorCode.AUTHORIZATION_DENIED, "denied");
        RecordingHost host = new RecordingHost(true);

        SuperuserOperationResult result = newController(preferences, client).enable(host);

        assertEquals(SuperuserErrorCode.AUTHORIZATION_DENIED, result.error());
        assertFalse(preferences.getBoolean(Constants.PREF_USE_ROOT, false));
        assertTrue(host.events.isEmpty());
    }

    @Test
    public void failureAfterCommitKeepsRootConfiguredWithoutNormalFallback() {
        SharedPreferences preferences = preferences();
        FakeModeClient client = new FakeModeClient();
        RecordingHost host = new RecordingHost(true);
        host.failStart = true;

        SuperuserOperationResult result = newController(preferences, client).enable(host);

        assertEquals(SuperuserErrorCode.CORE_LAUNCH_FAILED, result.error());
        assertTrue(preferences.getBoolean(Constants.PREF_USE_ROOT, false));
        assertEquals(List.of("connect", "verify", "recover"), client.events);
        assertEquals(List.of("stop", "start"), host.events);
    }

    @Test
    public void disableRepairsAndVerifiesBeforeClearingPreference() {
        SharedPreferences preferences = preferences();
        preferences.edit().putBoolean(Constants.PREF_USE_ROOT, true).commit();
        FakeModeClient client = new FakeModeClient();
        client.connected = true;
        RecordingHost host = new RecordingHost(true);
        RecordingStateAccess stateAccess = new RecordingStateAccess();

        SuperuserOperationResult result = newController(preferences, client, stateAccess)
                .disable(host);

        assertTrue(result.isSuccess());
        assertFalse(preferences.getBoolean(Constants.PREF_USE_ROOT, true));
        assertEquals(List.of("verify", "recover", "stop", "repair", "disconnect"),
                client.events);
        assertEquals(List.of("verify-normal"), stateAccess.events);
        assertEquals(List.of("start"), host.events);
    }

    @Test
    public void failedDisableLeavesRootConfiguredAndDoesNotStartNormalService() {
        SharedPreferences preferences = preferences();
        preferences.edit().putBoolean(Constants.PREF_USE_ROOT, true).commit();
        FakeModeClient client = new FakeModeClient();
        client.connected = true;
        client.repairResult = SuperuserOperationResult.failure(
                SuperuserErrorCode.OWNERSHIP_REPAIR_FAILED, "repair failed");
        RecordingHost host = new RecordingHost(true);

        SuperuserOperationResult result = newController(preferences, client).disable(host);

        assertEquals(SuperuserErrorCode.OWNERSHIP_REPAIR_FAILED, result.error());
        assertTrue(preferences.getBoolean(Constants.PREF_USE_ROOT, false));
        assertFalse(host.events.contains("start"));
        assertFalse(host.events.contains("disconnect"));
    }

    @Test
    public void ensureReadyDoesNotAuthorizeWhenRootIsNotConfigured() {
        SharedPreferences preferences = preferences();
        FakeModeClient client = new FakeModeClient();

        SuperuserOperationResult result = newController(preferences, client)
                .ensureReadyForConfiguredMode();

        assertTrue(result.isSuccess());
        assertTrue(client.events.isEmpty());
        assertEquals(SuperuserRuntimeStatus.State.NORMAL,
                newController(preferences, client).runtimeStatus().state());
    }

    @Test
    public void importPreflightFailsWithoutConnectingWhenRootIsConfigured() {
        SharedPreferences preferences = preferences();
        preferences.edit().putBoolean(Constants.PREF_USE_ROOT, true).commit();
        FakeModeClient client = new FakeModeClient();

        SuperuserOperationResult result = invokeImportPreflight(
                newController(preferences, client));

        assertEquals(SuperuserErrorCode.SERVICE_BIND_FAILED, result.error());
        assertTrue(preferences.getBoolean(Constants.PREF_USE_ROOT, false));
        assertTrue(client.events.isEmpty());
    }

    @Test
    public void importPreflightUsesExistingConnectionAndClearsRootBeforeRestore() {
        SharedPreferences preferences = preferences();
        preferences.edit().putBoolean(Constants.PREF_USE_ROOT, true).commit();
        FakeModeClient client = new FakeModeClient();
        client.connected = true;
        RecordingStateAccess stateAccess = new RecordingStateAccess();

        SuperuserOperationResult result = invokeImportPreflight(
                newController(preferences, client, stateAccess));

        assertTrue(result.isSuccess());
        assertFalse(preferences.getBoolean(Constants.PREF_USE_ROOT, true));
        assertEquals(List.of("verify", "recover", "stop", "repair", "disconnect"),
                client.events);
        assertEquals(List.of("verify-normal"), stateAccess.events);
    }

    private static SuperuserOperationResult invokeImportPreflight(
            SuperuserModeController controller) {
        try {
            Method method = SuperuserModeController.class.getMethod("prepareForNormalImport");
            return (SuperuserOperationResult) method.invoke(controller);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("Import preflight API is missing", exception);
        } catch (IllegalAccessException exception) {
            throw new AssertionError("Import preflight API is inaccessible", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new AssertionError("Import preflight failed unexpectedly", cause);
        }
    }

    private static SuperuserModeController newController(SharedPreferences preferences,
                                                          FakeModeClient client) {
        return newController(preferences, client, new RecordingStateAccess());
    }

    private static SuperuserModeController newController(SharedPreferences preferences,
                                                          FakeModeClient client,
                                                          RecordingStateAccess stateAccess) {
        return new SuperuserModeController(RuntimeEnvironment.getApplication(), preferences,
                client, stateAccess);
    }

    private static SharedPreferences preferences() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences preferences = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(context);
        preferences.edit().clear().commit();
        return preferences;
    }

    private static final class RecordingHost implements SuperuserModeController.TransitionHost {
        private final boolean shouldRun;
        private final List<String> events = new ArrayList<>();
        private boolean failStart;

        RecordingHost(boolean shouldRun) {
            this.shouldRun = shouldRun;
        }

        @Override
        public boolean shouldRunAfterTransition() {
            return shouldRun;
        }

        @Override
        public void stopForPrivilegeTransition() {
            events.add("stop");
        }

        @Override
        public void startAfterPrivilegeTransition() {
            events.add("start");
            if (failStart) {
                throw new RuntimeException("launch failed");
            }
        }
    }

    private static final class FakeModeClient implements SuperuserModeClient {
        private final List<String> events = new ArrayList<>();
        private SuperuserOperationResult connectResult = SuperuserOperationResult.success();
        private SuperuserOperationResult verifyResult = SuperuserOperationResult.success();
        private SuperuserOperationResult recoverResult = SuperuserOperationResult.success();
        private SuperuserOperationResult stopResult = SuperuserOperationResult.success();
        private SuperuserOperationResult repairResult = SuperuserOperationResult.success();
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
            events.add("stop");
            return stopResult;
        }

        @Override
        public SuperuserOperationResult repairAppPrivateState(int appUid, int appGid) {
            events.add("repair");
            return repairResult;
        }

        @Override
        public void disconnect() {
            events.add("disconnect");
            connected = false;
        }
    }

    private static final class RecordingStateAccess implements SyncthingStateAccess {
        private final List<String> events = new ArrayList<>();

        @Override public byte[] read(SyncthingStateFile file) { return new byte[0]; }
        @Override public boolean exists(SyncthingStateFile file) { return false; }
        @Override public void writeAtomic(SyncthingStateFile file, byte[] data) { }
        @Override public void delete(SyncthingStateFile file) { }
        @Override public void verifyNormalAccess() { events.add("verify-normal"); }
    }
}
