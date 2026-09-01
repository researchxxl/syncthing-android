package com.nutomic.syncthingandroid.superuser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

/** Static guardrails for the closed privileged boundary and normal-UID script path. */
public class SuperuserSecurityRegressionTest {

    @Test
    public void rootServiceIsPrivateAndPrivilegedSurfaceIsTyped() throws IOException {
        String manifest = read("app/src/main/AndroidManifest.xml");
        assertTrue(manifest.contains("SyncthingSuperuserService"));
        assertTrue(manifest.contains("android:exported=\"false\""));

        String aidl = readTree("app/src/main/aidl");
        assertFalse(aidl.contains("readStateFile(String"));
        assertFalse(aidl.contains("writeStateFile(String"));
        assertFalse(aidl.contains("executeAsRoot"));
        assertFalse(aidl.contains("runCommandAsRoot"));
        assertFalse(aidl.contains("runShellCommandAsRoot"));
        assertFalse(aidl.contains("executeRootCommand"));
    }

    @Test
    public void productionDoesNotReintroduceGenericRootOrFallbackPaths() throws IOException {
        String productionJava = readTree("app/src/main/java");
        assertFalse(productionJava.contains("executeAsRoot"));
        assertFalse(productionJava.contains("runCommandAsRoot"));
        assertFalse(productionJava.contains("runShellCommandAsRoot"));
        assertFalse(productionJava.contains("executeRootCommand"));

        String util = read("app/src/main/java/com/nutomic/syncthingandroid/util/Util.java");
        int runScriptStart = util.indexOf("runScriptSet");
        assertTrue(runScriptStart >= 0);
        String runScriptSource = util.substring(runScriptStart);
        assertFalse(runScriptSource.contains("SuperuserClient"));
        assertFalse(runScriptSource.contains("RootService"));

        String service = read(
                "app/src/main/java/com/nutomic/syncthingandroid/superuser/SyncthingSuperuserService.java");
        assertFalse(service.contains("SharedPreferences"));
        assertFalse(service.contains("component().inject"));

        String importSource = read(
                "app/src/main/java/com/nutomic/syncthingandroid/service/SyncthingService.java");
        assertTrue(importSource.contains("case \"use_root\":"));
        assertTrue(importSource.contains("PREF_USE_ROOT"));

        String versions = read("gradle/libs.versions.toml").toLowerCase();
        assertFalse(versions.contains("shizuku"));
        assertFalse(versions.contains("sui"));
    }

    @Test
    public void everyRootCapabilityChecksCallerAndServiceUid() throws IOException {
        String service = read(
                "app/src/main/java/com/nutomic/syncthingandroid/superuser/SyncthingSuperuserService.java");
        assertTrue(service.contains("private void enforcePrivilegedCaller()"));
        assertTrue(service.contains("android.os.Process.myUid() != android.os.Process.ROOT_UID"));

        assertBinderMethodGuards(service, "public SuperuserUidResult verifySuperuser()");
        assertBinderMethodGuards(service, "public SuperuserOperationResult recoverOrphanedCore()");
        assertBinderMethodGuards(service, "public SuperuserOperationResult startCore(");
        assertBinderMethodGuards(service, "public SuperuserCoreExitResult waitForCoreExit()");
        assertBinderMethodGuards(service, "public SuperuserOperationResult stopOwnedCore()");
        assertBinderMethodGuards(service, "public SuperuserCoreStatus getCoreStatus()");
        assertBinderMethodGuards(service, "public SuperuserBooleanResult testFolderWritable(");
        assertBinderMethodGuards(service, "public SuperuserStringListResult findSyncConflicts(");
        assertBinderMethodGuards(service, "public SuperuserStateFileResult openStateFileForRead(");
        assertBinderMethodGuards(service, "public SuperuserOperationResult writeStateFileAtomic(");
        assertBinderMethodGuards(service, "public SuperuserOperationResult deleteStateFile(");
        assertBinderMethodGuards(service, "public SuperuserOperationResult stageBackupState(");
        assertBinderMethodGuards(service, "public SuperuserOperationResult installBackupState(");
        assertBinderMethodGuards(service, "public SuperuserOperationResult repairAppPrivateState(");
    }

    @Test
    public void rootOwnershipOperationsBindBothIdsToTheApplication() throws IOException {
        String service = read(
                "app/src/main/java/com/nutomic/syncthingandroid/superuser/SyncthingSuperuserService.java");
        assertTrue(service.contains("appGid != expectedAppUid"));
        assertFalse(service.contains("chownTree(target, appUid, appGid)"));
        assertFalse(service.contains("repairTree(getFilesDir(), appUid, appGid"));
    }

    @Test
    public void rootServiceOwnsUuidDirectoryCreation() throws IOException {
        String client = read(
                "app/src/main/java/com/nutomic/syncthingandroid/superuser/SuperuserSyncthingStateTransfer.java");
        String service = read(
                "app/src/main/java/com/nutomic/syncthingandroid/superuser/SyncthingSuperuserService.java");

        assertFalse(client.contains("staging.mkdir()"));
        assertTrue(service.contains("staging.mkdir()"));
        assertTrue(service.contains("operations.chown(entry"));
        assertTrue(service.contains("operations.restoreContext(entry"));
        assertTrue(service.contains("collectSafeTree(staging, base)"));
    }

    @Test
    public void rootConfiguredImportPreflightsNormalTransitionBeforeStateInstall()
            throws IOException {
        String service = read(
                "app/src/main/java/com/nutomic/syncthingandroid/service/SyncthingService.java");
        int preflight = service.indexOf("prepareForNormalImport");
        int install = service.indexOf("mStateTransfer.installBackupState");
        assertTrue(preflight >= 0);
        assertTrue(install >= 0);
        assertTrue(preflight < install);
        assertTrue(service.contains("if (!rootTransition.isSuccess())"));
    }

    private static void assertBinderMethodGuards(String service, String methodSignature) {
        int methodStart = service.indexOf(methodSignature);
        assertTrue("Missing Binder method: " + methodSignature, methodStart >= 0);
        int bodyStart = service.indexOf('{', methodStart);
        int nextMethod = service.indexOf("\n                @Override", bodyStart);
        String body = service.substring(bodyStart,
                nextMethod < 0 ? service.length() : nextMethod);
        assertTrue("Missing privileged guard: " + methodSignature,
                body.contains("enforcePrivilegedCaller();"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(repoPath().resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static String readTree(String relativePath) throws IOException {
        try (Stream<Path> paths = Files.walk(repoPath().resolve(relativePath))) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }
    }

    private static Path repoPath() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null && !Files.isDirectory(path.resolve("app/src"))) {
            path = path.getParent();
        }
        if (path == null) {
            throw new AssertionError("Could not locate repository root");
        }
        return path;
    }
}
