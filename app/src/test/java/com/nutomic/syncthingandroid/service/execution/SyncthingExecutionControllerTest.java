package com.nutomic.syncthingandroid.service.execution;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.SharedPreferences;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

public class SyncthingExecutionControllerTest {

    @Test
    public void normalModeUsesOnlyNormalBackend() throws Exception {
        FakePreferences preferences = new FakePreferences(false);
        CountingBackend normal = new CountingBackend();
        CountingBackend superuser = new CountingBackend();
        SyncthingExecutionController controller = new SyncthingExecutionController(
                preferences, normal, superuser);

        controller.execute(SyncthingCommand.MAIN, Map.of(), false);
        controller.stopOwnedProcess();

        assertEquals(1, normal.executeCalls);
        assertEquals(1, normal.stopCalls);
        assertEquals(0, superuser.executeCalls);
        assertEquals(0, superuser.stopCalls);
    }

    @Test
    public void superuserExecutionFailureDoesNotFallBackToNormalBackend() throws Exception {
        FakePreferences preferences = new FakePreferences(true);
        CountingBackend normal = new CountingBackend();
        CountingBackend superuser = new CountingBackend();
        superuser.failExecute = true;
        SyncthingExecutionController controller = new SyncthingExecutionController(
                preferences, normal, superuser);

        try {
            controller.execute(SyncthingCommand.MAIN, Map.of(), false);
            fail("Expected privileged execution failure");
        } catch (SyncthingExecutionException expected) {
            // The selected backend's checked failure must reach the caller unchanged.
        }

        assertEquals(0, normal.executeCalls);
        assertEquals(1, superuser.executeCalls);
    }

    @Test
    public void superuserStopFailureDoesNotFallBackToNormalBackend() throws Exception {
        FakePreferences preferences = new FakePreferences(true);
        CountingBackend normal = new CountingBackend();
        CountingBackend superuser = new CountingBackend();
        superuser.failStop = true;
        SyncthingExecutionController controller = new SyncthingExecutionController(
                preferences, normal, superuser);

        try {
            controller.stopOwnedProcess();
            fail("Expected privileged stop failure");
        } catch (SyncthingExecutionException expected) {
            // The selected backend's checked failure must reach the caller unchanged.
        }

        assertEquals(0, normal.stopCalls);
        assertEquals(1, superuser.stopCalls);
    }

    private static final class CountingBackend implements SyncthingExecutionBackend {
        int executeCalls;
        int stopCalls;
        boolean failExecute;
        boolean failStop;

        @Override
        public SyncthingExecutionResult execute(
                SyncthingCommand command, Map<String, String> environment,
                boolean captureStdout) throws SyncthingExecutionException {
            executeCalls++;
            if (failExecute) {
                throw new SyncthingExecutionException("privileged execution failed");
            }
            return new SyncthingExecutionResult(0, "");
        }

        @Override
        public void stopOwnedProcess() throws SyncthingExecutionException {
            stopCalls++;
            if (failStop) {
                throw new SyncthingExecutionException("privileged stop failed");
            }
        }
    }

    private static final class FakePreferences implements SharedPreferences {
        private final boolean useRoot;

        FakePreferences(boolean useRoot) {
            this.useRoot = useRoot;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return useRoot;
        }

        @Override public Map<String, ?> getAll() { throw new UnsupportedOperationException(); }
        @Override public String getString(String key, String defValue) { throw new UnsupportedOperationException(); }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) { throw new UnsupportedOperationException(); }
        @Override public int getInt(String key, int defValue) { throw new UnsupportedOperationException(); }
        @Override public long getLong(String key, long defValue) { throw new UnsupportedOperationException(); }
        @Override public float getFloat(String key, float defValue) { throw new UnsupportedOperationException(); }
        @Override public boolean contains(String key) { throw new UnsupportedOperationException(); }
        @Override public Editor edit() { throw new UnsupportedOperationException(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
    }
}
