package com.nutomic.syncthingandroid.runtime;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mode-neutral runtime for bundled Syncthing execution and semantic operations.
 *
 * <p>The runtime owns the invariant that only one bundled Syncthing invocation may be active.
 * Backend-specific process and folder mechanics remain behind {@link PrivilegeBackend}.</p>
 */
public final class DefaultSyncthingRuntime {
    private final PrivilegeBackend backend;
    private final AtomicBoolean invocationActive = new AtomicBoolean(false);

    public DefaultSyncthingRuntime(PrivilegeBackend backend) {
        this.backend = Objects.requireNonNull(backend);
    }

    public SyncthingExecution start(SyncthingCommand command, SyncthingEnvironment environment)
            throws IOException, ExecutableNotFoundException {
        if (!invocationActive.compareAndSet(false, true)) {
            throw new ExecutionAdmissionException();
        }

        try {
            PrivilegeBackend.Execution execution = backend.start(command, environment);
            return new SyncthingExecution(execution, () -> invocationActive.set(false));
        } catch (IOException | ExecutableNotFoundException | RuntimeException e) {
            invocationActive.set(false);
            throw e;
        }
    }

    /**
     * Performs the selected backend's Syncthing-specific process compatibility cleanup.
     */
    public void terminateBundledSyncthing() {
        backend.terminateBundledSyncthing();
    }

    public ConfigStorage configStorage() {
        return backend.configStorage();
    }

    public FolderWriteability validateCandidateFolder(String path) {
        return backend.validateCandidateFolder(path);
    }

    public ConflictDiscoveryResult discoverConflicts(ConfiguredFolderReference folder) {
        return backend.discoverConflicts(folder);
    }

    public FolderIgnoreResult loadFolderIgnoreList(ConfiguredFolderReference folder) {
        return backend.loadFolderIgnoreList(folder);
    }

    public void saveFolderIgnoreList(ConfiguredFolderReference folder, String[] ignore) {
        backend.saveFolderIgnoreList(folder, ignore);
    }

    public void runFolderScripts(
            ConfiguredFolderReference folder,
            FolderEvent event
    ) {
        backend.runFolderScripts(folder, event);
    }
}
