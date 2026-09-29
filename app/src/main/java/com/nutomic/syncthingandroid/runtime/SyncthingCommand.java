package com.nutomic.syncthingandroid.runtime;

import java.util.Arrays;

/**
 * The closed set of bundled Syncthing invocations supported by the application.
 *
 * <p>The command arguments live with the command vocabulary so callers cannot construct an
 * arbitrary executable invocation through the runtime seam.</p>
 */
public enum SyncthingCommand {
    SERVE("serve", "--no-browser"),
    DEVICE_ID("device-id"),
    GENERATE("generate"),
    RESET_DATABASE("debug", "reset-database"),
    RESET_DELTAS("serve", "--debug-reset-delta-idxs");

    private final String[] arguments;

    SyncthingCommand(String... arguments) {
        this.arguments = arguments;
    }

    String[] argv(String binaryPath) {
        String[] command = new String[arguments.length + 1];
        command[0] = binaryPath;
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        return Arrays.copyOf(command, command.length);
    }
}
