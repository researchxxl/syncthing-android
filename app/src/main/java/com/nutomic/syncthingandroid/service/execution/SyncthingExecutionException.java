package com.nutomic.syncthingandroid.service.execution;

/** Checked failure raised by an execution backend without permitting a mode fallback. */
public class SyncthingExecutionException extends Exception {

    public SyncthingExecutionException(String message) {
        super(message);
    }

    public SyncthingExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
