package com.nutomic.syncthingandroid.superuser;

import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;

import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.execution.SyncthingCommand;
import com.nutomic.syncthingandroid.service.execution.SyncthingExecutionResult;
import com.nutomic.syncthingandroid.service.execution.SyncthingProcessOutput;
import com.nutomic.syncthingandroid.service.folder.NormalSyncthingFolderAccess;
import com.nutomic.syncthingandroid.service.folder.SyncthingFolderAccessException;
import com.nutomic.syncthingandroid.service.state.NormalSyncthingStateTransfer;
import com.nutomic.syncthingandroid.service.state.SyncthingStateAccessException;
import com.nutomic.syncthingandroid.service.state.SyncthingStateFile;
import com.nutomic.syncthingandroid.util.FileUtils;
import com.topjohnwu.superuser.ipc.RootService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The sole UID-0 Android component used by superuser mode.
 *
 * <p>Each capability rejects callers outside this app before operation-specific work. The
 * service intentionally does not initialize the normal Dagger graph or access preferences. Its
 * backup capability creates and owns the fixed transfer directory before handing it back to the
 * normal process.</p>
 */
public final class SyncthingSuperuserService extends RootService {

    private static final long CHILD_RESOLUTION_TIMEOUT_MS = 1_000L;
    private static final long ORPHAN_GRACEFUL_WAIT_MS = 5_000L;
    private static final long ORPHAN_POLL_INTERVAL_MS = 100L;
    private static final long PROCESS_WAIT_POLL_INTERVAL_MS = 25L;
    private static final long MAX_CONFLICT_RESULT_BYTES = 64L * 1024L;
    private static final int MAX_CONFLICT_RESULT_ITEMS = 512;

    private static final Set<String> ACCEPTED_ENVIRONMENT_KEYS = Set.of(
            "HOME", "STHOMEDIR", "SQLITE_TMPDIR", "STMONITORED", "STNOUPGRADE",
            "STVERSIONEXTRA", "STTRACE", "GOGC", "FALLBACK_NET_GATEWAY_IPV4", "all_proxy",
            "ALL_PROXY_NO_FALLBACK", "http_proxy", "https_proxy");
    private static final Set<String> SERVER_CONTROLLED_ENVIRONMENT_KEYS = Set.of(
            "HOME", "STHOMEDIR", "SQLITE_TMPDIR", "STMONITORED", "STNOUPGRADE",
            "STVERSIONEXTRA");
    private static final SecureFileAccess FILE_ACCESS = SecureFileAccess.production();

    private static final SuperuserStateTransferOperations STATE_TRANSFER_OPERATIONS =
            new SuperuserStateTransferOperations() {
                @Override
                public void copyTree(File source, File target, File sourceRoot)
                        throws IOException {
                    NormalSyncthingStateTransfer.copyTree(source, target, sourceRoot);
                }

                @Override
                public void chown(File file, int uid, int gid)
                        throws IOException, ErrnoException {
                    Os.lchown(file.getPath(), uid, gid);
                }

                @Override
                public void restoreContext(File file) throws IOException, InterruptedException {
                    restoreSelinuxContext(file.getPath());
                }

                @Override
                public void deleteTree(File file) throws IOException {
                    NormalSyncthingStateTransfer.deleteTree(file);
                }
            };

    private final Object mCoreLock = new Object();
    private java.lang.Process mOwnedCore;
    private ProcessIdentity mOwnedIdentity;
    private ProcessIdentityStore mIdentityStore;
    private boolean mCaptureStdout;
    private boolean mWaitInProgress;

    private final ISyncthingSuperuserService.Stub mBinder =
            new ISyncthingSuperuserService.Stub() {
                @Override
                public SuperuserUidResult verifySuperuser() {
                    enforcePrivilegedCaller();
                    return new SuperuserUidResult(SuperuserErrorCode.OK.wireCode(), "",
                            android.os.Process.myUid());
                }

                @Override
                public SuperuserOperationResult recoverOrphanedCore() {
                    enforcePrivilegedCaller();
                    return recoverOrphanedCoreInternal();
                }

                @Override
                public SuperuserOperationResult startCore(int commandId, Bundle environment,
                                                          boolean captureStdout) {
                    enforcePrivilegedCaller();
                    return startOwnedCore(commandId, environment, captureStdout);
                }

                @Override
                public SuperuserCoreExitResult waitForCoreExit() {
                    enforcePrivilegedCaller();
                    return awaitOwnedCore();
                }

                @Override
                public SuperuserOperationResult stopOwnedCore() {
                    enforcePrivilegedCaller();
                    return stopOwnedCoreProcess();
                }

                @Override
                public SuperuserCoreStatus getCoreStatus() {
                    enforcePrivilegedCaller();
                    return ownedCoreStatus();
                }

                @Override
                public SuperuserBooleanResult testFolderWritable(String absolutePath) {
                    enforcePrivilegedCaller();
                    return testFolderWritableInternal(absolutePath);
                }

                @Override
                public SuperuserStringListResult findSyncConflicts(
                        String absoluteConfiguredFolderPath) {
                    enforcePrivilegedCaller();
                    return findSyncConflictsInternal(absoluteConfiguredFolderPath);
                }

                @Override
                public SuperuserStateFileResult openStateFileForRead(int stateFileId) {
                    enforcePrivilegedCaller();
                    return openStateFile(stateFileId);
                }

                @Override
                public SuperuserOperationResult writeStateFileAtomic(int stateFileId,
                                                                      ParcelFileDescriptor source) {
                    enforcePrivilegedCaller();
                    return writeStateFile(stateFileId, source);
                }

                @Override
                public SuperuserOperationResult deleteStateFile(int stateFileId) {
                    enforcePrivilegedCaller();
                    return deleteStateFile(stateFileId);
                }

                @Override
                public SuperuserOperationResult stageBackupState(String transferId, int appUid,
                                                                  int appGid) {
                    enforcePrivilegedCaller();
                    return stageBackupStateInternal(transferId, appUid, appGid);
                }

                @Override
                public SuperuserOperationResult installBackupState(String transferId) {
                    enforcePrivilegedCaller();
                    return installBackupStateInternal(transferId);
                }

                @Override
                public SuperuserOperationResult repairAppPrivateState(int appUid, int appGid) {
                    enforcePrivilegedCaller();
                    return repairAppPrivateStateInternal(appUid, appGid);
                }
            };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void enforceAppCaller() {
        if (Binder.getCallingUid() != getApplicationInfo().uid) {
            throw new SecurityException("Only the owning application may call the root service");
        }
    }

    private void enforcePrivilegedCaller() {
        enforceAppCaller();
        if (android.os.Process.myUid() != android.os.Process.ROOT_UID) {
            throw new SecurityException("Syncthing root service must run as UID 0");
        }
    }

    private SuperuserOperationResult startOwnedCore(int commandId, Bundle environment,
                                                    boolean captureStdout) {
        final SyncthingCommand command;
        try {
            command = SyncthingCommand.fromWireId(commandId);
        } catch (IllegalArgumentException exception) {
            return invalidRequest("Unknown Syncthing command");
        }
        if (environment == null) {
            return invalidRequest("Syncthing environment is missing");
        }

        File logFile = Constants.getSyncthingLogFile(this);
        if (!logFile.isFile()) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.CORE_LAUNCH_FAILED,
                    "Syncthing log file is not prepared by the app process");
        }

        Map<String, String> requestedEnvironment = new HashMap<>();
        for (String key : environment.keySet()) {
            Object value = environment.get(key);
            if (!(value instanceof String)) {
                return invalidRequest("Syncthing environment contains an invalid entry");
            }
            requestedEnvironment.put(key, (String) value);
        }

        final Map<String, String> safeEnvironment;
        try {
            safeEnvironment = sanitizeEnvironment(requestedEnvironment,
                    FileUtils.getSyncthingTildeAbsolutePath(), getFilesDir().getAbsolutePath(),
                    getCacheDir().getAbsolutePath(), getString(com.nutomic.syncthingandroid.R.string.app_name));
        } catch (IllegalArgumentException exception) {
            return invalidRequest("Syncthing environment contains an invalid entry");
        }

        ProcessIdentityStore identityStore = getIdentityStore();
        synchronized (mCoreLock) {
            if (mOwnedCore != null) {
                return invalidRequest("A Syncthing process is already owned");
            }
            if (!identityStore.writeLaunchPending()) {
                return SuperuserOperationResult.failure(SuperuserErrorCode.CORE_LAUNCH_FAILED,
                        "Root process identity record could not be prepared");
            }

            ProcessBuilder builder;
            try {
                builder = new ProcessBuilder(command.argv(Constants.getSyncthingBinary(this)));
                builder.environment().clear();
                builder.environment().putAll(safeEnvironment);
                mOwnedCore = builder.start();
                mOwnedIdentity = null;
                mCaptureStdout = captureStdout;
                mWaitInProgress = false;
            } catch (IOException | RuntimeException exception) {
                identityStore.clear();
                return SuperuserOperationResult.failure(SuperuserErrorCode.CORE_LAUNCH_FAILED,
                        "Failed to start Syncthing as superuser");
            }
        }

        ProcessIdentity identity = resolveDirectChild(
                Constants.getSyncthingBinary(this), mOwnedCore);
        if (identity != null && identityStore.writeRunning(identity)) {
            synchronized (mCoreLock) {
                mOwnedIdentity = identity;
            }
            return SuperuserOperationResult.success();
        }

        java.lang.Process process;
        synchronized (mCoreLock) {
            process = mOwnedCore;
        }
        if (process != null && !isRunning(process)) {
            boolean cleared = identityStore.clear();
            synchronized (mCoreLock) {
                if (mOwnedCore == process) {
                    mOwnedCore = null;
                    mOwnedIdentity = null;
                    mCaptureStdout = false;
                    mWaitInProgress = false;
                }
            }
            return cleared
                    ? SuperuserOperationResult.success()
                    : SuperuserOperationResult.failure(SuperuserErrorCode.CORE_LAUNCH_FAILED,
                    "Root process identity record could not be cleared");
        }

        if (process != null) {
            boolean stopped = false;
            try {
                process.destroy();
                stopped = waitForProcessExit(process, ORPHAN_GRACEFUL_WAIT_MS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException ignored) {
                // The typed launch failure below is the public result.
            }
            if (!stopped) {
                return SuperuserOperationResult.failure(SuperuserErrorCode.CORE_LAUNCH_FAILED,
                        "Unable to stop the unverified launched Syncthing process");
            }
        }
        boolean cleared = identityStore.clear();
        synchronized (mCoreLock) {
            if (mOwnedCore == process) {
                mOwnedCore = null;
                mOwnedIdentity = null;
                mCaptureStdout = false;
                mWaitInProgress = false;
            }
        }
        return SuperuserOperationResult.failure(SuperuserErrorCode.CORE_LAUNCH_FAILED,
                cleared ? "Unable to verify the launched Syncthing process"
                        : "Unable to verify or clear the launched Syncthing process");
    }

    private SuperuserCoreExitResult awaitOwnedCore() {
        final java.lang.Process process;
        final boolean captureStdout;
        synchronized (mCoreLock) {
            if (mOwnedCore == null) {
                return new SuperuserCoreExitResult(SuperuserErrorCode.INVALID_REQUEST.wireCode(),
                        "No owned Syncthing process is running", -1, "");
            }
            if (mWaitInProgress) {
                return new SuperuserCoreExitResult(SuperuserErrorCode.INVALID_REQUEST.wireCode(),
                        "Syncthing process output is already being collected", -1, "");
            }
            process = mOwnedCore;
            captureStdout = mCaptureStdout;
            mWaitInProgress = true;
        }

        try {
            SyncthingExecutionResult result = SyncthingProcessOutput.await(
                    process, Constants.getSyncthingLogFile(this), captureStdout);
            if (!getIdentityStore().clear()) {
                return new SuperuserCoreExitResult(
                        SuperuserErrorCode.STATE_ACCESS_FAILED.wireCode(),
                        "Root process identity record could not be cleared",
                        result.exitCode(), result.stdout());
            }
            return new SuperuserCoreExitResult(SuperuserErrorCode.OK.wireCode(), "",
                    result.exitCode(), result.stdout());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new SuperuserCoreExitResult(SuperuserErrorCode.CORE_LAUNCH_FAILED.wireCode(),
                    "Waiting for Syncthing was interrupted", -1, "");
        } finally {
            synchronized (mCoreLock) {
                if (mOwnedCore == process) {
                    mOwnedCore = null;
                    mOwnedIdentity = null;
                    mCaptureStdout = false;
                    mWaitInProgress = false;
                }
            }
        }
    }

    private SuperuserOperationResult stopOwnedCoreProcess() {
        java.lang.Process process;
        synchronized (mCoreLock) {
            process = mOwnedCore;
        }
        if (process == null) {
            return SuperuserOperationResult.success();
        }
        try {
            process.destroy();
            boolean stopped = waitForProcessExit(process, ORPHAN_GRACEFUL_WAIT_MS);
            if (!stopped) {
                ProcessIdentity identity;
                synchronized (mCoreLock) {
                    identity = mOwnedIdentity;
                }
                if (identity == null
                        || new ProcfsIdentityProbe().probe(identity)
                        != OrphanRecoveryPolicy.ProbeState.MATCH) {
                    return SuperuserOperationResult.failure(SuperuserErrorCode.CORE_STOP_FAILED,
                            "The owned Syncthing process identity could not be verified");
                }
                Os.kill(identity.pid(), OrphanRecoveryPolicy.SIGKILL);
                stopped = waitForProcessExit(process, ORPHAN_GRACEFUL_WAIT_MS);
            }
            if (!stopped) {
                return SuperuserOperationResult.failure(SuperuserErrorCode.CORE_STOP_FAILED,
                        "The owned Syncthing process did not exit");
            }
            if (!getIdentityStore().clear()) {
                return SuperuserOperationResult.failure(SuperuserErrorCode.STATE_ACCESS_FAILED,
                        "Root process identity record could not be cleared");
            }
            synchronized (mCoreLock) {
                if (mOwnedCore == process) {
                    mOwnedCore = null;
                    mOwnedIdentity = null;
                    mCaptureStdout = false;
                    mWaitInProgress = false;
                }
            }
            return SuperuserOperationResult.success();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return SuperuserOperationResult.failure(SuperuserErrorCode.CORE_STOP_FAILED,
                    "Stopping the owned Syncthing process was interrupted");
        } catch (ErrnoException exception) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.CORE_STOP_FAILED,
                    "The owned Syncthing process could not be force-stopped");
        } catch (RuntimeException exception) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.CORE_STOP_FAILED,
                    "Failed to stop the owned Syncthing process");
        }
    }

    private SuperuserCoreStatus ownedCoreStatus() {
        java.lang.Process process;
        synchronized (mCoreLock) {
            process = mOwnedCore;
            if (process != null && mOwnedIdentity != null) {
                int pid = mOwnedIdentity.pid();
                boolean running = isRunning(process);
                return new SuperuserCoreStatus(SuperuserErrorCode.OK.wireCode(), "", running,
                        running ? pid : -1);
            }
        }
        boolean running = process != null && isRunning(process);
        return new SuperuserCoreStatus(SuperuserErrorCode.OK.wireCode(), "", running, -1);
    }

    private SuperuserOperationResult recoverOrphanedCoreInternal() {
        java.lang.Process ownedProcess;
        synchronized (mCoreLock) {
            ownedProcess = mOwnedCore;
            if (ownedProcess != null && isRunning(ownedProcess)) {
                return SuperuserOperationResult.success();
            }
        }

        if (ownedProcess != null) {
            SuperuserCoreExitResult exitResult = awaitOwnedCore();
            return exitResult.isSuccess()
                    ? SuperuserOperationResult.success()
                    : SuperuserOperationResult.failure(SuperuserErrorCode.ORPHAN_RECOVERY_FAILED,
                    "Owned Syncthing process could not be cleaned up");
        }

        ProcessIdentityStore identityStore = getIdentityStore();
        OrphanRecoveryPolicy.Result result = OrphanRecoveryPolicy.recover(
                identityStore.read(), identityStore, new ProcfsIdentityProbe(),
                new OsProcessSignaler(), Thread::sleep,
                ORPHAN_GRACEFUL_WAIT_MS, ORPHAN_POLL_INTERVAL_MS);
        return result.operation();
    }

    private ProcessIdentityStore getIdentityStore() {
        synchronized (mCoreLock) {
            if (mIdentityStore == null) {
                mIdentityStore = new ProcessIdentityStore(this);
            }
            return mIdentityStore;
        }
    }

    private ProcessIdentity resolveDirectChild(File expectedExecutable,
                                               java.lang.Process process) {
        String expectedPath = expectedExecutable.getPath();
        long deadline = System.nanoTime()
                + CHILD_RESOLUTION_TIMEOUT_MS * 1_000_000L;
        while (isRunning(process)) {
            File procDirectory = new File("/proc");
            File[] entries = procDirectory.listFiles();
            ProcessIdentity match = null;
            int matchCount = 0;
            if (entries != null) {
                for (File entry : entries) {
                    if (!entry.isDirectory() || !isNumeric(entry.getName())) {
                        continue;
                    }
                    ProcessIdentity candidate = readDirectChildIdentity(entry, expectedPath);
                    if (candidate != null) {
                        match = candidate;
                        matchCount++;
                    }
                }
            }
            if (matchCount == 1) {
                return match;
            }
            if (matchCount > 1 || System.nanoTime() >= deadline) {
                return null;
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static ProcessIdentity readDirectChildIdentity(File procDirectory,
                                                           String expectedExecutable) {
        try {
            String stat = readFirstLine(new File(procDirectory, "stat"));
            ProcessIdentityStore.ProcStat procStat = ProcessIdentityStore.parseProcStat(stat);
            if (procStat.parentPid() != android.os.Process.myPid()) {
                return null;
            }
            String executable = Os.readlink(new File(procDirectory, "exe").getPath());
            if (!expectedExecutable.equals(executable)) {
                return null;
            }
            return new ProcessIdentity(Integer.parseInt(procDirectory.getName()),
                    procStat.startTimeTicks(), executable);
        } catch (IOException | ErrnoException | IllegalArgumentException | SecurityException
                 exception) {
            return null;
        }
    }

    private static String readFirstLine(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            return reader.readLine();
        }
    }

    private static boolean isNumeric(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private final class ProcfsIdentityProbe implements OrphanRecoveryPolicy.ProcessIdentityProbe {
        @Override
        public OrphanRecoveryPolicy.ProbeState probe(ProcessIdentity expected) {
            File procDirectory = new File("/proc", Integer.toString(expected.pid()));
            if (!procDirectory.isDirectory()) {
                return OrphanRecoveryPolicy.ProbeState.ABSENT;
            }
            try {
                ProcessIdentityStore.ProcStat procStat = ProcessIdentityStore.parseProcStat(
                        readFirstLine(new File(procDirectory, "stat")));
                String executable = Os.readlink(new File(procDirectory, "exe").getPath());
                ProcessIdentity actual = new ProcessIdentity(expected.pid(),
                        procStat.startTimeTicks(), executable);
                return actual.equals(expected)
                        ? OrphanRecoveryPolicy.ProbeState.MATCH
                        : OrphanRecoveryPolicy.ProbeState.MISMATCH;
            } catch (IOException | ErrnoException | IllegalArgumentException | SecurityException
                     exception) {
                return OrphanRecoveryPolicy.ProbeState.UNKNOWN;
            }
        }
    }

    private static final class OsProcessSignaler implements OrphanRecoveryPolicy.ProcessSignaler {
        @Override
        public void signal(ProcessIdentity identity, int signal) throws ErrnoException {
            Os.kill(identity.pid(), signal);
        }
    }

    private static boolean validEnvironmentKey(String key) {
        return key != null && !key.isEmpty() && key.indexOf('=') < 0
                && key.indexOf('\0') < 0;
    }

    private static boolean isRunning(java.lang.Process process) {
        try {
            process.exitValue();
            return false;
        } catch (IllegalThreadStateException exception) {
            return true;
        }
    }

    private static boolean waitForProcessExit(java.lang.Process process, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (isRunning(process)) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                return false;
            }
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            Thread.sleep(Math.min(PROCESS_WAIT_POLL_INTERVAL_MS, Math.max(1L, remainingMs)));
        }
        return true;
    }

    private static SuperuserOperationResult invalidRequest(String diagnostic) {
        return SuperuserOperationResult.failure(SuperuserErrorCode.INVALID_REQUEST, diagnostic);
    }

    private SuperuserStateFileResult openStateFile(int stateFileId) {
        final File stateFile;
        try {
            stateFile = SyncthingStateFile.fromWireId(stateFileId).resolve(getFilesDir());
        } catch (IllegalArgumentException exception) {
            return new SuperuserStateFileResult(SuperuserErrorCode.INVALID_REQUEST.wireCode(),
                    "Unknown Syncthing state file", null);
        }
        try {
            try (SecureFileAccess.OpenedFile opened = FILE_ACCESS.openExistingRegular(
                    stateFile, android.system.OsConstants.O_RDONLY)) {
                return new SuperuserStateFileResult(SuperuserErrorCode.OK.wireCode(), "",
                        opened.duplicate());
            }
        } catch (SecureFileAccess.AccessException exception) {
            if (exception.errno() == android.system.OsConstants.ENOENT) {
                return new SuperuserStateFileResult(SuperuserErrorCode.OK.wireCode(), "", null);
            }
            return new SuperuserStateFileResult(SuperuserErrorCode.STATE_ACCESS_FAILED.wireCode(),
                    "Failed to open Syncthing state", null);
        } catch (IOException | SecurityException exception) {
            return new SuperuserStateFileResult(SuperuserErrorCode.STATE_ACCESS_FAILED.wireCode(),
                    "Failed to open Syncthing state", null);
        }
    }

    private SuperuserOperationResult writeStateFile(int stateFileId,
                                                     ParcelFileDescriptor source) {
        if (source == null) {
            return invalidRequest("Syncthing state source is missing");
        }
        final SyncthingStateFile stateFile;
        try {
            stateFile = SyncthingStateFile.fromWireId(stateFileId);
        } catch (IllegalArgumentException exception) {
            return invalidRequest("Unknown Syncthing state file");
        }

        File target = stateFile.resolve(getFilesDir());
        File temporary = new File(getFilesDir(), stateFile.fileName() + ".superuser.tmp");
        boolean temporaryCreated = false;
        try (ParcelFileDescriptor.AutoCloseInputStream input =
                     new ParcelFileDescriptor.AutoCloseInputStream(source);
             SecureFileAccess.OpenedFile temporaryFile = FILE_ACCESS.createNewRegular(temporary)) {
            temporaryCreated = true;
            FileOutputStream output = new FileOutputStream(temporaryFile.descriptor());
            byte[] buffer = new byte[8192];
            int count;
            long total = 0;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > 16L * 1024L * 1024L) {
                    return SuperuserOperationResult.failure(SuperuserErrorCode.STATE_ACCESS_FAILED,
                            "Syncthing state is too large");
                }
                output.write(buffer, 0, count);
            }
            output.flush();
            temporaryFile.sync();
            if (!temporary.renameTo(target)) {
                return SuperuserOperationResult.failure(SuperuserErrorCode.STATE_ACCESS_FAILED,
                        "Failed to install Syncthing state");
            }
            return SuperuserOperationResult.success();
        } catch (IOException | SecurityException exception) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.STATE_ACCESS_FAILED,
                    "Failed to write Syncthing state");
        } finally {
            if (temporaryCreated && temporary.exists() && !temporary.delete()) {
                // The temporary file is confined to the fixed app-private directory.
            }
        }
    }

    private SuperuserOperationResult deleteStateFile(int stateFileId) {
        final File stateFile;
        try {
            stateFile = SyncthingStateFile.fromWireId(stateFileId).resolve(getFilesDir());
        } catch (IllegalArgumentException exception) {
            return invalidRequest("Unknown Syncthing state file");
        }
        try {
            return !stateFile.exists() || stateFile.delete()
                    ? SuperuserOperationResult.success()
                    : SuperuserOperationResult.failure(SuperuserErrorCode.STATE_ACCESS_FAILED,
                    "Failed to delete Syncthing state");
        } catch (SecurityException exception) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.STATE_ACCESS_FAILED,
                    "Failed to delete Syncthing state");
        }
    }

    private SuperuserOperationResult stageBackupStateInternal(String transferId, int appUid,
                                                               int appGid) {
        int expectedAppUid = getApplicationInfo().uid;
        if (!NormalSyncthingStateTransfer.isValidTransferId(transferId)
                || appUid <= 0 || appGid <= 0
                || appUid != expectedAppUid || appGid != expectedAppUid) {
            return invalidRequest("Invalid Syncthing state transfer request");
        }
        synchronized (mCoreLock) {
            if (mOwnedCore != null) {
                return SuperuserOperationResult.failure(SuperuserErrorCode.CORE_STOP_FAILED,
                        "Syncthing must be stopped before state staging");
            }
        }

        SuperuserOperationResult recovery = recoverOrphanedCoreInternal();
        if (!recovery.isSuccess()) {
            return recovery;
        }

        return stageBackupStateSnapshot(transferId, appUid, appGid, expectedAppUid,
                getFilesDir(), getCacheDir(), STATE_TRANSFER_OPERATIONS);
    }

    /**
     * Creates a root-owned transfer directory and copies the closed backup snapshot into it.
     * The operation seam keeps the security-sensitive sequence deterministic in JVM tests while
     * the production implementation remains a direct file operation with no generic filesystem
     * abstraction.
     */
    static SuperuserOperationResult stageBackupStateSnapshot(
            String transferId, int appUid, int appGid, int expectedAppUid,
            File filesDir, File cacheDir, SuperuserStateTransferOperations operations) {
        if (!NormalSyncthingStateTransfer.isValidTransferId(transferId)
                || appUid <= 0 || appGid <= 0
                || appUid != expectedAppUid || appGid != expectedAppUid
                || filesDir == null || cacheDir == null || operations == null) {
            return invalidRequest("Invalid Syncthing state transfer request");
        }

        File base = new File(cacheDir, NormalSyncthingStateTransfer.STAGING_DIRECTORY);
        File staging = new File(base, transferId);
        boolean createdByThisInvocation = false;
        try {
            if (NormalSyncthingStateTransfer.isSymbolicLink(filesDir)
                    || !filesDir.isDirectory()) {
                throw new IOException("App-private state directory is unavailable");
            }
            NormalSyncthingStateTransfer.ensureStagingBase(cacheDir, base);
            if (NormalSyncthingStateTransfer.isSymbolicLink(staging) || staging.exists()) {
                return invalidRequest("State staging directory already exists");
            }
            if (!staging.mkdir()) {
                if (NormalSyncthingStateTransfer.isSymbolicLink(staging) || staging.exists()) {
                    return invalidRequest("State staging directory already exists");
                }
                throw new IOException("Unable to create state staging directory");
            }
            createdByThisInvocation = true;
            NormalSyncthingStateTransfer.validateStagingDirectory(base, staging);

            for (String name : NormalSyncthingStateTransfer.snapshotNames()) {
                File source = new File(filesDir, name);
                if (NormalSyncthingStateTransfer.isSymbolicLink(source)) {
                    throw new IOException("State source symlinks are not allowed");
                }
                if (!source.exists()) {
                    continue;
                }
                File target = new File(staging, name);
                if (NormalSyncthingStateTransfer.isSymbolicLink(target) || target.exists()) {
                    throw new IOException("State staging entry already exists");
                }
                operations.copyTree(source, target, filesDir);
            }

            List<File> completeTree = collectSafeTree(staging, base);
            for (File entry : completeTree) {
                operations.chown(entry, expectedAppUid, expectedAppUid);
            }
            for (File entry : completeTree) {
                operations.restoreContext(entry);
            }
            NormalSyncthingStateTransfer.ensureSafeTree(staging, base);
            return SuperuserOperationResult.success();
        } catch (IOException | SecurityException | ErrnoException | InterruptedException
                 | SyncthingStateAccessException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (createdByThisInvocation) {
                deleteCreatedStaging(staging, base, operations);
            }
            return SuperuserOperationResult.failure(SuperuserErrorCode.STATE_TRANSFER_FAILED,
                    "Failed to stage Syncthing state");
        }
    }

    private static List<File> collectSafeTree(File root, File base) throws IOException {
        NormalSyncthingStateTransfer.ensureSafeTree(root, base);
        List<File> entries = new ArrayList<>();
        collectSafeTreeEntries(root, entries);
        return entries;
    }

    private static void collectSafeTreeEntries(File file, List<File> entries) throws IOException {
        entries.add(file);
        if (!file.isDirectory()) {
            return;
        }
        File[] children = file.listFiles();
        if (children == null) {
            throw new IOException("Unable to inspect state staging entry");
        }
        for (File child : children) {
            NormalSyncthingStateTransfer.ensureSafeTree(child, file);
            collectSafeTreeEntries(child, entries);
        }
    }

    private static void deleteCreatedStaging(File staging, File base,
                                             SuperuserStateTransferOperations operations) {
        try {
            NormalSyncthingStateTransfer.validateStagingDirectory(base, staging);
            operations.deleteTree(staging);
        } catch (IOException | SyncthingStateAccessException | RuntimeException ignored) {
            // The original operation error is the useful typed result.
        }
    }

    private SuperuserOperationResult installBackupStateInternal(String transferId) {
        if (!NormalSyncthingStateTransfer.isValidTransferId(transferId)) {
            return invalidRequest("Invalid Syncthing state transfer request");
        }
        synchronized (mCoreLock) {
            if (mOwnedCore != null) {
                return SuperuserOperationResult.failure(SuperuserErrorCode.CORE_STOP_FAILED,
                        "Syncthing must be stopped before state installation");
            }
        }

        SuperuserOperationResult recovery = recoverOrphanedCoreInternal();
        if (!recovery.isSuccess()) {
            return recovery;
        }

        File base = NormalSyncthingStateTransfer.stagingBase(this);
        File staging = new File(base, transferId);
        try {
            NormalSyncthingStateTransfer.validateStagingDirectory(base, staging);
            NormalSyncthingStateTransfer.ensureSafeTree(staging, base);
            File[] children = staging.listFiles();
            if (children == null) {
                throw new IOException("Unable to inspect state staging directory");
            }
            for (File child : children) {
                if (!NormalSyncthingStateTransfer.isSnapshotName(child.getName())) {
                    throw new IOException("State staging directory contains an unsupported entry");
                }
                NormalSyncthingStateTransfer.ensureSafeTree(child, staging);
            }
            for (File child : children) {
                File target = new File(getFilesDir(), child.getName());
                if (target.exists()) {
                    NormalSyncthingStateTransfer.ensureSafeTree(target, getFilesDir());
                }
                if (child.isDirectory()) {
                    NormalSyncthingStateTransfer.deleteTree(target);
                    NormalSyncthingStateTransfer.copyTree(child, target, staging);
                } else {
                    NormalSyncthingStateTransfer.copyFileAtomic(child, target);
                }
            }
            NormalSyncthingStateTransfer.deleteTree(staging);
            return SuperuserOperationResult.success();
        } catch (IOException | SecurityException | SyncthingStateAccessException exception) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.STATE_TRANSFER_FAILED,
                    "Failed to install Syncthing state");
        }
    }

    private SuperuserBooleanResult testFolderWritableInternal(String absolutePath) {
        try {
            boolean writable = new NormalSyncthingFolderAccess(this).canWrite(absolutePath);
            return new SuperuserBooleanResult(SuperuserErrorCode.OK.wireCode(), "", writable);
        } catch (SyncthingFolderAccessException exception) {
            return new SuperuserBooleanResult(SuperuserErrorCode.FOLDER_ACCESS_FAILED.wireCode(),
                    "Root folder writeability test failed", false);
        }
    }

    private SuperuserStringListResult findSyncConflictsInternal(String absolutePath) {
        try {
            String[] conflicts = new NormalSyncthingFolderAccess(this)
                    .findSyncConflicts(absolutePath);
            return boundedConflictResult(conflicts);
        } catch (SyncthingFolderAccessException exception) {
            return new SuperuserStringListResult(
                    SuperuserErrorCode.FOLDER_ACCESS_FAILED.wireCode(),
                    "Root conflict discovery failed", new String[0]);
        }
    }

    /** Returns a Binder-safe conflict result and explicitly reports omitted entries. */
    static SuperuserStringListResult boundedConflictResult(String[] conflicts) {
        if (conflicts == null) {
            return new SuperuserStringListResult(
                    SuperuserErrorCode.FOLDER_ACCESS_FAILED.wireCode(),
                    "Root conflict discovery returned no result", new String[0]);
        }

        List<String> bounded = new ArrayList<>();
        long serializedBytes = 4L;
        boolean truncated = false;
        for (String conflict : conflicts) {
            if (conflict == null) {
                truncated = true;
                break;
            }
            long entryBytes = 4L + (long) conflict.length() * 2L;
            if (bounded.size() >= MAX_CONFLICT_RESULT_ITEMS
                    || serializedBytes + entryBytes > MAX_CONFLICT_RESULT_BYTES) {
                truncated = true;
                break;
            }
            bounded.add(conflict);
            serializedBytes += entryBytes;
        }
        return new SuperuserStringListResult(SuperuserErrorCode.OK.wireCode(), "",
                bounded.toArray(new String[0]), truncated);
    }

    /** Builds the only environment that the UID-0 process is allowed to inherit. */
    static Map<String, String> sanitizeEnvironment(Map<String, String> requested,
                                                   String home, String stateHome,
                                                   String sqliteTempDir, String versionExtra) {
        if (requested == null || home == null || stateHome == null || sqliteTempDir == null
                || versionExtra == null) {
            throw new IllegalArgumentException("Syncthing environment values must not be null");
        }

        Map<String, String> safe = new HashMap<>();
        safe.put("HOME", home);
        safe.put("STHOMEDIR", stateHome);
        safe.put("SQLITE_TMPDIR", sqliteTempDir);
        safe.put("STMONITORED", "1");
        safe.put("STNOUPGRADE", "1");
        safe.put("STVERSIONEXTRA", versionExtra);

        for (Map.Entry<String, String> entry : requested.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!validEnvironmentKey(key) || !ACCEPTED_ENVIRONMENT_KEYS.contains(key)
                    || value == null || value.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Syncthing environment contains an invalid entry");
            }
            if (!SERVER_CONTROLLED_ENVIRONMENT_KEYS.contains(key)) {
                safe.put(key, value);
            }
        }
        return safe;
    }

    private SuperuserOperationResult repairAppPrivateStateInternal(int appUid, int appGid) {
        int expectedAppUid = getApplicationInfo().uid;
        if (appUid <= 0 || appGid <= 0
                || appUid != expectedAppUid || appGid != expectedAppUid) {
            return invalidRequest("Invalid app ownership repair request");
        }
        synchronized (mCoreLock) {
            if (mOwnedCore != null) {
                return SuperuserOperationResult.failure(SuperuserErrorCode.CORE_STOP_FAILED,
                        "Syncthing must be stopped before ownership repair");
            }
        }

        List<String> changedPaths = new ArrayList<>();
        try {
            repairTree(getFilesDir(), expectedAppUid, expectedAppUid, changedPaths);
        } catch (IOException | ErrnoException exception) {
            return SuperuserOperationResult.failure(SuperuserErrorCode.OWNERSHIP_REPAIR_FAILED,
                    "Unable to repair app-private state ownership");
        }

        try {
            restoreSelinuxContexts(changedPaths);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return SuperuserOperationResult.failure(SuperuserErrorCode.SELINUX_RESTORE_FAILED,
                    "Unable to restore app-private SELinux contexts");
        }
        return SuperuserOperationResult.success();
    }

    private static void repairTree(File file, int appUid, int appGid,
                                    List<String> changedPaths)
            throws IOException, ErrnoException {
        android.system.StructStat stat = Os.lstat(file.getPath());
        if ((stat.st_mode & android.system.OsConstants.S_IFMT)
                == android.system.OsConstants.S_IFLNK) {
            throw new IOException("Symlinks are not permitted in app-private repair scope");
        }
        if (stat.st_uid == 0 || stat.st_gid == 0) {
            Os.chown(file.getPath(), appUid, appGid);
            changedPaths.add(file.getPath());
        } else if (stat.st_uid != appUid || stat.st_gid != appGid) {
            throw new IOException("App-private state has an unexpected owner");
        }
        if (!file.isDirectory()) {
            return;
        }
        File[] children = file.listFiles();
        if (children == null) {
            throw new IOException("Unable to inspect app-private state");
        }
        for (File child : children) {
            repairTree(child, appUid, appGid, changedPaths);
        }
    }

    private static void restoreSelinuxContexts(List<String> changedPaths)
            throws IOException, InterruptedException {
        for (String path : changedPaths) {
            restoreSelinuxContext(path);
        }
    }

    private static void restoreSelinuxContext(String path)
            throws IOException, InterruptedException {
        Process restorecon = new ProcessBuilder("/system/bin/restorecon", path).start();
        if (!waitForProcessExit(restorecon, 10_000L)) {
            restorecon.destroy();
            if (!waitForProcessExit(restorecon, 1_000L)) {
                throw new IOException("restorecon timed out");
            }
        }
        if (restorecon.exitValue() != 0) {
            throw new IOException("restorecon failed");
        }
    }

    private static SuperuserOperationResult unsupported() {
        return SuperuserOperationResult.failure(SuperuserErrorCode.INVALID_REQUEST,
                "Root capability is not initialized");
    }
}

/** Package-private operations seam for the root-created backup staging tree. */
interface SuperuserStateTransferOperations {
    void copyTree(File source, File target, File sourceRoot) throws IOException;

    void chown(File file, int uid, int gid) throws IOException, ErrnoException;

    void restoreContext(File file) throws IOException, InterruptedException;

    void deleteTree(File file) throws IOException;
}
