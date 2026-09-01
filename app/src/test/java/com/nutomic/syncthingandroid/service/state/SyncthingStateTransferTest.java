package com.nutomic.syncthingandroid.service.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import com.nutomic.syncthingandroid.service.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SyncthingStateTransferTest {

    private static final ReentrantLock STAGING_PATH_LOCK = new ReentrantLock();

    @Before
    public void lockStagingPaths() {
        STAGING_PATH_LOCK.lock();
    }

    @After
    public void unlockStagingPaths() {
        STAGING_PATH_LOCK.unlock();
    }

    @Test
    public void stageContainsOnlyKnownExistingStateEntries() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearKnownState(context);
        write(new File(context.getFilesDir(), Constants.CONFIG_FILE), "config");
        write(new File(context.getFilesDir(), Constants.HTTPS_CERT_FILE), "cert");
        File index = Constants.getIndexDbFolder(context);
        assertTrue(index.mkdir());
        write(new File(index, "db"), "index");
        write(new File(context.getFilesDir(), Constants.SHARED_PREFS_FILE), "prefs");
        write(new File(context.getFilesDir(), "syncthing.log"), "log");

        File staged = new NormalSyncthingStateTransfer(context).stageBackupState();

        Set<String> names = new HashSet<>();
        File[] children = staged.listFiles();
        assertTrue(children != null);
        for (File child : children) {
            names.add(child.getName());
        }
        assertEquals(Set.of(Constants.CONFIG_FILE, Constants.HTTPS_CERT_FILE, "index-v2"),
                names);
        assertFalse(names.contains(Constants.SHARED_PREFS_FILE));
        assertFalse(names.contains("syncthing.log"));
    }

    @Test
    public void installRejectsEntriesOutsideFixedSnapshot() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File base = NormalSyncthingStateTransfer.stagingBase(context);
        assertTrue(base.mkdirs() || base.isDirectory());
        File staged = new File(base, UUID.randomUUID().toString());
        assertTrue(staged.mkdir());
        write(new File(staged, Constants.CONFIG_FILE), "config");
        write(new File(staged, Constants.SHARED_PREFS_FILE), "prefs");

        try {
            new NormalSyncthingStateTransfer(context).installBackupState(staged);
            fail("Expected unsupported staging entry to be rejected");
        } catch (SyncthingStateAccessException expected) {
            // The transfer boundary rejects the complete staging set before installation.
        }
    }

    @Test
    public void transferIdsAndBasePathsAreClosed() throws Exception {
        assertFalse(NormalSyncthingStateTransfer.isValidTransferId("../escape"));
        assertFalse(NormalSyncthingStateTransfer.isValidTransferId("not-a-transfer"));
        String canonicalId = "123e4567-e89b-12d3-a456-426614174000";
        assertTrue(NormalSyncthingStateTransfer.isValidTransferId(canonicalId));
        assertFalse(NormalSyncthingStateTransfer.isValidTransferId(
                canonicalId.toUpperCase(Locale.ROOT)));
        assertFalse(NormalSyncthingStateTransfer.isValidTransferId(
                canonicalId.replace("-", "")));
        assertFalse(NormalSyncthingStateTransfer.isValidTransferId(
                canonicalId.replace('-', '_')));
        assertFalse(NormalSyncthingStateTransfer.isValidTransferId(
                "123e4567-e89b-12d3-a456-42661417400"));

        Context context = RuntimeEnvironment.getApplication();
        File outside = new File(context.getCacheDir(), UUID.randomUUID().toString());
        assertTrue(outside.mkdir());
        try {
            new NormalSyncthingStateTransfer(context).installBackupState(outside);
            fail("Expected staging directory outside fixed base to be rejected");
        } catch (SyncthingStateAccessException expected) {
            // A UUID-shaped directory is still invalid when its parent is not the fixed base.
        }
    }

    @Test
    public void fixedStagingBaseSymlinkIsRejected() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File base = NormalSyncthingStateTransfer.stagingBase(context);
        clearKnownState(context);
        File outside = new File(context.getCacheDir(), "staging-outside-" + UUID.randomUUID());
        assertTrue(outside.mkdir());
        try {
            Files.createSymbolicLink(base.toPath(), outside.toPath());

            try {
                new NormalSyncthingStateTransfer(context).stageBackupState();
                fail("Expected a symlinked fixed staging base to be rejected");
            } catch (SyncthingStateAccessException expected) {
                // The normal and privileged paths must never follow a staging-base symlink.
            }
        } finally {
            Files.deleteIfExists(base.toPath());
            delete(outside);
        }
    }

    @Test
    public void symlinkedUuidTransferDirectoryIsRejectedBeforeUse() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File base = NormalSyncthingStateTransfer.stagingBase(context);
        clearKnownState(context);
        assertTrue(base.mkdir());
        File target = new File(base, UUID.randomUUID().toString());
        assertTrue(target.mkdir());
        File staged = new File(base, UUID.randomUUID().toString());
        try {
            Files.createSymbolicLink(staged.toPath(), target.toPath());

            try {
                NormalSyncthingStateTransfer.validateStagingDirectory(base, staged);
                fail("Expected a symlinked UUID transfer directory to be rejected");
            } catch (SyncthingStateAccessException expected) {
                // Existing transfer paths are never trusted merely because their names are UUIDs.
            }
        } finally {
            Files.deleteIfExists(staged.toPath());
            delete(target);
            delete(base);
        }
    }

    private static void clearKnownState(Context context) throws IOException {
        delete(new File(context.getFilesDir(), Constants.CONFIG_FILE));
        delete(new File(context.getFilesDir(), Constants.PUBLIC_KEY_FILE));
        delete(new File(context.getFilesDir(), Constants.PRIVATE_KEY_FILE));
        delete(new File(context.getFilesDir(), Constants.HTTPS_CERT_FILE));
        delete(new File(context.getFilesDir(), Constants.HTTPS_KEY_FILE));
        delete(Constants.getIndexDbFolder(context));
        delete(new File(context.getFilesDir(), Constants.SHARED_PREFS_FILE));
        delete(new File(context.getFilesDir(), "syncthing.log"));
        delete(NormalSyncthingStateTransfer.stagingBase(context));
    }

    private static void write(File file, String value) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Unable to create test directory");
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void delete(File file) throws IOException {
        if (Files.isSymbolicLink(file.toPath())) {
            Files.deleteIfExists(file.toPath());
            return;
        }
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
}
