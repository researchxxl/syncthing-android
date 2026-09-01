package com.nutomic.syncthingandroid.service.folder;

/** Capability boundary for filesystem operations performed on configured Syncthing folders. */
public interface SyncthingFolderAccess {

    boolean canWrite(String absoluteFolderPath) throws SyncthingFolderAccessException;

    String[] findSyncConflicts(String absoluteConfiguredFolderPath)
            throws SyncthingFolderAccessException;
}
