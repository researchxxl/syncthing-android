package com.nutomic.syncthingandroid.superuser;

import static org.junit.Assert.assertEquals;

import com.nutomic.syncthingandroid.service.execution.SyncthingCommand;
import com.nutomic.syncthingandroid.service.execution.SyncthingExecutionException;
import com.nutomic.syncthingandroid.service.execution.SyncthingExecutionResult;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class SuperuserSyncthingExecutionBackendTest {

    @Test
    public void everyCoreCommandUsesOnlyItsStableId() throws Exception {
        RecordingCoreClient client = new RecordingCoreClient();
        SuperuserSyncthingExecutionBackend backend =
                new SuperuserSyncthingExecutionBackend(client);
        Map<String, String> environment = Map.of("STHOMEDIR", "/data/user/0/app/files");

        for (SyncthingCommand command : SyncthingCommand.values()) {
            backend.execute(command, environment, false);
            assertEquals(command.wireId(), client.commandId);
            assertEquals(environment, client.environment);
            assertEquals(false, client.captureStdout);
        }
        assertEquals(SyncthingCommand.values().length, client.startCalls);
    }

    @Test
    public void deviceIdReturnsBoundedCapturedOutput() throws Exception {
        RecordingCoreClient client = new RecordingCoreClient();
        client.exitResult = new SuperuserCoreExitResult(
                SuperuserErrorCode.OK.wireCode(), "", 0, "device-id\n");
        SuperuserSyncthingExecutionBackend backend =
                new SuperuserSyncthingExecutionBackend(client);

        SyncthingExecutionResult result = backend.execute(
                SyncthingCommand.DEVICE_ID, Map.of("STNOUPGRADE", "1"), true);

        assertEquals("device-id\n", result.stdout());
        assertEquals(true, client.captureStdout);
    }

    @Test
    public void startFailurePropagatesWithoutFallback() throws Exception {
        RecordingCoreClient client = new RecordingCoreClient();
        client.startResult = SuperuserOperationResult.failure(
                SuperuserErrorCode.CORE_LAUNCH_FAILED, "start failed");
        SuperuserSyncthingExecutionBackend backend =
                new SuperuserSyncthingExecutionBackend(client);

        try {
            backend.execute(SyncthingCommand.MAIN, Map.of(), false);
        } catch (SyncthingExecutionException expected) {
            assertEquals(1, client.startCalls);
            return;
        }
        throw new AssertionError("Expected privileged start failure");
    }

    @Test
    public void stopFailureIsTypedAsExecutionFailure() throws Exception {
        RecordingCoreClient client = new RecordingCoreClient();
        client.stopResult = SuperuserOperationResult.failure(
                SuperuserErrorCode.CORE_STOP_FAILED, "stop failed");
        SuperuserSyncthingExecutionBackend backend =
                new SuperuserSyncthingExecutionBackend(client);

        try {
            backend.stopOwnedProcess();
        } catch (SyncthingExecutionException expected) {
            assertEquals(1, client.stopCalls);
            return;
        }
        throw new AssertionError("Expected privileged stop failure");
    }

    private static final class RecordingCoreClient implements SuperuserCoreClient {
        private int commandId;
        private Map<String, String> environment;
        private boolean captureStdout;
        private int startCalls;
        private int stopCalls;
        private SuperuserOperationResult startResult = SuperuserOperationResult.success();
        private SuperuserOperationResult stopResult = SuperuserOperationResult.success();
        private SuperuserCoreExitResult exitResult = new SuperuserCoreExitResult(
                SuperuserErrorCode.OK.wireCode(), "", 0, "");

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public SuperuserOperationResult connectBlocking(long timeoutMs) {
            return SuperuserOperationResult.success();
        }

        @Override
        public SuperuserOperationResult verifySuperuser() {
            return SuperuserOperationResult.success();
        }

        @Override
        public SuperuserOperationResult recoverOrphanedCore() {
            return SuperuserOperationResult.success();
        }

        @Override
        public SuperuserOperationResult startCore(SyncthingCommand command,
                                                  Map<String, String> environment,
                                                  boolean captureStdout) {
            startCalls++;
            commandId = command.wireId();
            this.environment = new HashMap<>(environment);
            this.captureStdout = captureStdout;
            return startResult;
        }

        @Override
        public SuperuserCoreExitResult waitForCoreExit() {
            return exitResult;
        }

        @Override
        public SuperuserOperationResult stopOwnedCore() {
            stopCalls++;
            return stopResult;
        }

        @Override
        public SuperuserCoreStatus getCoreStatus() {
            return new SuperuserCoreStatus(SuperuserErrorCode.OK.wireCode(), "", false, -1);
        }
    }
}
