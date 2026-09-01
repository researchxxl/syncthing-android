package com.nutomic.syncthingandroid.service.state;

import com.nutomic.syncthingandroid.service.Constants;

import java.io.File;

/** Closed IDs for the app-private Syncthing state files exposed to the privileged service. */
public enum SyncthingStateFile {
    CONFIG(1, Constants.CONFIG_FILE),
    HTTPS_CERT(2, Constants.HTTPS_CERT_FILE),
    HTTPS_KEY(3, Constants.HTTPS_KEY_FILE);

    private final int mWireId;
    private final String mFileName;

    SyncthingStateFile(int wireId, String fileName) {
        mWireId = wireId;
        mFileName = fileName;
    }

    /** Returns the stable ID used by the AIDL state-file capabilities. */
    public int wireId() {
        return mWireId;
    }

    /** Returns the fixed app-private filename represented by this ID. */
    public String fileName() {
        return mFileName;
    }

    /** Resolves the fixed state file below the supplied app-private directory. */
    public File resolve(File filesDir) {
        if (filesDir == null) {
            throw new IllegalArgumentException("filesDir must not be null");
        }
        return new File(filesDir, mFileName);
    }

    /** Converts a wire ID without coercing unknown values. */
    public static SyncthingStateFile fromWireId(int wireId) {
        for (SyncthingStateFile file : values()) {
            if (file.mWireId == wireId) {
                return file;
            }
        }
        throw new IllegalArgumentException("Unknown Syncthing state-file ID: " + wireId);
    }
}
