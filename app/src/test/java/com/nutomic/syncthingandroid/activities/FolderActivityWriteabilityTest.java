package com.nutomic.syncthingandroid.activities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FolderActivityWriteabilityTest {

    @Test
    public void anInFlightReadOnlyProbeDoesNotForceSendOnly() {
        assertFalse(FolderActivity.shouldForceSendOnly(false, true));
    }

    @Test
    public void aCompletedReadOnlyProbeForcesSendOnly() {
        assertTrue(FolderActivity.shouldForceSendOnly(false, false));
    }
}
