package com.nutomic.syncthingandroid.service.execution;

import java.util.Map;

/**
 * Executes one of the closed Syncthing command modes and stops only the process it owns.
 */
public interface SyncthingExecutionBackend {

    /**
     * Executes a command with the supplied environment.
     *
     * @param command closed Syncthing command vocabulary entry
     * @param environment string environment values for the core process
     * @param captureStdout whether bounded standard output should be returned
     */
    SyncthingExecutionResult execute(
            SyncthingCommand command,
            Map<String, String> environment,
            boolean captureStdout
    ) throws SyncthingExecutionException, InterruptedException;

    /** Stops the currently owned process, if one exists. */
    void stopOwnedProcess() throws SyncthingExecutionException;
}
