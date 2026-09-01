package com.nutomic.syncthingandroid.superuser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SuperuserRuntimeStatusTest {

    @Test
    public void unavailableStatusRetainsTypedDiagnostic() {
        SuperuserRuntimeStatus status = SuperuserRuntimeStatus.unavailable(
                SuperuserErrorCode.ORPHAN_RECOVERY_FAILED,
                true,
                "orphan identity could not be verified");

        assertEquals(SuperuserRuntimeStatus.State.SUPERUSER_UNAVAILABLE, status.state());
        assertEquals(SuperuserErrorCode.ORPHAN_RECOVERY_FAILED, status.errorCode());
        assertTrue(status.orphanRisk());
        assertEquals("orphan identity could not be verified", status.diagnostic());
    }

    @Test
    public void ordinaryStatusesHaveNoFailureMetadata() {
        assertEquals(SuperuserRuntimeStatus.State.NORMAL,
                SuperuserRuntimeStatus.normal().state());
        assertEquals(SuperuserRuntimeStatus.State.AUTHORIZING,
                SuperuserRuntimeStatus.authorizing().state());
        assertEquals(SuperuserRuntimeStatus.State.SUPERUSER_READY,
                SuperuserRuntimeStatus.ready().state());
        assertFalse(SuperuserRuntimeStatus.ready().orphanRisk());
    }
}
