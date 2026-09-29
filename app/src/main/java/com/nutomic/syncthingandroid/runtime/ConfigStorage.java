package com.nutomic.syncthingandroid.runtime;

import java.io.IOException;

/**
 * Semantic storage for the Syncthing configuration document.
 *
 * <p>This interface deliberately exposes configuration state only. It is not a general-purpose
 * filesystem API.</p>
 */
public interface ConfigStorage {
    boolean canRead();

    byte[] load() throws IOException;

    boolean canWrite();

    void save(byte[] contents) throws IOException;
}
