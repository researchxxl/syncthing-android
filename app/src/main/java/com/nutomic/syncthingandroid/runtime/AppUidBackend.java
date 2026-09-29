package com.nutomic.syncthingandroid.runtime;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Normal-mode backend using the application UID and the bundled executable.
 *
 * <p>This class contains transport and app-UID filesystem mechanics. Runtime policy remains in
 * {@link DefaultSyncthingRuntime} and the service-facing exit policy remains in the runnable.</p>
 */
public final class AppUidBackend implements PrivilegeBackend {
    private static final String TAG = "AppUidBackend";

    private final Context context;
    private final File binary;
    private final AppUidProcessLauncher processLauncher;
    private final AppUidSyncthingTerminator syncthingTerminator;
    private final ConfigStorage configStorage;

    public AppUidBackend(Context context) {
        Context applicationContext = context.getApplicationContext();
        this.context = applicationContext;
        this.binary = Constants.getSyncthingBinary(applicationContext);
        this.processLauncher = AppUidBackend::startWithProcessBuilder;
        this.syncthingTerminator = () -> Util.killProcess(Constants.FILENAME_SYNCTHING_BINARY);
        this.configStorage = new AppUidConfigStorage(applicationContext);
    }

    /**
     * Package-private execution seam used by JVM tests without an Android process.
     */
    AppUidBackend(
            File binary,
            AppUidProcessLauncher processLauncher,
            AppUidSyncthingTerminator syncthingTerminator,
            ConfigStorage configStorage
    ) {
        this.context = null;
        this.binary = Objects.requireNonNull(binary);
        this.processLauncher = Objects.requireNonNull(processLauncher);
        this.syncthingTerminator = Objects.requireNonNull(syncthingTerminator);
        this.configStorage = Objects.requireNonNull(configStorage);
    }

    @Override
    public Execution start(SyncthingCommand command, SyncthingEnvironment environment)
            throws IOException, ExecutableNotFoundException {
        String binaryPath = binary.getPath();
        if (!binary.exists()) {
            throw new ExecutableNotFoundException(binaryPath);
        }

        return new ProcessExecution(
                processLauncher.start(command.argv(binaryPath), environment.values())
        );
    }

    @Override
    public void terminateBundledSyncthing() {
        syncthingTerminator.terminate();
    }

    @Override
    public ConfigStorage configStorage() {
        return configStorage;
    }

    @Override
    public FolderWriteability validateCandidateFolder(String path) {
        return Util.nativeBinaryCanWriteToPath(context, path)
                ? FolderWriteability.WRITABLE
                : FolderWriteability.READ_ONLY;
    }

    @Override
    public ConflictDiscoveryResult discoverConflicts(ConfiguredFolderReference folder) {
        String[] paths = Util.getSyncConflictFiles(folder.path());
        return ConflictDiscoveryResult.of(Arrays.asList(paths));
    }

    @Override
    public FolderIgnoreResult loadFolderIgnoreList(ConfiguredFolderReference folder) {
        File file = new File(folder.path(), Constants.FILENAME_STIGNORE);
        FileInputStream input = null;
        try {
            if (!file.exists()) {
                Log.w(TAG, "loadFolderIgnoreList: File missing " + file);
                return FolderIgnoreResult.of(null);
            }
            input = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            input.read(data);
            return FolderIgnoreResult.of(new String(data, StandardCharsets.UTF_8).split("\n"));
        } catch (IOException e) {
            Log.e(TAG, "loadFolderIgnoreList: Failed to read '" + file + "' #1", e);
            return FolderIgnoreResult.of(null);
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "loadFolderIgnoreList: Failed to read '" + file + "' #2", e);
            }
        }
    }

    @Override
    public void saveFolderIgnoreList(ConfiguredFolderReference folder, String[] ignore) {
        File file = new File(folder.path(), Constants.FILENAME_STIGNORE);
        FileOutputStream output = null;
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            output = new FileOutputStream(file);
            output.write(TextUtils.join("\n", ignore).getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException e) {
            Log.w(TAG, "saveFolderIgnoreList: Failed to write '" + file + "' #1", e);
        } finally {
            try {
                if (output != null) {
                    output.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "saveFolderIgnoreList: Failed to write '" + file + "' #2", e);
            }
        }
    }

    @Override
    public void runFolderScripts(
            ConfiguredFolderReference folder,
            FolderEvent event
    ) {
        String scriptDirectory = folder.path() + "/" + Constants.FILENAME_STFOLDER;
        Util.runScriptSet(scriptDirectory, new String[]{event.argument()});
    }

    private static final class ProcessExecution implements Execution {
        private final Process process;

        private ProcessExecution(Process process) {
            this.process = process;
        }

        @Override
        public InputStream stdout() {
            return process.getInputStream();
        }

        @Override
        public InputStream stderr() {
            return process.getErrorStream();
        }

        @Override
        public int await() throws InterruptedException {
            return process.waitFor();
        }

        @Override
        public void destroy() {
            process.destroy();
        }
    }

    private static Process startWithProcessBuilder(
            String[] argv,
            Map<String, String> environment
    ) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(argv);
        processBuilder.environment().putAll(environment);
        return processBuilder.start();
    }
}

/** Package-private seam for transporting an app-UID Syncthing process. */
@FunctionalInterface
interface AppUidProcessLauncher {
    Process start(String[] argv, Map<String, String> environment) throws IOException;
}

/** Package-private seam for the existing Syncthing-specific process cleanup. */
@FunctionalInterface
interface AppUidSyncthingTerminator {
    void terminate();
}
