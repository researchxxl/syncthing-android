package com.nutomic.syncthingandroid.service.state;

/** Checked failure for app-private Syncthing state operations. */
public class SyncthingStateAccessException extends Exception {

    public SyncthingStateAccessException(String message) {
        super(message);
    }

    public SyncthingStateAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
