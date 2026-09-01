package com.nutomic.syncthingandroid.service.folder;

/** Checked failure for a configured-folder capability operation. */
public class SyncthingFolderAccessException extends Exception {

    public SyncthingFolderAccessException(String message) {
        super(message);
    }

    public SyncthingFolderAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
