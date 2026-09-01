package com.nutomic.syncthingandroid.superuser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
        ProcessIdentityStore store = new ProcessIdentityStore(recordFile);
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
        ProcessIdentityStore store = new ProcessIdentityStore(recordFile);

        assertEquals(ProcessIdentityRecordState.CORRUPT, store.read().state());
    }
}
