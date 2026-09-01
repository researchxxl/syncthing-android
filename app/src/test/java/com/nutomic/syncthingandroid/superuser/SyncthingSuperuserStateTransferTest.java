package com.nutomic.syncthingandroid.superuser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;

import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.state.NormalSyncthingStateTransfer;
import com.nutomic.syncthingandroid.service.state.SyncthingStateAccessException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SyncthingSuperuserStateTransferTest {

    private final List<Path> mTemporaryRoots = new ArrayList<>();

    @After
    public void removeTemporaryRoots() throws IOException {
        for (Path root : mTemporaryRoots) {
            delete(root.toFile());
        }
    }

    @Test
    public void rootStageOwnsUuidAndRestoresContextForEveryTreeEntry() throws Exception {
        Path root = newTemporaryRoot();
        File filesDir = new File(root.toFile(), "files");
        File cacheDir = new File(root.toFile(), "cache");
        assertTrue(filesDir.mkdir());
        assertTrue(cacheDir.mkdir());
        write(new File(filesDir, Constants.CONFIG_FILE), "config");
        File index = new File(filesDir, "index-v2");
        assertTrue(index.mkdir());
        write(new File(index, "db"), "index");

        List<String> chowned = new ArrayList<>();
        List<String> restored = new ArrayList<>();
        SuperuserStateTransferOperations operations = recordingOperations(chowned, restored,
                false);
        String transferId = "123e4567-e89b-12d3-a456-426614174000";

        SuperuserOperationResult result = SyncthingSuperuserService.stageBackupStateSnapshot(
                transferId, 12000, 12000, 12000, filesDir, cacheDir, operations);

        assertTrue(result.diagnostic, result.isSuccess());
        File staging = new File(new File(cacheDir,
                NormalSyncthingStateTransfer.STAGING_DIRECTORY), transferId);
        Set<String> expectedEntries = Set.of(
                staging.getAbsolutePath(),
                new File(staging, Constants.CONFIG_FILE).getAbsolutePath(),
                new File(staging, "index-v2").getAbsolutePath(),
                new File(staging, "index-v2/db").getAbsolutePath());
        assertEquals(expectedEntries, new HashSet<>(chowned));
        assertEquals(expectedEntries, new HashSet<>(restored));
        assertEquals(expectedEntries.size(), chowned.size());
        assertEquals(expectedEntries.size(), restored.size());
    }

    @Test
    public void clientAcceptsServiceCreatedClosedSnapshot() throws Exception {
        Path root = newTemporaryRoot();
        File cacheDir = new File(root.toFile(), "cache");
        assertTrue(cacheDir.mkdir());
        Context context = transferContext(cacheDir);
        FakeStateTransferClient client = new FakeStateTransferClient(context,
                StageOutcome.VALID);

        File staged = new SuperuserSyncthingStateTransfer(context, client).stageBackupState();

        assertEquals(client.staging, staged);
        assertEquals(Set.of(Constants.CONFIG_FILE, Constants.PUBLIC_KEY_FILE,
                        Constants.PRIVATE_KEY_FILE, Constants.HTTPS_CERT_FILE,
                        Constants.HTTPS_KEY_FILE, "index-v2"), topLevelNames(staged));
    }

    @Test
    public void clientRejectsUnsupportedServiceEntryAndCleansServiceCreatedDirectory()
            throws Exception {
        Path root = newTemporaryRoot();
        File cacheDir = new File(root.toFile(), "cache");
        assertTrue(cacheDir.mkdir());
        Context context = transferContext(cacheDir);
        FakeStateTransferClient client = new FakeStateTransferClient(context,
                StageOutcome.UNSUPPORTED_ENTRY);

        try {
            new SuperuserSyncthingStateTransfer(context, client).stageBackupState();
            throw new AssertionError("Expected unsupported service staging entry to be rejected");
        } catch (SyncthingStateAccessException expected) {
            // A successful Binder response proves ownership of this transfer directory.
        }

        assertFalse(client.staging.exists());
    }

    @Test
    public void clientDoesNotCleanUuidPathAfterBinderFailure() throws Exception {
        Path root = newTemporaryRoot();
        File cacheDir = new File(root.toFile(), "cache");
        assertTrue(cacheDir.mkdir());
        Context context = transferContext(cacheDir);
        FakeStateTransferClient client = new FakeStateTransferClient(context,
                StageOutcome.BINDER_FAILURE_WITH_DIRECTORY);

        try {
            new SuperuserSyncthingStateTransfer(context, client).stageBackupState();
            throw new AssertionError("Expected Binder staging failure");
        } catch (SyncthingStateAccessException expected) {
            // A failed Binder response does not prove that the client-created UUID is owned by
            // this invocation, so the client must not remove it.
        }

        assertTrue(client.staging.isDirectory());
        assertTrue(new File(client.staging, "failure-marker").isFile());
    }

    @Test
    public void preExistingUuidIsRejectedAndPreserved() throws Exception {
        Path root = newTemporaryRoot();
        File filesDir = new File(root.toFile(), "files");
        File cacheDir = new File(root.toFile(), "cache");
        assertTrue(filesDir.mkdir());
        assertTrue(cacheDir.mkdir());
        File base = new File(cacheDir, NormalSyncthingStateTransfer.STAGING_DIRECTORY);
        assertTrue(base.mkdir());
        String transferId = UUID.randomUUID().toString();
        File existing = new File(base, transferId);
        assertTrue(existing.mkdir());

        SuperuserOperationResult result = SyncthingSuperuserService.stageBackupStateSnapshot(
                transferId, 12000, 12000, 12000, filesDir, cacheDir,
                recordingOperations(new ArrayList<>(), new ArrayList<>(), false));

        assertFalse(result.isSuccess());
        assertEquals(result.diagnostic, SuperuserErrorCode.INVALID_REQUEST, result.error());
        assertTrue(existing.isDirectory());
    }

    @Test
    public void symlinkedPreExistingUuidIsRejectedAndPreserved() throws Exception {
        Path root = newTemporaryRoot();
        File filesDir = new File(root.toFile(), "files");
        File cacheDir = new File(root.toFile(), "cache");
        assertTrue(filesDir.mkdir());
        assertTrue(cacheDir.mkdir());
        File base = new File(cacheDir, NormalSyncthingStateTransfer.STAGING_DIRECTORY);
        assertTrue(base.mkdir());
        File target = new File(base, UUID.randomUUID().toString());
        assertTrue(target.mkdir());
        String transferId = UUID.randomUUID().toString();
        File existing = new File(base, transferId);
        Files.createSymbolicLink(existing.toPath(), target.toPath());

        SuperuserOperationResult result = SyncthingSuperuserService.stageBackupStateSnapshot(
                transferId, 12000, 12000, 12000, filesDir, cacheDir,
                recordingOperations(new ArrayList<>(), new ArrayList<>(), false));

        assertFalse(result.isSuccess());
        assertEquals(SuperuserErrorCode.INVALID_REQUEST, result.error());
        assertTrue(Files.isSymbolicLink(existing.toPath()));
        assertTrue(target.isDirectory());
    }

    @Test
    public void symlinkedFixedBaseIsRejectedWithoutCreatingOutsideIt() throws Exception {
        Path root = newTemporaryRoot();
        File filesDir = new File(root.toFile(), "files");
        File cacheDir = new File(root.toFile(), "cache");
        File outside = new File(root.toFile(), "outside");
        assertTrue(filesDir.mkdir());
        assertTrue(cacheDir.mkdir());
        assertTrue(outside.mkdir());
        File base = new File(cacheDir, NormalSyncthingStateTransfer.STAGING_DIRECTORY);
        Files.createSymbolicLink(base.toPath(), outside.toPath());
        String transferId = UUID.randomUUID().toString();

        SuperuserOperationResult result = SyncthingSuperuserService.stageBackupStateSnapshot(
                transferId, 12000, 12000, 12000, filesDir, cacheDir,
                recordingOperations(new ArrayList<>(), new ArrayList<>(), false));

        assertFalse(result.isSuccess());
        assertFalse(new File(outside, transferId).exists());
    }

    @Test
    public void unsafeSourceDescendantFailsAndCreatedTransferIsRemoved() throws Exception {
        Path root = newTemporaryRoot();
        File filesDir = new File(root.toFile(), "files");
        File cacheDir = new File(root.toFile(), "cache");
        File outside = new File(root.toFile(), "outside");
        assertTrue(filesDir.mkdir());
        assertTrue(cacheDir.mkdir());
        assertTrue(outside.mkdir());
        File unsafeSource = new File(filesDir, "index-v2");
        Files.createSymbolicLink(unsafeSource.toPath(), outside.toPath());
        String transferId = UUID.randomUUID().toString();

        SuperuserOperationResult result = SyncthingSuperuserService.stageBackupStateSnapshot(
                transferId, 12000, 12000, 12000, filesDir, cacheDir,
                recordingOperations(new ArrayList<>(), new ArrayList<>(), false));

        File staging = new File(new File(cacheDir,
                NormalSyncthingStateTransfer.STAGING_DIRECTORY), transferId);
        assertFalse(result.isSuccess());
        assertEquals(SuperuserErrorCode.STATE_TRANSFER_FAILED, result.error());
        assertFalse(staging.exists());
        assertEquals(0, outside.list().length);
    }

    @Test
    public void failureCleansOnlyTheUuidCreatedByThisInvocation() throws Exception {
        Path root = newTemporaryRoot();
        File filesDir = new File(root.toFile(), "files");
        File cacheDir = new File(root.toFile(), "cache");
        assertTrue(filesDir.mkdir());
        assertTrue(cacheDir.mkdir());
        write(new File(filesDir, Constants.CONFIG_FILE), "config");
        String transferId = UUID.randomUUID().toString();
        File[] deleted = new File[1];
        SuperuserStateTransferOperations operations = recordingOperations(
                new ArrayList<>(), new ArrayList<>(), true, false, deleted);

        SuperuserOperationResult result = SyncthingSuperuserService.stageBackupStateSnapshot(
                transferId, 12000, 12000, 12000, filesDir, cacheDir, operations);

        File staging = new File(new File(cacheDir,
                NormalSyncthingStateTransfer.STAGING_DIRECTORY), transferId);
        assertFalse(result.isSuccess());
        assertEquals(result.diagnostic, SuperuserErrorCode.STATE_TRANSFER_FAILED, result.error());
        assertFalse(staging.exists());
        assertEquals(staging.getAbsolutePath(), deleted[0].getAbsolutePath());
    }

    @Test
    public void contextFailureCleansOnlyTheUuidCreatedByThisInvocation() throws Exception {
        Path root = newTemporaryRoot();
        File filesDir = new File(root.toFile(), "files");
        File cacheDir = new File(root.toFile(), "cache");
        assertTrue(filesDir.mkdir());
        assertTrue(cacheDir.mkdir());
        write(new File(filesDir, Constants.CONFIG_FILE), "config");
        String transferId = UUID.randomUUID().toString();
        File[] deleted = new File[1];
        SuperuserStateTransferOperations operations = recordingOperations(
                new ArrayList<>(), new ArrayList<>(), false, true, deleted);

        SuperuserOperationResult result = SyncthingSuperuserService.stageBackupStateSnapshot(
                transferId, 12000, 12000, 12000, filesDir, cacheDir, operations);

        File staging = new File(new File(cacheDir,
                NormalSyncthingStateTransfer.STAGING_DIRECTORY), transferId);
        assertFalse(result.isSuccess());
        assertEquals(SuperuserErrorCode.STATE_TRANSFER_FAILED, result.error());
        assertFalse(staging.exists());
        assertEquals(staging.getAbsolutePath(), deleted[0].getAbsolutePath());
    }

    @Test
    public void copyFailureCleansOnlyTheUuidCreatedByThisInvocation() throws Exception {
        Path root = newTemporaryRoot();
        File filesDir = new File(root.toFile(), "files");
        File cacheDir = new File(root.toFile(), "cache");
        assertTrue(filesDir.mkdir());
        assertTrue(cacheDir.mkdir());
        write(new File(filesDir, Constants.CONFIG_FILE), "config");

        File base = new File(cacheDir, NormalSyncthingStateTransfer.STAGING_DIRECTORY);
        assertTrue(base.mkdir());
        String existingId = UUID.randomUUID().toString();
        File existing = new File(base, existingId);
        assertTrue(existing.mkdir());
        write(new File(existing, "marker"), "preserve");

        String transferId = UUID.randomUUID().toString();
        File[] deleted = new File[1];
        SuperuserStateTransferOperations operations = recordingOperations(
                new ArrayList<>(), new ArrayList<>(), false, false, true, deleted);

        SuperuserOperationResult result = SyncthingSuperuserService.stageBackupStateSnapshot(
                transferId, 12000, 12000, 12000, filesDir, cacheDir, operations);

        File staging = new File(base, transferId);
        assertFalse(result.isSuccess());
        assertEquals(SuperuserErrorCode.STATE_TRANSFER_FAILED, result.error());
        assertFalse(staging.exists());
        assertEquals(staging.getAbsolutePath(), deleted[0].getAbsolutePath());
        assertTrue(existing.isDirectory());
        assertTrue(new File(existing, "marker").isFile());
    }

    private SuperuserStateTransferOperations recordingOperations(List<String> chowned,
                                                                  List<String> restored,
                                                                  boolean failChown) {
        return recordingOperations(chowned, restored, failChown, false, false, new File[1]);
    }

    private SuperuserStateTransferOperations recordingOperations(List<String> chowned,
                                                                  List<String> restored,
                                                                  boolean failChown,
                                                                  boolean failRestore,
                                                                  File[] deleted) {
        return recordingOperations(chowned, restored, failChown, failRestore, false, deleted);
    }

    private SuperuserStateTransferOperations recordingOperations(List<String> chowned,
                                                                  List<String> restored,
                                                                  boolean failChown,
                                                                  boolean failRestore,
                                                                  boolean failCopy,
                                                                  File[] deleted) {
        return new SuperuserStateTransferOperations() {
            @Override
            public void copyTree(File source, File target, File sourceRoot) throws IOException {
                if (failCopy) {
                    write(target, "partial");
                    throw new IOException("intentional copy failure");
                }
                NormalSyncthingStateTransfer.copyTree(source, target, sourceRoot);
            }

            @Override
            public void chown(File file, int uid, int gid) throws IOException {
                if (failChown) {
                    throw new IOException("intentional ownership failure");
                }
                assertEquals(12000, uid);
                assertEquals(12000, gid);
                chowned.add(file.getAbsolutePath());
            }

            @Override
            public void restoreContext(File file) throws IOException {
                if (failRestore) {
                    throw new IOException("intentional context failure");
                }
                restored.add(file.getAbsolutePath());
            }

            @Override
            public void deleteTree(File file) throws IOException {
                deleted[0] = file;
                NormalSyncthingStateTransfer.deleteTree(file);
            }
        };
    }

    private static Context transferContext(File cacheDir) {
        Context application = RuntimeEnvironment.getApplication();
        return new ContextWrapper(application) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public File getCacheDir() {
                return cacheDir;
            }
        };
    }

    private static Set<String> topLevelNames(File directory) {
        File[] entries = directory.listFiles();
        assertTrue(entries != null);
        Set<String> names = new HashSet<>();
        for (File entry : entries) {
            names.add(entry.getName());
        }
        return names;
    }

    private enum StageOutcome {
        VALID,
        UNSUPPORTED_ENTRY,
        BINDER_FAILURE_WITH_DIRECTORY
    }

    private static final class FakeStateTransferClient implements SuperuserStateTransferClient {
        private final Context context;
        private final StageOutcome outcome;
        private File staging;

        private FakeStateTransferClient(Context context, StageOutcome outcome) {
            this.context = context;
            this.outcome = outcome;
        }

        @Override
        public SuperuserOperationResult connectBlocking(long timeoutMs) {
            return SuperuserOperationResult.success();
        }

        @Override
        public SuperuserOperationResult verifySuperuser() {
            return SuperuserOperationResult.success();
        }

        @Override
        public SuperuserOperationResult stageBackupState(String transferId, int appUid,
                                                          int appGid) {
            File base = NormalSyncthingStateTransfer.stagingBase(context);
            staging = new File(base, transferId);
            if (!staging.mkdir()) {
                return SuperuserOperationResult.failure(SuperuserErrorCode.STATE_TRANSFER_FAILED,
                        "Unable to create fake service staging directory");
            }
            try {
                if (outcome == StageOutcome.BINDER_FAILURE_WITH_DIRECTORY) {
                    write(new File(staging, "failure-marker"), "partial");
                    return SuperuserOperationResult.failure(
                            SuperuserErrorCode.STATE_TRANSFER_FAILED,
                            "Intentional Binder staging failure");
                }
                write(new File(staging, Constants.CONFIG_FILE), "config");
                write(new File(staging, Constants.PUBLIC_KEY_FILE), "public");
                write(new File(staging, Constants.PRIVATE_KEY_FILE), "private");
                write(new File(staging, Constants.HTTPS_CERT_FILE), "https-cert");
                write(new File(staging, Constants.HTTPS_KEY_FILE), "https-key");
                File index = new File(staging, "index-v2");
                assertTrue(index.mkdir());
                write(new File(index, "db"), "index");
                if (outcome == StageOutcome.UNSUPPORTED_ENTRY) {
                    write(new File(staging, "unsupported-entry"), "unsupported");
                }
                return SuperuserOperationResult.success();
            } catch (IOException exception) {
                return SuperuserOperationResult.failure(SuperuserErrorCode.STATE_TRANSFER_FAILED,
                        "Unable to prepare fake service staging directory");
            }
        }

        @Override
        public SuperuserOperationResult installBackupState(String transferId) {
            return SuperuserOperationResult.success();
        }
    }

    private Path newTemporaryRoot() throws IOException {
        Path root = Files.createTempDirectory("syncthing-state-transfer-");
        mTemporaryRoots.add(root);
        return root;
    }

    private static void write(File file, String value) throws IOException {
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
