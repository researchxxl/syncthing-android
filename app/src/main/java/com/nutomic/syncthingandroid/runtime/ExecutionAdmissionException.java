package com.nutomic.syncthingandroid.runtime;

/** Raised when a second bundled Syncthing invocation would overlap an active invocation. */
public class ExecutionAdmissionException extends IllegalStateException {
    public ExecutionAdmissionException() {
        super("A bundled Syncthing invocation is already active");
    }
}
