package com.nutomic.syncthingandroid.service.execution;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Executes Syncthing directly under the normal application UID. */
public class NormalSyncthingExecutionBackend implements SyncthingExecutionBackend {

    /** Injectable process creation seam used by JVM tests. */
    @FunctionalInterface
    interface ProcessFactory {
        Process start(String[] argv, Map<String, String> environment) throws IOException;
    }

    private final File mBinary;
    private final File mLogFile;
    private final ProcessFactory mProcessFactory;
    private final AtomicReference<Process> mOwnedProcess = new AtomicReference<>();

    public NormalSyncthingExecutionBackend(File binary, File logFile) {
        this(binary, logFile, (argv, environment) -> {
            ProcessBuilder processBuilder = new ProcessBuilder(argv);
            processBuilder.environment().putAll(environment);
            return processBuilder.start();
        });
    }

    NormalSyncthingExecutionBackend(File binary, File logFile, ProcessFactory processFactory) {
        mBinary = binary;
        mLogFile = logFile;
        mProcessFactory = processFactory;
    }

    @Override
    public SyncthingExecutionResult execute(SyncthingCommand command,
                                            Map<String, String> environment,
                                            boolean captureStdout)
            throws SyncthingExecutionException, InterruptedException {
        if (command == null) {
            throw new SyncthingExecutionException("Syncthing command must not be null");
        }
        if (mOwnedProcess.get() != null) {
            throw new SyncthingExecutionException("A Syncthing process is already owned");
        }

        final Process process;
        try {
            process = mProcessFactory.start(command.argv(mBinary), environment);
        } catch (IOException | RuntimeException e) {
            throw new SyncthingExecutionException("Failed to start Syncthing", e);
        }
        if (process == null || !mOwnedProcess.compareAndSet(null, process)) {
            if (process != null) {
                process.destroy();
            }
            throw new SyncthingExecutionException("Failed to own Syncthing process");
        }

        try {
            return SyncthingProcessOutput.await(process, mLogFile, captureStdout);
        } finally {
            mOwnedProcess.compareAndSet(process, null);
        }
    }

    @Override
    public void stopOwnedProcess() throws SyncthingExecutionException {
        Process process = mOwnedProcess.get();
        if (process == null) {
            return;
        }
        try {
            process.destroy();
        } catch (RuntimeException e) {
            throw new SyncthingExecutionException("Failed to stop owned Syncthing process", e);
        }
    }
}
