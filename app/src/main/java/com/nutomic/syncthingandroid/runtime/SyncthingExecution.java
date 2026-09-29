package com.nutomic.syncthingandroid.runtime;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mode-neutral handle for one admitted bundled Syncthing invocation.
 *
 * <p>A completed wait releases the runtime admission slot. Destruction only requests termination;
 * the slot remains occupied until a later wait observes the execution's exit.</p>
 */
public final class SyncthingExecution {
    private final PrivilegeBackend.Execution execution;
    private final Runnable releaseAdmission;
    private final AtomicBoolean released = new AtomicBoolean(false);

    SyncthingExecution(PrivilegeBackend.Execution execution, Runnable releaseAdmission) {
        this.execution = execution;
        this.releaseAdmission = releaseAdmission;
    }

    public InputStream stdout() {
        return execution.stdout();
    }

    public InputStream stderr() {
        return execution.stderr();
    }

    public int await() throws InterruptedException {
        int exitCode = execution.await();
        release();
        return exitCode;
    }

    public void destroy() {
        execution.destroy();
    }

    private void release() {
        if (released.compareAndSet(false, true)) {
            releaseAdmission.run();
        }
    }
}
