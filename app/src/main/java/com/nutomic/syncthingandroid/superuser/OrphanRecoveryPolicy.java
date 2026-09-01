package com.nutomic.syncthingandroid.superuser;

import android.system.OsConstants;

/**
 * Applies the fail-closed decision table for a persisted root-process identity.
 *
 * <p>The policy never signals a PID until a probe has confirmed the recorded PID, start time,
 * and executable as one identity. The small interfaces make the dangerous branches deterministic
 * in JVM tests and keep Android {@code /proc} mechanics outside the decision table.</p>
 */
public final class OrphanRecoveryPolicy {

    public static final int SIGINT = OsConstants.SIGINT;
    public static final int SIGKILL = OsConstants.SIGKILL;

    private OrphanRecoveryPolicy() {
    }

    public enum ProbeState {
        ABSENT,
        MATCH,
        MISMATCH,
        UNKNOWN
    }

    @FunctionalInterface
    interface ProcessIdentityProbe {
        ProbeState probe(ProcessIdentity expected);
    }

    @FunctionalInterface
    interface ProcessSignaler {
        void signal(ProcessIdentity identity, int signal) throws Exception;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long milliseconds) throws InterruptedException;
    }

    public record Result(SuperuserOperationResult operation, boolean orphanRisk) {
    }

    static Result recover(ProcessIdentityStore.Snapshot snapshot, ProcessIdentityStore store,
                          ProcessIdentityProbe probe, ProcessSignaler signaler, Sleeper sleeper,
                          long gracefulWaitMs, long pollIntervalMs) {
        if (snapshot == null || store == null || probe == null || signaler == null
                || sleeper == null || gracefulWaitMs < 0 || pollIntervalMs <= 0) {
            return failure("Invalid orphan-recovery policy input");
        }
        switch (snapshot.state()) {
            case CLEAR:
                return success();
            case LAUNCH_PENDING:
            case CORRUPT:
                return failure("Root process identity cannot be proven");
            case RUNNING:
                if (snapshot.identity() == null) {
                    return failure("Running root process identity is missing");
                }
                return recoverRunning(snapshot.identity(), store, probe, signaler, sleeper,
                        gracefulWaitMs, pollIntervalMs);
            default:
                return failure("Unknown root process identity state");
        }
    }

    private static Result recoverRunning(ProcessIdentity identity, ProcessIdentityStore store,
                                         ProcessIdentityProbe probe, ProcessSignaler signaler,
                                         Sleeper sleeper, long gracefulWaitMs,
                                         long pollIntervalMs) {
        ProbeState initial = probe.probe(identity);
        if (initial == ProbeState.ABSENT) {
            return clear(store);
        }
        if (initial != ProbeState.MATCH) {
            return failure("Recorded root process identity does not match");
        }

        try {
            signaler.signal(identity, SIGINT);
            long waited = 0;
            while (waited < gracefulWaitMs) {
                sleeper.sleep(pollIntervalMs);
                waited += pollIntervalMs;
                ProbeState state = probe.probe(identity);
                if (state == ProbeState.ABSENT) {
                    return clear(store);
                }
                if (state != ProbeState.MATCH) {
                    return failure("Root process identity changed during recovery");
                }
            }

            signaler.signal(identity, SIGKILL);
            sleeper.sleep(pollIntervalMs);
            if (probe.probe(identity) != ProbeState.ABSENT) {
                return failure("Root process survived verified recovery signals");
            }
            return clear(store);
        } catch (Exception exception) {
            return failure("Root process recovery signaling failed");
        }
    }

    private static Result clear(ProcessIdentityStore store) {
        return store.clear() ? success() : failure("Root process identity could not be cleared");
    }

    private static Result success() {
        return new Result(SuperuserOperationResult.success(), false);
    }

    private static Result failure(String diagnostic) {
        return new Result(SuperuserOperationResult.failure(
                SuperuserErrorCode.ORPHAN_RECOVERY_FAILED, diagnostic), true);
    }
}
