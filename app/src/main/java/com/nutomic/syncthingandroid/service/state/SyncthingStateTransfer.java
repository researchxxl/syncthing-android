package com.nutomic.syncthingandroid.service.state;

import java.io.File;

/** Mode-neutral export/import operations for the fixed Syncthing state snapshot. */
public interface SyncthingStateTransfer {

    File stageBackupState() throws SyncthingStateAccessException;

    void installBackupState(File stagedState) throws SyncthingStateAccessException;
}
