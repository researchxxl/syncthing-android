package com.nutomic.syncthingandroid.service.execution;

/** Exit status and bounded captured output from a Syncthing command. */
public record SyncthingExecutionResult(int exitCode, String stdout) {
}
