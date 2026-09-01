package com.nutomic.syncthingandroid.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Environment;

import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.service.state.NormalSyncthingStateTransfer;
import com.nutomic.syncthingandroid.service.state.SyncthingStateAccessException;
import com.nutomic.syncthingandroid.service.state.SyncthingStateTransfer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

/** Regression coverage for transactional configuration export. */
@RunWith(RobolectricTestRunner.class)
@Config(application = SyncthingApp.class, manifest = Config.NONE)
public class SyncthingServiceExportTest {

    private Path mTemporaryRoot;

    @After
    public void removeTemporaryRoot() throws IOException {
        if (mTemporaryRoot != null) {
            delete(mTemporaryRoot.toFile());
        }
    }

    @Test
    public void exportDoesNotDeleteThePreviousArchiveBeforeWriting() throws IOException {
        String source = Files.readString(repoPath().resolve(
                "app/src/main/java/com/nutomic/syncthingandroid/service/SyncthingService.java"),
                StandardCharsets.UTF_8);

        assertFalse(source.contains("targetZip.delete()"));
        assertTrue(source.contains("File.createTempFile"));
    }

    @Test
    public void archiveWriteFailureLeavesPreviousArchiveIntactAndCleansTemporaryFile()
            throws Exception {
        File root = newTemporaryRoot().toFile();
        File target = new File(root, "config.zip");
        byte[] previous = "previous archive".getBytes(StandardCharsets.UTF_8);
        Files.write(target.toPath(), previous);
        File staged = createStagedState(root, "new state");
        File preferences = writeFile(new File(root, "sharedpreferences.dat"), "preferences");
        File[] temporary = new File[1];

        try {
            SyncthingService.writeTransactionalBackup(target, staged, preferences, "",
                    (destination, ignoredState, ignoredPreferences, ignoredPassword) -> {
                        temporary[0] = destination;
                        Files.write(destination.toPath(), "partial archive".getBytes(
                                StandardCharsets.UTF_8));
                        throw new IOException("intentional archive failure");
                    });
            throw new AssertionError("Expected archive writing to fail");
        } catch (IOException expected) {
            // A failed temporary write must not touch the valid destination archive.
        }

        assertArrayEquals(previous, Files.readAllBytes(target.toPath()));
        assertTrue(temporary[0] != null);
        assertFalse(temporary[0].exists());
    }

    @Test
    public void replacementFailureLeavesExistingDestinationUntouched() throws Exception {
        File root = newTemporaryRoot().toFile();
        File targetDirectory = new File(root, "config.zip");
        assertTrue(targetDirectory.mkdir());
        File staged = createStagedState(root, "new state");
        File preferences = writeFile(new File(root, "sharedpreferences.dat"), "preferences");

        try {
            SyncthingService.writeTransactionalBackup(targetDirectory, staged, preferences, "",
                    (destination, ignoredState, ignoredPreferences, ignoredPassword) ->
                            writeFile(destination, "archive"));
            throw new AssertionError("Expected replacement to fail for a directory target");
        } catch (IOException expected) {
            // Replacement is attempted only after the complete temporary archive is closed.
        }

        assertTrue(targetDirectory.isDirectory());
    }

    @Test
    public void successfulArchiveUsesFreshStateAndPreservesZipFormat() throws Exception {
        File root = newTemporaryRoot().toFile();
        File target = new File(root, "config.zip");
        File legacy = new File(root, "legacy.zip");
        File staged = createStagedState(root, "new state");
        File preferences = writeFile(new File(root, "sharedpreferences.dat"), "preferences");
        createStaleArchive(target, root);
        writeLegacyArchive(legacy, staged, preferences);

        SyncthingService.writeTransactionalBackup(target, staged, preferences, "",
                SyncthingService::writeBackupArchive);

        Set<String> expectedNames;
        try (ZipFile zipFile = new ZipFile(legacy)) {
            expectedNames = new HashSet<>();
            for (FileHeader header : zipFile.getFileHeaders()) {
                expectedNames.add(header.getFileName());
            }
        }
        try (ZipFile zipFile = new ZipFile(target)) {
            List<FileHeader> headers = zipFile.getFileHeaders();
            Set<String> names = new HashSet<>();
            for (FileHeader header : headers) {
                names.add(header.getFileName());
            }
            assertEquals(expectedNames, names);
            assertTrue(names.contains("config.xml"));
            assertTrue(names.contains("index-v2/db"));
            assertFalse(names.contains("stale-entry"));
            assertFalse(zipFile.isEncrypted());
            assertEquals(CompressionMethod.DEFLATE,
                    zipFile.getFileHeader("config.xml").getCompressionMethod());
            try (InputStream input = zipFile.getInputStream(zipFile.getFileHeader("config.xml"))) {
                assertArrayEquals("new state".getBytes(StandardCharsets.UTF_8),
                        input.readAllBytes());
            }
        }
    }

    @Test
    public void archiveStoresSharedPreferencesAtZipRootWhenSourceIsInFilesDirectory()
            throws Exception {
        File root = newTemporaryRoot().toFile();
        File target = new File(root, "config.zip");
        File staged = createStagedState(root, "state");
        File filesDirectory = new File(root, "files");
        assertTrue(filesDirectory.mkdir());
        File preferences = writeFile(
                new File(filesDirectory, Constants.SHARED_PREFS_FILE), "preferences");

        SyncthingService.writeBackupArchive(target, staged, preferences, "");

        try (ZipFile zipFile = new ZipFile(target)) {
            assertTrue(zipFile.getFileHeader(Constants.SHARED_PREFS_FILE) != null);
            assertTrue(zipFile.getFileHeader("files/" + Constants.SHARED_PREFS_FILE) == null);
        }
    }

    @Test
    public void successfulEncryptedArchiveRetainsAesEncryption() throws Exception {
        File root = newTemporaryRoot().toFile();
        File target = new File(root, "config.zip");
        File staged = createStagedState(root, "encrypted state");
        File preferences = writeFile(new File(root, "sharedpreferences.dat"), "preferences");

        SyncthingService.writeTransactionalBackup(target, staged, preferences, "secret",
                SyncthingService::writeBackupArchive);

        try (ZipFile zipFile = new ZipFile(target, "secret".toCharArray())) {
            assertTrue(zipFile.isEncrypted());
            FileHeader header = zipFile.getFileHeader("config.xml");
            assertTrue(header.isEncrypted());
            assertEquals(EncryptionMethod.AES, header.getEncryptionMethod());
        }
    }

    @Test
    public void exportConfigLeavesPreviousArchiveWhenStateStagingFails() throws Exception {
        ServiceController<SyncthingService> controller = Robolectric
                .buildService(SyncthingService.class).create();
        SyncthingService service = controller.get();
        String relativePath = "backups/syncthing/export-regression-" + UUID.randomUUID() + ".zip";
        service.mPreferences.edit()
                .putString(Constants.PREF_BACKUP_REL_PATH_TO_ZIP, relativePath)
                .commit();
        File target = new File(Environment.getExternalStorageDirectory(), relativePath);
        File targetParent = target.getParentFile();
        assertTrue(targetParent == null || targetParent.isDirectory() || targetParent.mkdirs());
        byte[] previous = "previous archive".getBytes(StandardCharsets.UTF_8);
        Files.write(target.toPath(), previous);
        service.mStateTransfer = new SyncthingStateTransfer() {
            @Override
            public File stageBackupState() throws SyncthingStateAccessException {
                throw new SyncthingStateAccessException("intentional staging failure");
            }

            @Override
            public void installBackupState(File stagedState) {
                throw new AssertionError("Import should not be used by export");
            }
        };

        try {
            assertFalse(service.exportConfig());
            assertArrayEquals(previous, Files.readAllBytes(target.toPath()));
            assertFalse(Constants.getSharedPrefsFile(service).exists());
        } finally {
            delete(target);
        }
    }

    @Test
    public void exportConfigRejectsStateOutsideFixedStagingBase() throws Exception {
        ServiceController<SyncthingService> controller = Robolectric
                .buildService(SyncthingService.class).create();
        SyncthingService service = controller.get();
        String relativePath = "backups/syncthing/outside-staging-" + UUID.randomUUID() + ".zip";
        service.mPreferences.edit()
                .putString(Constants.PREF_BACKUP_REL_PATH_TO_ZIP, relativePath)
                .commit();
        File target = new File(Environment.getExternalStorageDirectory(), relativePath);
        File targetParent = target.getParentFile();
        assertTrue(targetParent == null || targetParent.isDirectory() || targetParent.mkdirs());
        byte[] previous = "previous archive".getBytes(StandardCharsets.UTF_8);
        Files.write(target.toPath(), previous);

        File outsideBase = new File(newTemporaryRoot().toFile(), "outside-base");
        assertTrue(outsideBase.mkdir());
        File staged = new File(outsideBase, UUID.randomUUID().toString());
        assertTrue(staged.mkdir());
        writeFile(new File(staged, Constants.CONFIG_FILE), "outside state");
        service.mStateTransfer = new SyncthingStateTransfer() {
            @Override
            public File stageBackupState() {
                return staged;
            }

            @Override
            public void installBackupState(File stagedState) {
                throw new AssertionError("Import should not be used by export");
            }
        };

        try {
            assertFalse(service.exportConfig());
            assertArrayEquals(previous, Files.readAllBytes(target.toPath()));
            assertTrue(staged.isDirectory());
        } finally {
            delete(target);
        }
    }

    @Test
    public void exportConfigRejectsUnsupportedStagedEntry() throws Exception {
        ServiceController<SyncthingService> controller = Robolectric
                .buildService(SyncthingService.class).create();
        SyncthingService service = controller.get();
        String relativePath = "backups/syncthing/unsupported-staging-" + UUID.randomUUID() + ".zip";
        service.mPreferences.edit()
                .putString(Constants.PREF_BACKUP_REL_PATH_TO_ZIP, relativePath)
                .commit();
        File target = new File(Environment.getExternalStorageDirectory(), relativePath);
        File targetParent = target.getParentFile();
        assertTrue(targetParent == null || targetParent.isDirectory() || targetParent.mkdirs());
        byte[] previous = "previous archive".getBytes(StandardCharsets.UTF_8);
        Files.write(target.toPath(), previous);

        File base = NormalSyncthingStateTransfer.stagingBase(service);
        assertTrue(base.mkdir() || base.isDirectory());
        File staged = new File(base, UUID.randomUUID().toString());
        assertTrue(staged.mkdir());
        writeFile(new File(staged, Constants.CONFIG_FILE), "state");
        writeFile(new File(staged, "unsupported-entry"), "unsupported");
        service.mStateTransfer = new SyncthingStateTransfer() {
            @Override
            public File stageBackupState() {
                return staged;
            }

            @Override
            public void installBackupState(File stagedState) {
                throw new AssertionError("Import should not be used by export");
            }
        };

        try {
            assertFalse(service.exportConfig());
            assertArrayEquals(previous, Files.readAllBytes(target.toPath()));
            assertFalse(staged.exists());
        } finally {
            delete(target);
        }
    }

    @Test
    public void exportConfigWritesNormalModeStateAndCleansStaging() throws Exception {
        ServiceController<SyncthingService> controller = Robolectric
                .buildService(SyncthingService.class).create();
        SyncthingService service = controller.get();
        String relativePath = "backups/syncthing/normal-export-" + UUID.randomUUID() + ".zip";
        service.mPreferences.edit()
                .putString(Constants.PREF_BACKUP_REL_PATH_TO_ZIP, relativePath)
                .putString(Constants.PREF_BACKUP_PASSWORD, "")
                .commit();
        File target = new File(Environment.getExternalStorageDirectory(), relativePath);
        File targetParent = target.getParentFile();
        assertTrue(targetParent == null || targetParent.isDirectory() || targetParent.mkdirs());
        File base = NormalSyncthingStateTransfer.stagingBase(service);
        assertTrue(base.mkdir() || base.isDirectory());
        File staged = new File(base, UUID.randomUUID().toString());
        assertTrue(staged.mkdir());
        writeFile(new File(staged, "config.xml"), "normal state");
        service.mStateTransfer = new SyncthingStateTransfer() {
            @Override
            public File stageBackupState() {
                return staged;
            }

            @Override
            public void installBackupState(File stagedState) {
                throw new AssertionError("Import should not be used by export");
            }
        };

        try {
            assertTrue(service.exportConfig());
            assertTrue(target.isFile());
            assertFalse(staged.exists());
            assertFalse(Constants.getSharedPrefsFile(service).exists());
            try (ZipFile zipFile = new ZipFile(target)) {
                assertTrue(zipFile.getFileHeader("config.xml") != null);
                assertTrue(zipFile.getFileHeader(Constants.SHARED_PREFS_FILE) != null);
                assertFalse(zipFile.getFileHeaders().stream().anyMatch(header ->
                        header.getFileName().endsWith("/" + Constants.SHARED_PREFS_FILE)));
            }
        } finally {
            delete(target);
            if (staged.exists()) {
                NormalSyncthingStateTransfer.deleteTree(staged);
            }
        }
    }

    private Path newTemporaryRoot() throws IOException {
        mTemporaryRoot = Files.createTempDirectory("syncthing-export-");
        return mTemporaryRoot;
    }

    private static File createStagedState(File root, String configContents) throws IOException {
        File base = new File(root, NormalSyncthingStateTransfer.STAGING_DIRECTORY);
        assertTrue(base.mkdir());
        File staged = new File(base, UUID.randomUUID().toString());
        assertTrue(staged.mkdir());
        writeFile(new File(staged, "config.xml"), configContents);
        File index = new File(staged, "index-v2");
        assertTrue(index.mkdir());
        writeFile(new File(index, "db"), "index");
        return staged;
    }

    private static void writeLegacyArchive(File destination, File stagedState,
                                           File sharedPreferencesFile) throws Exception {
        ZipParameters parameters = new ZipParameters();
        parameters.setCompressionMethod(CompressionMethod.DEFLATE);
        try (ZipFile zipFile = new ZipFile(destination)) {
            for (File includePath : stagedState.listFiles()) {
                if (includePath.isFile()) {
                    zipFile.addFile(includePath, parameters);
                } else {
                    zipFile.addFolder(includePath, parameters);
                }
            }
            parameters.setFileNameInZip(Constants.SHARED_PREFS_FILE);
            zipFile.addFile(sharedPreferencesFile, parameters);
        }
    }

    private static void createStaleArchive(File destination, File root) throws Exception {
        File stale = writeFile(new File(root, "stale-content"), "stale");
        ZipParameters parameters = new ZipParameters();
        parameters.setFileNameInZip("stale-entry");
        try (ZipFile zipFile = new ZipFile(destination)) {
            zipFile.addFile(stale, parameters);
        }
    }

    private static File writeFile(File file, String contents) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Unable to create test directory");
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
        }
        return file;
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
}
