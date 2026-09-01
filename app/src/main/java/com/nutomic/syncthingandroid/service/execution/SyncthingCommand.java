package com.nutomic.syncthingandroid.service.execution;

import java.io.File;

/**
 * The small, reviewable set of Syncthing core commands supported by the wrapper.
 *
 * <p>The wire IDs are part of the privileged Binder contract. Keep them stable when adding or
 * removing commands, and do not add caller-provided executable or argument fields.</p>
 */
public enum SyncthingCommand {
    DEVICE_ID(1, "device-id"),
    GENERATE(2, "generate"),
    MAIN(3, "serve", "--no-browser"),
    RESET_DATABASE(4, "debug", "reset-database"),
    RESET_DELTAS(5, "serve", "--debug-reset-delta-idxs");

    private final int mWireId;
    private final String[] mArguments;

    SyncthingCommand(int wireId, String... arguments) {
        mWireId = wireId;
        mArguments = arguments;
    }

    /** Returns the stable integer used by the privileged Binder contract. */
    public int wireId() {
        return mWireId;
    }

    /** Resolves a stable Binder command ID without accepting unknown values. */
    public static SyncthingCommand fromWireId(int wireId) {
        for (SyncthingCommand command : values()) {
            if (command.mWireId == wireId) {
                return command;
            }
        }
        throw new IllegalArgumentException("Unknown Syncthing command ID: " + wireId);
    }

    /** Builds the command line from the server-selected binary and closed arguments. */
    public String[] argv(File binary) {
        if (binary == null) {
            throw new IllegalArgumentException("Syncthing binary must not be null");
        }
        String[] argv = new String[mArguments.length + 1];
        argv[0] = binary.getPath();
        System.arraycopy(mArguments, 0, argv, 1, mArguments.length);
        return argv;
    }
}
