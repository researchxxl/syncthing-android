package com.nutomic.syncthingandroid.service.execution;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Test;

public class SyncthingCommandTest {

    @Test
    public void commandWireIdsAreStable() {
        assertEquals(1, SyncthingCommand.DEVICE_ID.wireId());
        assertEquals(2, SyncthingCommand.GENERATE.wireId());
        assertEquals(3, SyncthingCommand.MAIN.wireId());
        assertEquals(4, SyncthingCommand.RESET_DATABASE.wireId());
        assertEquals(5, SyncthingCommand.RESET_DELTAS.wireId());
    }

    @Test
    public void commandsBuildOnlyApprovedArguments() {
        File binary = new File("/bin/syncthing");

        assertArrayEquals(
                new String[]{"/bin/syncthing", "device-id"},
                SyncthingCommand.DEVICE_ID.argv(binary));
        assertArrayEquals(
                new String[]{"/bin/syncthing", "generate"},
                SyncthingCommand.GENERATE.argv(binary));
        assertArrayEquals(
                new String[]{"/bin/syncthing", "serve", "--no-browser"},
                SyncthingCommand.MAIN.argv(binary));
        assertArrayEquals(
                new String[]{"/bin/syncthing", "debug", "reset-database"},
                SyncthingCommand.RESET_DATABASE.argv(binary));
        assertArrayEquals(
                new String[]{"/bin/syncthing", "serve", "--debug-reset-delta-idxs"},
                SyncthingCommand.RESET_DELTAS.argv(binary));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownWireIdIsRejected() {
        SyncthingCommand.fromWireId(99);
    }
}
