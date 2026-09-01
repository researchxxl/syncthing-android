package com.nutomic.syncthingandroid.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.superuser.SuperuserRuntimeStatus
import me.zhanghai.compose.preference.SwitchPreference

/** The short-lived visual state of the manually controlled superuser switch. */
enum class SuperuserTransitionUiState {
    IDLE,
    ENABLING,
    DISABLING,
    FAILED,
}

/** Copy state selected for the superuser preference row. */
enum class Summary {
    CAUTION,
    REQUESTING,
    ENABLED,
    UNAVAILABLE,
    DISABLING,
    FAILURE,
}

/** Immutable presentation state for the Behavior screen's superuser preference. */
data class SuperuserPreferenceUiState(
    val checked: Boolean,
    val enabled: Boolean,
    val summary: Summary,
)

/**
 * Projects durable configuration, runtime privilege proof, and transient UI state into the
 * switch state. The durable preference remains the source of truth for the checked value.
 */
fun projectSuperuserPreferenceUiState(
    configured: Boolean,
    runtimeStatus: SuperuserRuntimeStatus,
    transition: SuperuserTransitionUiState,
    serviceAvailable: Boolean,
): SuperuserPreferenceUiState {
    val summary = when (transition) {
        SuperuserTransitionUiState.ENABLING -> Summary.REQUESTING
        SuperuserTransitionUiState.DISABLING -> Summary.DISABLING
        SuperuserTransitionUiState.FAILED -> Summary.FAILURE
        SuperuserTransitionUiState.IDLE -> when {
            configured && runtimeStatus.state()
                    == SuperuserRuntimeStatus.State.SUPERUSER_READY -> Summary.ENABLED
            configured && runtimeStatus.state()
                    == SuperuserRuntimeStatus.State.SUPERUSER_UNAVAILABLE -> Summary.UNAVAILABLE
            else -> Summary.CAUTION
        }
    }

    return SuperuserPreferenceUiState(
        checked = configured && transition != SuperuserTransitionUiState.ENABLING,
        enabled = serviceAvailable && (transition == SuperuserTransitionUiState.IDLE
                || transition == SuperuserTransitionUiState.FAILED),
        summary = summary,
    )
}

/** Renders the row without owning or mutating SharedPreferences. */
@Composable
internal fun SuperuserPreferenceRow(
    state: SuperuserPreferenceUiState,
    onToggle: (Boolean) -> Unit,
) {
    SwitchPreference(
        value = state.checked,
        onValueChange = onToggle,
        title = { androidx.compose.material3.Text(stringResource(R.string.use_root_title)) },
        enabled = state.enabled,
        summary = {
            androidx.compose.material3.Text(
                stringResource(
                    when (state.summary) {
                        Summary.CAUTION -> R.string.use_root_summary
                        Summary.REQUESTING -> R.string.use_root_requesting
                        Summary.ENABLED -> R.string.use_root_enabled
                        Summary.UNAVAILABLE -> R.string.use_root_unavailable
                        Summary.DISABLING -> R.string.use_root_disabling
                        Summary.FAILURE -> R.string.toast_root_denied
                    }
                )
            )
        },
    )
}
