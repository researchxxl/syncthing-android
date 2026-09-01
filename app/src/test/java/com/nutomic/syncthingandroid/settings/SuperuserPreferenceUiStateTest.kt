package com.nutomic.syncthingandroid.settings

import com.nutomic.syncthingandroid.superuser.SuperuserErrorCode
import com.nutomic.syncthingandroid.superuser.SuperuserRuntimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperuserPreferenceUiStateTest {

    @Test
    fun normalIdleStateIsUncheckedAndEnabledWithCaution() {
        val state = projectSuperuserPreferenceUiState(
            configured = false,
            runtimeStatus = SuperuserRuntimeStatus.normal(),
            transition = SuperuserTransitionUiState.IDLE,
            serviceAvailable = true,
        )

        assertFalse(state.checked)
        assertTrue(state.enabled)
        assertEquals(Summary.CAUTION, state.summary)
    }

    @Test
    fun enablingOrAuthenticatingIsOffAndDisabled() {
        val enabling = projectSuperuserPreferenceUiState(
            configured = false,
            runtimeStatus = SuperuserRuntimeStatus.authorizing(),
            transition = SuperuserTransitionUiState.ENABLING,
            serviceAvailable = true,
        )

        assertFalse(enabling.checked)
        assertFalse(enabling.enabled)
        assertEquals(Summary.REQUESTING, enabling.summary)
    }

    @Test
    fun readyConfiguredStateIsCheckedAndEnabled() {
        val state = projectSuperuserPreferenceUiState(
            configured = true,
            runtimeStatus = SuperuserRuntimeStatus.ready(),
            transition = SuperuserTransitionUiState.IDLE,
            serviceAvailable = true,
        )

        assertTrue(state.checked)
        assertTrue(state.enabled)
        assertEquals(Summary.ENABLED, state.summary)
    }

    @Test
    fun unavailableConfiguredStateStaysChecked() {
        val state = projectSuperuserPreferenceUiState(
            configured = true,
            runtimeStatus = SuperuserRuntimeStatus.unavailable(
                SuperuserErrorCode.ROOT_UNAVAILABLE, false, "not shown"),
            transition = SuperuserTransitionUiState.IDLE,
            serviceAvailable = true,
        )

        assertTrue(state.checked)
        assertTrue(state.enabled)
        assertEquals(Summary.UNAVAILABLE, state.summary)
    }

    @Test
    fun disablingStaysCheckedAndDisabled() {
        val state = projectSuperuserPreferenceUiState(
            configured = true,
            runtimeStatus = SuperuserRuntimeStatus.ready(),
            transition = SuperuserTransitionUiState.DISABLING,
            serviceAvailable = true,
        )

        assertTrue(state.checked)
        assertFalse(state.enabled)
        assertEquals(Summary.DISABLING, state.summary)
    }

    @Test
    fun failedEnableUsesUncommittedUncheckedValue() {
        val state = projectSuperuserPreferenceUiState(
            configured = false,
            runtimeStatus = SuperuserRuntimeStatus.unavailable(
                SuperuserErrorCode.AUTHORIZATION_DENIED, false, "not shown"),
            transition = SuperuserTransitionUiState.FAILED,
            serviceAvailable = true,
        )

        assertFalse(state.checked)
        assertTrue(state.enabled)
        assertEquals(Summary.FAILURE, state.summary)
    }

    @Test
    fun failedDisableLeavesCommittedValueChecked() {
        val state = projectSuperuserPreferenceUiState(
            configured = true,
            runtimeStatus = SuperuserRuntimeStatus.unavailable(
                SuperuserErrorCode.OWNERSHIP_REPAIR_FAILED, false, "not shown"),
            transition = SuperuserTransitionUiState.FAILED,
            serviceAvailable = true,
        )

        assertTrue(state.checked)
        assertTrue(state.enabled)
        assertEquals(Summary.FAILURE, state.summary)
    }

    @Test
    fun missingServiceDisablesInteractionButPreservesCommittedValue() {
        val state = projectSuperuserPreferenceUiState(
            configured = true,
            runtimeStatus = SuperuserRuntimeStatus.unavailable(
                SuperuserErrorCode.SERVICE_BIND_FAILED, false, "not shown"),
            transition = SuperuserTransitionUiState.IDLE,
            serviceAvailable = false,
        )

        assertTrue(state.checked)
        assertFalse(state.enabled)
    }
}
