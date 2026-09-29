package com.nutomic.syncthingandroid.runtime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class RuntimeSeamTest {

    @Test
    public void commandsOwnTheSupportedSyncthingArgv() {
        String binary = "/data/app/lib/libsyncthingnative.so";

        assertArrayEquals(
                new String[]{binary, "serve", "--no-browser"},
                SyncthingCommand.SERVE.argv(binary)
        );
        assertArrayEquals(
                new String[]{binary, "device-id"},
                SyncthingCommand.DEVICE_ID.argv(binary)
        );
        assertArrayEquals(
                new String[]{binary, "generate"},
                SyncthingCommand.GENERATE.argv(binary)
        );
        assertArrayEquals(
                new String[]{binary, "debug", "reset-database"},
                SyncthingCommand.RESET_DATABASE.argv(binary)
        );
        assertArrayEquals(
                new String[]{binary, "serve", "--debug-reset-delta-idxs"},
                SyncthingCommand.RESET_DELTAS.argv(binary)
        );
    }

    @Test
    public void environmentPreservesTheNormalModeContract() {
        SyncthingEnvironment environment = SyncthingEnvironment.builder()
                .home("/data/user/0/app/syncthing")
                .syncthingHome("/data/user/0/app/files")
                .trace("fs,connections")
                .monitored()
                .noUpgrade()
                .versionExtra("Syncthing")
                .sqliteTemporaryDirectory("/data/user/0/app/cache")
                .fallbackGatewayIpv4("192.0.2.1")
                .torProxy()
                .gogc(75)
                .customVariables(Collections.singletonMap("CUSTOM", "value"))
                .build();

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("HOME", "/data/user/0/app/syncthing");
        expected.put("STHOMEDIR", "/data/user/0/app/files");
        expected.put("STTRACE", "fs,connections");
        expected.put("STMONITORED", "1");
        expected.put("STNOUPGRADE", "1");
        expected.put("STVERSIONEXTRA", "Syncthing");
        expected.put("SQLITE_TMPDIR", "/data/user/0/app/cache");
        expected.put("FALLBACK_NET_GATEWAY_IPV4", "192.0.2.1");
        expected.put("all_proxy", "socks5://localhost:9050");
        expected.put("ALL_PROXY_NO_FALLBACK", "1");
        expected.put("GOGC", "75");
        expected.put("CUSTOM", "value");

        assertEquals(expected, environment.values());
    }

    @Test
    public void environmentSupportsNormalModeProxyVariants() {
        SyncthingEnvironment environment = SyncthingEnvironment.builder()
                .home("/home")
                .syncthingHome("/state")
                .trace("")
                .monitored()
                .noUpgrade()
                .versionExtra("app")
                .sqliteTemporaryDirectory("/tmp")
                .socksProxy("socks5://127.0.0.1:9050")
                .httpProxy("http://127.0.0.1:8080")
                .gogc(100)
                .build();

        assertEquals("socks5://127.0.0.1:9050", environment.values().get("all_proxy"));
        assertEquals("http://127.0.0.1:8080", environment.values().get("http_proxy"));
        assertEquals("http://127.0.0.1:8080", environment.values().get("https_proxy"));
    }

    @Test
    public void runtimeAdmitsOnlyOneSyncthingInvocationAndDelegatesEnvironment() throws Exception {
        RecordingBackend backend = new RecordingBackend();
        DefaultSyncthingRuntime runtime = new DefaultSyncthingRuntime(backend);
        SyncthingEnvironment environment = SyncthingEnvironment.builder()
                .home("/home")
                .syncthingHome("/state")
                .trace("")
                .monitored()
                .noUpgrade()
                .versionExtra("app")
                .sqliteTemporaryDirectory("/tmp")
                .gogc(100)
                .build();

        SyncthingExecution first = runtime.start(SyncthingCommand.SERVE, environment);

        assertSame(SyncthingCommand.SERVE, backend.command);
        assertSame(environment, backend.environment);
        assertThrows(
                ExecutionAdmissionException.class,
                () -> runtime.start(SyncthingCommand.DEVICE_ID, environment)
        );

        first.await();
        SyncthingExecution second = runtime.start(SyncthingCommand.DEVICE_ID, environment);
        second.await();
    }

    @Test
    public void destroyingAnInvocationKeepsAdmissionUntilExecutionExit() throws Exception {
        RecordingBackend backend = new RecordingBackend();
        DelayedTerminationExecution execution = new DelayedTerminationExecution();
        backend.execution = execution;
        DefaultSyncthingRuntime runtime = new DefaultSyncthingRuntime(backend);
        SyncthingEnvironment environment = SyncthingEnvironment.builder()
                .home("/home")
                .syncthingHome("/state")
                .trace("")
                .monitored()
                .noUpgrade()
                .versionExtra("app")
                .sqliteTemporaryDirectory("/tmp")
                .gogc(100)
                .build();

        SyncthingExecution first = runtime.start(SyncthingCommand.SERVE, environment);
        first.destroy();
        assertTrue(execution.destroyRequested);

        assertThrows(
                ExecutionAdmissionException.class,
                () -> runtime.start(SyncthingCommand.DEVICE_ID, environment)
        );

        execution.exit(137);
        assertEquals(137, first.await());

        backend.execution = new ImmediateExecution();
        SyncthingExecution second = runtime.start(SyncthingCommand.DEVICE_ID, environment);
        second.await();
    }

    @Test
    public void interruptedAwaitKeepsAdmissionUntilExecutionExit() throws Exception {
        RecordingBackend backend = new RecordingBackend();
        InterruptedThenDelayedExitExecution execution = new InterruptedThenDelayedExitExecution();
        backend.execution = execution;
        DefaultSyncthingRuntime runtime = new DefaultSyncthingRuntime(backend);
        SyncthingEnvironment environment = SyncthingEnvironment.builder()
                .home("/home")
                .syncthingHome("/state")
                .trace("")
                .monitored()
                .noUpgrade()
                .versionExtra("app")
                .sqliteTemporaryDirectory("/tmp")
                .gogc(100)
                .build();

        SyncthingExecution first = runtime.start(SyncthingCommand.SERVE, environment);

        assertThrows(InterruptedException.class, first::await);
        assertThrows(
                ExecutionAdmissionException.class,
                () -> runtime.start(SyncthingCommand.DEVICE_ID, environment)
        );

        first.destroy();
        assertTrue(execution.destroyRequested);
        assertThrows(
                ExecutionAdmissionException.class,
                () -> runtime.start(SyncthingCommand.DEVICE_ID, environment)
        );

        execution.exit(0);
        assertEquals(0, first.await());

        backend.execution = new ImmediateExecution();
        SyncthingExecution second = runtime.start(SyncthingCommand.DEVICE_ID, environment);
        second.await();
    }

    @Test
    public void runtimeKeepsConfigStorageAndFolderOperationsSemantic() throws IOException {
        RecordingBackend backend = new RecordingBackend();
        DefaultSyncthingRuntime runtime = new DefaultSyncthingRuntime(backend);
        ConfigStorage storage = backend.storage;
        ConfiguredFolderReference folder = ConfiguredFolderReference.of("folder-id", "/configured/folder");

        assertSame(storage, runtime.configStorage());
        storage.save(new byte[]{1, 2, 3});
        assertArrayEquals(new byte[]{1, 2, 3}, storage.load());
        assertEquals(
                FolderWriteability.WRITABLE,
                runtime.validateCandidateFolder("/candidate/folder")
        );
        assertEquals(
                Arrays.asList("nested/file.sync-conflict-20260101-010101-DEVICE"),
                runtime.discoverConflicts(folder).relativePaths()
        );
        assertArrayEquals(
                new String[]{"*.tmp"},
                runtime.loadFolderIgnoreList(folder).lines()
        );
        runtime.saveFolderIgnoreList(folder, new String[]{"*.tmp", "*.bak"});
        assertArrayEquals(new String[]{"*.tmp", "*.bak"}, backend.ignore);
        runtime.runFolderScripts(folder, FolderEvent.SYNC_COMPLETE);
        assertEquals("folder-id", backend.folder.id());
        assertEquals("/configured/folder", backend.folder.path());
        assertEquals(FolderEvent.SYNC_COMPLETE, backend.event);
        assertEquals("sync_complete", FolderEvent.SYNC_COMPLETE.argument());
        runtime.terminateBundledSyncthing();
        assertTrue(backend.terminationRequested);
    }

    @Test
    public void appUidBackendPreservesProcessTransportOutputAndCleanup() throws Exception {
        File binary = File.createTempFile("syncthing", ".bin");
        binary.deleteOnExit();
        RecordingProcess process = new RecordingProcess("stdout", "stderr", 23);
        RecordingProcessLauncher launcher = new RecordingProcessLauncher(process);
        boolean[] terminationRequested = new boolean[1];
        AppUidBackend backend = new AppUidBackend(
                binary,
                launcher,
                () -> terminationRequested[0] = true,
                new InMemoryConfigStorage()
        );
        SyncthingEnvironment environment = SyncthingEnvironment.builder()
                .home("/home")
                .syncthingHome("/state")
                .trace("")
                .monitored()
                .noUpgrade()
                .versionExtra("app")
                .sqliteTemporaryDirectory("/tmp")
                .gogc(100)
                .customVariables(Collections.singletonMap("CUSTOM", "value"))
                .build();

        PrivilegeBackend.Execution execution = backend.start(
                SyncthingCommand.DEVICE_ID,
                environment
        );

        assertArrayEquals(
                new String[]{binary.getPath(), "device-id"},
                launcher.argv
        );
        assertEquals(environment.values(), launcher.environment);
        assertEquals("stdout", new String(execution.stdout().readAllBytes(), StandardCharsets.UTF_8));
        assertEquals("stderr", new String(execution.stderr().readAllBytes(), StandardCharsets.UTF_8));
        assertEquals(23, execution.await());
        execution.destroy();
        assertTrue(process.destroyed);

        backend.terminateBundledSyncthing();
        assertTrue(terminationRequested[0]);
    }

    private static final class RecordingBackend implements PrivilegeBackend {
        private final ConfigStorage storage = new InMemoryConfigStorage();
        private Execution execution = new ImmediateExecution();
        private SyncthingCommand command;
        private SyncthingEnvironment environment;
        private ConfiguredFolderReference folder;
        private FolderEvent event;
        private String[] ignore;
        private boolean terminationRequested;

        @Override
        public Execution start(SyncthingCommand command, SyncthingEnvironment environment)
                throws IOException, ExecutableNotFoundException {
            this.command = command;
            this.environment = environment;
            return execution;
        }

        @Override
        public ConfigStorage configStorage() {
            return storage;
        }

        @Override
        public FolderWriteability validateCandidateFolder(String path) {
            return FolderWriteability.WRITABLE;
        }

        @Override
        public ConflictDiscoveryResult discoverConflicts(ConfiguredFolderReference folder) {
            this.folder = folder;
            return ConflictDiscoveryResult.of(
                    Collections.singletonList("nested/file.sync-conflict-20260101-010101-DEVICE")
            );
        }

        @Override
        public FolderIgnoreResult loadFolderIgnoreList(ConfiguredFolderReference folder) {
            this.folder = folder;
            return FolderIgnoreResult.of(new String[]{"*.tmp"});
        }

        @Override
        public void saveFolderIgnoreList(ConfiguredFolderReference folder, String[] ignore) {
            this.folder = folder;
            this.ignore = ignore.clone();
        }

        @Override
        public void runFolderScripts(
                ConfiguredFolderReference folder,
                FolderEvent event
        ) {
            this.folder = folder;
            this.event = event;
        }

        @Override
        public void terminateBundledSyncthing() {
            terminationRequested = true;
        }
    }

    private static final class ImmediateExecution implements PrivilegeBackend.Execution {
        @Override
        public InputStream stdout() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream stderr() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int await() {
            return 0;
        }

        @Override
        public void destroy() {
        }
    }

    private static final class DelayedTerminationExecution implements PrivilegeBackend.Execution {
        private boolean destroyRequested;
        private boolean exited;
        private int exitCode;

        @Override
        public InputStream stdout() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream stderr() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int await() {
            if (!exited) {
                throw new AssertionError("test execution has not exited");
            }
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyRequested = true;
        }

        private void exit(int exitCode) {
            this.exitCode = exitCode;
            exited = true;
        }
    }

    private static final class InterruptedThenDelayedExitExecution
            implements PrivilegeBackend.Execution {
        private boolean interrupted = true;
        private boolean destroyRequested;
        private boolean exited;
        private int exitCode;

        @Override
        public InputStream stdout() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream stderr() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int await() throws InterruptedException {
            if (interrupted) {
                interrupted = false;
                throw new InterruptedException("test interruption");
            }
            if (!exited) {
                throw new AssertionError("test execution has not exited");
            }
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyRequested = true;
        }

        private void exit(int exitCode) {
            this.exitCode = exitCode;
            exited = true;
        }
    }

    private static final class RecordingProcessLauncher implements AppUidProcessLauncher {
        private final Process process;
        private String[] argv;
        private Map<String, String> environment;

        private RecordingProcessLauncher(Process process) {
            this.process = process;
        }

        @Override
        public Process start(String[] argv, Map<String, String> environment) {
            this.argv = argv.clone();
            this.environment = environment;
            return process;
        }
    }

    private static final class RecordingProcess extends Process {
        private final InputStream stdout;
        private final InputStream stderr;
        private final int exitCode;
        private boolean destroyed;

        private RecordingProcess(String stdout, String stderr, int exitCode) {
            this.stdout = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
            this.stderr = new ByteArrayInputStream(stderr.getBytes(StandardCharsets.UTF_8));
            this.exitCode = exitCode;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }

    private static final class InMemoryConfigStorage implements ConfigStorage {
        private byte[] contents = new byte[0];

        @Override
        public boolean canRead() {
            return true;
        }

        @Override
        public byte[] load() {
            return contents.clone();
        }

        @Override
        public boolean canWrite() {
            return true;
        }

        @Override
        public void save(byte[] contents) {
            this.contents = contents.clone();
        }
    }
}
