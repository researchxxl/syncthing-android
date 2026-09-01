package com.nutomic.syncthingandroid.superuser;

/** States persisted for the one supervised root Syncthing process. */
public enum ProcessIdentityRecordState {
    CLEAR,
    LAUNCH_PENDING,
    RUNNING,
    CORRUPT
}
