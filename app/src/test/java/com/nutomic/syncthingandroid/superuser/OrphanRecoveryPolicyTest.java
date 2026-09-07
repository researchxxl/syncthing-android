package com.nutomic.syncthingandroid.superuser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.Test;

public class OrphanRecoveryPolicyTest {

    private static final ProcessIdentity IDENTITY = new ProcessIdentity(
            123, 987654L, "/data/app/libsyncthingnative.so");

    @Test
    public void clearRecordSucceedsWithoutSignaling() throws Exception {
        RecoveryFixture fixture = fixture(ProcessIdentityRecordState.CLEAR);

        OrphanRecoveryPolicy.Result result = fixture.recover();

        assertTrue(result.operation().isSuccess());
        assertFalse(result.orphanRisk());
        assertEquals(0, fixture.signaler.signals.size());
    }

    @Test
    public void launchPendingFailsClosedWithOrphanRisk() throws Exception {
        RecoveryFixture fixture = fixture(ProcessIdentityRecordState.LAUNCH_PENDING);

        OrphanRecoveryPolicy.Result result = fixture.recover();

        assertEquals(SuperuserErrorCode.ORPHAN_RECOVERY_FAILED, result.operation().error());
        assertTrue(result.orphanRisk());
        assertEquals(ProcessIdentityRecordState.LAUNCH_PENDING, fixture.store.read().state());
        assertEquals(0, fixture.signaler.signals.size());
    }

    @Test
    public void corruptRecordFailsClosedWithoutOverwritingOrSignaling() throws Exception {
        Path directory = Files.createTempDirectory("orphan-corrupt");
        File recordFile = directory.resolve("identity").toFile();
        Files.writeString(recordFile.toPath(), "corrupt");
        ProcessIdentityStore store = new ProcessIdentityStore(recordFile,
                new JavaSecureFileAccess());
        RecoveryFixture fixture = new RecoveryFixture(store, ProcessIdentityRecordState.CORRUPT);

        OrphanRecoveryPolicy.Result result = fixture.recover();

        assertEquals(SuperuserErrorCode.ORPHAN_RECOVERY_FAILED, result.operation().error());
        assertTrue(result.orphanRisk());
        assertEquals("corrupt", Files.readString(recordFile.toPath()));
        assertEquals(0, fixture.signaler.signals.size());
    }

    @Test
    public void absentRecordedPidIsClearedAndRecoverySucceeds() throws Exception {
        RecoveryFixture fixture = fixture(ProcessIdentityRecordState.RUNNING, ProbeResult.ABSENT);

        OrphanRecoveryPolicy.Result result = fixture.recover();

        assertTrue(result.operation().isSuccess());
        assertEquals(ProcessIdentityRecordState.CLEAR, fixture.store.read().state());
        assertEquals(0, fixture.signaler.signals.size());
    }

    @Test
    public void matchingIdentityGetsGracefulSignalAndClearsAfterExit() throws Exception {
        RecoveryFixture fixture = fixture(ProcessIdentityRecordState.RUNNING,
                ProbeResult.MATCH, ProbeResult.ABSENT);

        OrphanRecoveryPolicy.Result result = fixture.recover();

        assertTrue(result.operation().isSuccess());
        assertEquals(List.of(OrphanRecoveryPolicy.SIGINT), fixture.signaler.signals);
        assertEquals(ProcessIdentityRecordState.CLEAR, fixture.store.read().state());
    }

    @Test
    public void matchingIdentityUsesKillFallbackOnlyWhileStillMatching() throws Exception {
        RecoveryFixture fixture = fixture(ProcessIdentityRecordState.RUNNING,
                ProbeResult.MATCH, ProbeResult.MATCH, ProbeResult.MATCH, ProbeResult.ABSENT);

        OrphanRecoveryPolicy.Result result = fixture.recover(2, 1);

        assertTrue(result.operation().isSuccess());
        assertEquals(List.of(OrphanRecoveryPolicy.SIGINT, OrphanRecoveryPolicy.SIGKILL),
                fixture.signaler.signals);
        assertEquals(ProcessIdentityRecordState.CLEAR, fixture.store.read().state());
    }

    @Test
    public void mismatchedIdentityIsNeverSignaledOrCleared() throws Exception {
        RecoveryFixture fixture = fixture(ProcessIdentityRecordState.RUNNING, ProbeResult.MISMATCH);

        OrphanRecoveryPolicy.Result result = fixture.recover();

        assertEquals(SuperuserErrorCode.ORPHAN_RECOVERY_FAILED, result.operation().error());
        assertTrue(result.orphanRisk());
        assertEquals(0, fixture.signaler.signals.size());
        assertEquals(ProcessIdentityRecordState.RUNNING, fixture.store.read().state());
    }

    private static RecoveryFixture fixture(ProcessIdentityRecordState state,
                                            ProbeResult... probes) throws Exception {
        Path directory = Files.createTempDirectory("orphan-recovery");
        ProcessIdentityStore store = new ProcessIdentityStore(
                directory.resolve("identity").toFile(), new JavaSecureFileAccess());
        store.ensureExists();
        if (state == ProcessIdentityRecordState.RUNNING) {
            store.writeRunning(IDENTITY);
        } else if (state == ProcessIdentityRecordState.LAUNCH_PENDING) {
            store.writeLaunchPending();
        } else if (state == ProcessIdentityRecordState.CLEAR) {
            store.clear();
        }
        return new RecoveryFixture(store, state, probes);
    }

    private enum ProbeResult {
        ABSENT,
        MATCH,
        MISMATCH,
        UNKNOWN
    }

    private static final class RecoveryFixture {
        private final ProcessIdentityStore store;
        private final FakeProbe probe;
        private final FakeSignaler signaler = new FakeSignaler();

        RecoveryFixture(ProcessIdentityStore store, ProcessIdentityRecordState ignoredState,
                        ProbeResult... probes) {
            this.store = store;
            this.probe = new FakeProbe(probes);
        }

        OrphanRecoveryPolicy.Result recover() {
            return recover(100, 10);
        }

        OrphanRecoveryPolicy.Result recover(long graceMs, long pollMs) {
            return OrphanRecoveryPolicy.recover(store.read(), store, probe, signaler,
                    ignored -> { }, graceMs, pollMs);
        }
    }

    private static final class FakeProbe implements OrphanRecoveryPolicy.ProcessIdentityProbe {
        private final Deque<ProbeResult> results = new ArrayDeque<>();

        FakeProbe(ProbeResult... results) {
            for (ProbeResult result : results) {
                this.results.add(result);
            }
        }

        @Override
        public OrphanRecoveryPolicy.ProbeState probe(ProcessIdentity expected) {
            ProbeResult result = results.isEmpty() ? ProbeResult.UNKNOWN : results.removeFirst();
            return OrphanRecoveryPolicy.ProbeState.valueOf(result.name());
        }
    }

    private static final class FakeSignaler implements OrphanRecoveryPolicy.ProcessSignaler {
        private final List<Integer> signals = new ArrayList<>();

        @Override
        public void signal(ProcessIdentity identity, int signal) {
            signals.add(signal);
        }
    }
}
