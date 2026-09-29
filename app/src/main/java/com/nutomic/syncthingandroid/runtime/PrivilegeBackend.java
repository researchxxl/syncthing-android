package com.nutomic.syncthingandroid.runtime;

import java.io.IOException;
import java.io.InputStream;

/**
 * Backend mechanics selected by the mode-neutral runtime.
 *
 * <p>Implementations own process transport and identity-specific folder mechanics. They do not
 * own user-visible Syncthing exit policy or service lifecycle decisions.</p>
 */
public interface PrivilegeBackend {
    Execution start(SyncthingCommand command, SyncthingEnvironment environment)
            throws IOException, ExecutableNotFoundException;

    /**
     * Applies the existing Normal Mode compatibility cleanup for bundled Syncthing processes.
     *
     * <p>The selected backend owns how this cleanup is performed. Callers decide when the
     * cleanup is required as part of service lifecycle transitions.</p>
     */
    void terminateBundledSyncthing();

    ConfigStorage configStorage();

    FolderWriteability validateCandidateFolder(String path);

    ConflictDiscoveryResult discoverConflicts(ConfiguredFolderReference folder);

    FolderIgnoreResult loadFolderIgnoreList(ConfiguredFolderReference folder);

    void saveFolderIgnoreList(ConfiguredFolderReference folder, String[] ignore);

    void runFolderScripts(ConfiguredFolderReference folder, FolderEvent event);

    interface Execution {
        InputStream stdout();

        InputStream stderr();

        int await() throws InterruptedException;

        void destroy();
    }
}
