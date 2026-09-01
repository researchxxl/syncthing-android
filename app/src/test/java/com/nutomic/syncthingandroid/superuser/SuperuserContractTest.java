package com.nutomic.syncthingandroid.superuser;

import static org.junit.Assert.assertTrue;

import com.nutomic.syncthingandroid.service.state.SyncthingStateFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class SuperuserContractTest {

    @Test
    public void aidlContainsOnlyApprovedSemanticCapabilities() throws IOException {
        Path aidl = Path.of("app/src/main/aidl/com/nutomic/syncthingandroid/superuser/ISyncthingSuperuserService.aidl");
        if (!Files.exists(aidl)) {
            aidl = Path.of("src/main/aidl/com/nutomic/syncthingandroid/superuser/ISyncthingSuperuserService.aidl");
        }
        String source = Files.readString(aidl, StandardCharsets.UTF_8);

        assertTrue(source.contains("verifySuperuser"));
        assertTrue(source.contains("recoverOrphanedCore"));
        assertTrue(source.contains("startCore"));
        assertTrue(source.contains("waitForCoreExit"));
        assertTrue(source.contains("stopOwnedCore"));
        assertTrue(source.contains("getCoreStatus"));
        assertTrue(source.contains("testFolderWritable"));
        assertTrue(source.contains("findSyncConflicts"));
        assertTrue(source.contains("openStateFileForRead"));
        assertTrue(source.contains("writeStateFileAtomic"));
        assertTrue(source.contains("deleteStateFile"));
        assertTrue(source.contains("stageBackupState"));
        assertTrue(source.contains("installBackupState"));
        assertTrue(source.contains("repairAppPrivateState"));

        assertTrue(!source.contains("executeAsRoot"));
        assertTrue(!source.contains("runCommand"));
        assertTrue(!source.contains("shell"));
        assertTrue(!source.contains("readFile(String"));
        assertTrue(!source.contains("writeFile(String"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownStateFileIdIsRejected() {
        SyncthingStateFile.fromWireId(99);
    }
}
