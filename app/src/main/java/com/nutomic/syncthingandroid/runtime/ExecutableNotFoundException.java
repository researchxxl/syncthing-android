package com.nutomic.syncthingandroid.runtime;

/** Raised when the bundled Syncthing executable is absent. */
public class ExecutableNotFoundException extends Exception {
    public ExecutableNotFoundException(String message) {
        super(message);
    }
}
