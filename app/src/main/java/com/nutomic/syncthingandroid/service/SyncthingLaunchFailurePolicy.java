package com.nutomic.syncthingandroid.service;

/**
 * Selects the terminal service state after a Syncthing launch attempt fails.
 *
 * <p>A launch failure while the service is starting is a persistent service error. Other states
 * are preserved so this policy does not take ownership of unrelated lifecycle transitions.</p>
 */
final class SyncthingLaunchFailurePolicy {
    private SyncthingLaunchFailurePolicy() {
    }

    static SyncthingService.State terminalState(SyncthingService.State currentState) {
        if (currentState == SyncthingService.State.STARTING) {
            return SyncthingService.State.ERROR;
        }
        return currentState;
    }
}
