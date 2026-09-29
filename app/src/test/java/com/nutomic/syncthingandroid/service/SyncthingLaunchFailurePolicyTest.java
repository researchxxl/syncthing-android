package com.nutomic.syncthingandroid.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SyncthingLaunchFailurePolicyTest {
    @Test
    public void admissionFailureLeavesStartingWithErrorTerminalState() {
        assertEquals(
                SyncthingService.State.ERROR,
                SyncthingLaunchFailurePolicy.terminalState(SyncthingService.State.STARTING)
        );
    }

    @Test
    public void policyDoesNotRewriteOtherServiceStates() {
        assertEquals(
                SyncthingService.State.DISABLED,
                SyncthingLaunchFailurePolicy.terminalState(SyncthingService.State.DISABLED)
        );
    }
}
