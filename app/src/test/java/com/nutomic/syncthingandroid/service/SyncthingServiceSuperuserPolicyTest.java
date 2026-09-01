package com.nutomic.syncthingandroid.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.nutomic.syncthingandroid.superuser.SuperuserErrorCode;
import com.nutomic.syncthingandroid.superuser.SuperuserRuntimeStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Covers service-facing root policy invariants that do not require an Android service runtime. */
public class SyncthingServiceSuperuserPolicyTest {

    @Test
    public void rootUnavailableIsASeparateServiceState() {
        assertEquals(SyncthingService.State.SUPERUSER_UNAVAILABLE,
                SyncthingService.State.valueOf("SUPERUSER_UNAVAILABLE"));
        assertEquals(SuperuserRuntimeStatus.State.SUPERUSER_UNAVAILABLE,
                SuperuserRuntimeStatus.unavailable(
                        SuperuserErrorCode.ROOT_UNAVAILABLE, false, "diagnostic").state());
    }

    @Test
    public void runtimeFailureVocabularyCarriesOrphanRiskSeparately() {
        SuperuserRuntimeStatus status = SuperuserRuntimeStatus.unavailable(
                SuperuserErrorCode.BINDER_DIED, true, "connection lost");

        assertEquals(SuperuserErrorCode.BINDER_DIED, status.errorCode());
        assertTrue(status.orphanRisk());
        assertEquals("connection lost", status.diagnostic());
    }

    @Test
    public void serviceLifecycleDoesNotUseProcessNameKillingOrLegacyCommandCalls() throws IOException {
        String source = Files.readString(repoPath().resolve(
                "app/src/main/java/com/nutomic/syncthingandroid/service/SyncthingService.java"),
                StandardCharsets.UTF_8);

        assertFalse(source.contains("Util.killProcess"));
        assertFalse(source.contains("SyncthingRunnable.Command"));
        assertTrue(source.contains("SyncthingExecutionController"));
        assertTrue(source.contains("SUPERUSER_UNAVAILABLE"));
    }

    private static Path repoPath() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null && !Files.isDirectory(path.resolve("app/src"))) {
            path = path.getParent();
        }
        if (path == null) {
            throw new IllegalStateException("Unable to locate repository root from test directory");
        }
        return path;
    }
}
