package com.nutomic.syncthingandroid.service.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.SharedPreferences;

import com.nutomic.syncthingandroid.service.Constants;

import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class SyncthingStateAccessRouterTest {

    @Test
    public void rootStateFailureDoesNotFallBackToNormalAccess() throws Exception {
        FakeAccess normal = new FakeAccess();
        FakeAccess root = new FakeAccess();
        root.failure = true;
        SyncthingStateAccessRouter router = new SyncthingStateAccessRouter(
                new FakePreferences(true), normal, root);

        try {
            router.read(SyncthingStateFile.CONFIG);
            fail("Expected root state failure");
        } catch (SyncthingStateAccessException expected) {
            // The configured backend's failure is part of the public state-access contract.
        }

        assertEquals(0, normal.readCalls);
        assertEquals(1, root.readCalls);
    }

    @Test
    public void normalStateUsesNormalAccessWhenRootIsDisabled() throws Exception {
        FakeAccess normal = new FakeAccess();
        FakeAccess root = new FakeAccess();
        SyncthingStateAccessRouter router = new SyncthingStateAccessRouter(
                new FakePreferences(false), normal, root);

        router.exists(SyncthingStateFile.CONFIG);

        assertEquals(1, normal.existsCalls);
        assertEquals(0, root.existsCalls);
    }

    private static final class FakeAccess implements SyncthingStateAccess {
        private boolean failure;
        private int readCalls;
        private int existsCalls;

        @Override
        public byte[] read(SyncthingStateFile file) throws SyncthingStateAccessException {
            readCalls++;
            failIfNeeded();
            return new byte[0];
        }

        @Override
        public boolean exists(SyncthingStateFile file) throws SyncthingStateAccessException {
            existsCalls++;
            failIfNeeded();
            return false;
        }

        @Override
        public void writeAtomic(SyncthingStateFile file, byte[] data)
                throws SyncthingStateAccessException {
            failIfNeeded();
        }

        @Override
        public void delete(SyncthingStateFile file) throws SyncthingStateAccessException {
            failIfNeeded();
        }

        @Override
        public void verifyNormalAccess() throws SyncthingStateAccessException {
            failIfNeeded();
        }

        private void failIfNeeded() throws SyncthingStateAccessException {
            if (failure) {
                throw new SyncthingStateAccessException("state failure");
            }
        }
    }

    private static final class FakePreferences implements SharedPreferences {
        private final boolean useRoot;

        FakePreferences(boolean useRoot) {
            this.useRoot = useRoot;
        }

        @Override public boolean getBoolean(String key, boolean defValue) { return useRoot; }
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
