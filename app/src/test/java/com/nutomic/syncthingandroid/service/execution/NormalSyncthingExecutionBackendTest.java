package com.nutomic.syncthingandroid.service.execution;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class NormalSyncthingExecutionBackendTest {

    @Test
    public void executeUsesClosedCommandAndProvidedEnvironment() throws Exception {
        File binary = new File("/data/app/libsyncthingnative.so");
        File logFile = new File("build/test-syncthing.log");
        FakeProcess process = new FakeProcess("device-id-output\n", "");
        RecordingFactory factory = new RecordingFactory(process);
        NormalSyncthingExecutionBackend backend =
                new NormalSyncthingExecutionBackend(binary, logFile, factory);
        Map<String, String> environment = new HashMap<>();
        environment.put("STHOMEDIR", "/data/user/0/app/files");
        environment.put("STNOUPGRADE", "1");

        SyncthingExecutionResult result = backend.execute(
                SyncthingCommand.DEVICE_ID, environment, true);

        assertArrayEquals(
                new String[]{binary.getPath(), "device-id"}, factory.argv);
        assertEquals(environment, factory.environment);
        assertEquals(0, result.exitCode());
        assertEquals("device-id-output\n", result.stdout());
        assertFalse(process.destroyed);
    }

    @Test
    public void stopOwnedProcessDestroysOnlyTheProcessCreatedByBackend() throws Exception {
        File binary = new File("/data/app/libsyncthingnative.so");
        BlockingProcess process = new BlockingProcess();
        NormalSyncthingExecutionBackend backend = new NormalSyncthingExecutionBackend(
                binary, new File("build/test-syncthing.log"), (argv, environment) -> process);

        Thread execution = new Thread(() -> {
            try {
                backend.execute(SyncthingCommand.MAIN, Map.of(), false);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        execution.start();
        assertTrue(process.started.await(2, TimeUnit.SECONDS));

        backend.stopOwnedProcess();
        assertTrue(process.destroyed);
        process.finish();
        execution.join(2000);
        assertFalse(execution.isAlive());
    }

    @Test
    public void completedProcessClearsOwnedReference() throws Exception {
        File binary = new File("/data/app/libsyncthingnative.so");
        FakeProcess process = new FakeProcess("", "");
        NormalSyncthingExecutionBackend backend = new NormalSyncthingExecutionBackend(
                binary, new File("build/test-syncthing.log"), (argv, environment) -> process);

        backend.execute(SyncthingCommand.MAIN, Map.of(), false);
        backend.stopOwnedProcess();

        assertFalse(process.destroyed);
    }

    private static class RecordingFactory implements NormalSyncthingExecutionBackend.ProcessFactory {
        private final Process process;
        private String[] argv;
        private Map<String, String> environment;

        RecordingFactory(Process process) {
            this.process = process;
        }

        @Override
        public Process start(String[] argv, Map<String, String> environment) {
            this.argv = argv;
            this.environment = new HashMap<>(environment);
            return process;
        }
    }

    private static class FakeProcess extends Process {
        private final InputStream stdout;
        private final InputStream stderr;
        private final OutputStream stdin = new ByteArrayOutputStream();
        boolean destroyed;

        FakeProcess(String stdout, String stderr) {
            this.stdout = new ByteArrayInputStream(stdout.getBytes());
            this.stderr = new ByteArrayInputStream(stderr.getBytes());
        }

        @Override
        public OutputStream getOutputStream() {
            return stdin;
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
        public int waitFor() throws InterruptedException {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }

    private static final class BlockingProcess extends FakeProcess {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch finished = new CountDownLatch(1);

        BlockingProcess() {
            super("", "");
        }

        @Override
        public int waitFor() throws InterruptedException {
            started.countDown();
            finished.await();
            return 0;
        }

        void finish() {
            finished.countDown();
        }
    }
}
