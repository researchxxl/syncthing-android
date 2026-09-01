package com.nutomic.syncthingandroid.service.folder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.SharedPreferences;

import com.nutomic.syncthingandroid.service.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SyncthingFolderAccessTest {

    @Test
    public void normalWriteProbeUsesOnlyFixedTemporaryFile() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File folder = new File(context.getCacheDir(), "folder-access-write");
        delete(folder);
        assertTrue(folder.mkdirs());

        assertTrue(new NormalSyncthingFolderAccess(context).canWrite(folder.getAbsolutePath()));
        assertFalse(new File(folder, ".stwritetest").exists());
    }

    @Test
    public void normalConflictDiscoveryWalksJavaFilesystemAndSkipsVersions() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File folder = new File(context.getCacheDir(), "folder-access-conflicts");
        delete(folder);
        assertTrue(folder.mkdirs());
        File nested = new File(folder, "nested");
        File versions = new File(folder, Constants.FOLDER_NAME_STVERSIONS);
        assertTrue(nested.mkdir());
        assertTrue(versions.mkdir());
        write(new File(folder, "root.sync-conflict-20260827-105353-deviceid"));
        write(new File(nested, "child.sync-conflict-20260827-105354-deviceid"));
        write(new File(versions, "ignored.sync-conflict-20260827-105355-deviceid"));
        write(new File(folder, "ordinary.txt"));
        writeConfig(context, folder);

        String[] conflicts = new NormalSyncthingFolderAccess(context)
                .findSyncConflicts(folder.getAbsolutePath());

        assertArrayEquals(new String[]{
                "nested/child.sync-conflict-20260827-105354-deviceid",
                "root.sync-conflict-20260827-105353-deviceid"
        }, conflicts);
    }

    @Test
    public void conflictDiscoveryRejectsFolderNotInConfiguration() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File folder = new File(context.getCacheDir(), "folder-access-unconfigured");
        delete(folder);
        assertTrue(folder.mkdirs());
        writeConfig(context, new File(context.getCacheDir(), "another-configured-folder"));

        try {
            new NormalSyncthingFolderAccess(context).findSyncConflicts(folder.getAbsolutePath());
            fail("Expected unconfigured folder to be rejected");
        } catch (SyncthingFolderAccessException expected) {
            // Conflict discovery is limited to folders currently present in config.xml.
        }
    }

    @Test
    public void configuredRootFailureDoesNotFallbackToNormalFolderAccess() throws Exception {
        FakeFolderAccess normal = new FakeFolderAccess();
        FakeFolderAccess root = new FakeFolderAccess();
        root.failure = true;
        SyncthingFolderAccessRouter router = new SyncthingFolderAccessRouter(
                new FakePreferences(true), normal, root);

        try {
            router.canWrite("/configured/folder");
            fail("Expected configured root capability failure");
        } catch (SyncthingFolderAccessException expected) {
            // A root capability failure must be visible to the caller.
        }
        assertTrue(root.calls > 0);
        assertTrue(normal.calls == 0);
    }

    private static void writeConfig(Context context, File folder) throws IOException {
        String xml = "<?xml version=\"1.0\"?><configuration><folder path=\""
                + folder.getAbsolutePath() + "\"/></configuration>";
        write(Constants.getConfigFile(context), xml);
    }

    private static void write(File file) throws IOException {
        write(file, "test");
    }

    private static void write(File file, String contents) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Unable to create test directory");
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void delete(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new IOException("Unable to inspect test directory");
            }
            for (File child : children) {
                delete(child);
            }
        }
        if (!file.delete()) {
            throw new IOException("Unable to remove test file");
        }
    }

    private static final class FakeFolderAccess implements SyncthingFolderAccess {
        private boolean failure;
        private int calls;

        @Override
        public boolean canWrite(String absoluteFolderPath) throws SyncthingFolderAccessException {
            calls++;
            if (failure) {
                throw new SyncthingFolderAccessException("folder failure");
            }
            return true;
        }

        @Override
        public String[] findSyncConflicts(String absoluteConfiguredFolderPath)
                throws SyncthingFolderAccessException {
            calls++;
            if (failure) {
                throw new SyncthingFolderAccessException("folder failure");
            }
            return new String[0];
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
