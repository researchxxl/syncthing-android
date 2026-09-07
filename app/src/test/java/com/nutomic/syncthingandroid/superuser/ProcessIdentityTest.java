package com.nutomic.syncthingandroid.superuser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class ProcessIdentityTest {

    @Test
    public void procStatParsingHandlesNormalAndSpaceContainingNames() {
        String[] fixtures = {
                "123 (libsyncthingnative.so) S 42 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 987654",
                "123 (name with spaces) S 42 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 987654"
        };

        for (String fixture : fixtures) {
            ProcessIdentityStore.ProcStat stat = ProcessIdentityStore.parseProcStat(fixture);
            assertEquals(42, stat.parentPid());
            assertEquals(987654L, stat.startTimeTicks());
        }
    }

    @Test
    public void recordRoundTripPreservesRunningIdentity() throws Exception {
        Path directory = Files.createTempDirectory("identity-store");
        File recordFile = directory.resolve("identity").toFile();
        ProcessIdentityStore store = new ProcessIdentityStore(recordFile,
                new JavaSecureFileAccess());
        ProcessIdentity identity = new ProcessIdentity(123, 987654L,
                "/data/user/0/app/files/libsyncthingnative.so");

        assertEquals(true, store.ensureExists());
        assertEquals(true, store.writeRunning(identity));
        ProcessIdentityStore.Snapshot snapshot = store.read();

        assertEquals(ProcessIdentityRecordState.RUNNING, snapshot.state());
        assertEquals(identity, snapshot.identity());
        assertNotNull(Files.readString(recordFile.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void malformedExistingRecordIsCorruptAndNeverCoercedToClear() throws Exception {
        Path directory = Files.createTempDirectory("identity-store-corrupt");
        File recordFile = directory.resolve("identity").toFile();
        Files.writeString(recordFile.toPath(), "truncated", StandardCharsets.UTF_8);
        ProcessIdentityStore store = new ProcessIdentityStore(recordFile,
                new JavaSecureFileAccess());

        assertEquals(ProcessIdentityRecordState.CORRUPT, store.read().state());
    }

    @Test
    public void symlinkRecordIsRejectedWithoutFollowingItsTarget() throws Exception {
        Path directory = Files.createTempDirectory("identity-store-symlink");
        Path target = directory.resolve("target");
        Files.writeString(target, "sentinel", StandardCharsets.UTF_8);
        Path record = directory.resolve("identity");
        Files.createSymbolicLink(record, target.getFileName());
        ProcessIdentityStore store = new ProcessIdentityStore(record.toFile(),
                new JavaSecureFileAccess());

        assertFalse(store.ensureExists());
        assertFalse(store.writeRunning(new ProcessIdentity(123, 987654L, "/bin/syncthing")));
        assertEquals("sentinel", Files.readString(target, StandardCharsets.UTF_8));
        assertEquals(ProcessIdentityRecordState.CORRUPT, store.read().state());
    }
}
