package com.nutomic.syncthingandroid.service.state;

/** Mode-neutral access to the fixed app-private Syncthing state files. */
public interface SyncthingStateAccess {

    byte[] read(SyncthingStateFile file) throws SyncthingStateAccessException;

    boolean exists(SyncthingStateFile file) throws SyncthingStateAccessException;

    void writeAtomic(SyncthingStateFile file, byte[] data) throws SyncthingStateAccessException;

    void delete(SyncthingStateFile file) throws SyncthingStateAccessException;

    void verifyNormalAccess() throws SyncthingStateAccessException;
}
