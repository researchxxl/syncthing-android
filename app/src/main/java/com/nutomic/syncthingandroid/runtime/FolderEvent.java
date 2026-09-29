package com.nutomic.syncthingandroid.runtime;

/** Events that may dispatch a configured-folder script set. */
public enum FolderEvent {
    SYNC_COMPLETE("sync_complete");

    private final String argument;

    FolderEvent(String argument) {
        this.argument = argument;
    }

    String argument() {
        return argument;
    }
}
