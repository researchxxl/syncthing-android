package com.nutomic.syncthingandroid.settings

import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.AppPrefs
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.superuser.SuperuserErrorCode
import com.nutomic.syncthingandroid.superuser.SuperuserRuntimeStatus
import kotlinx.coroutines.delay
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.rememberPreferenceState


fun EntryProviderScope<SettingsRoute>.settingsBehaviorEntry() {
    entry<SettingsRoute.Behavior> {
        SettingsBehaviorScreen()
    }
}


@Composable
fun SettingsBehaviorScreen() {

    val context = LocalContext.current
    val service = LocalSyncthingService.current
    LocalServiceUpdateTick.current
    val configured = AppPrefs.getUseRoot(context)
    val runtimeStatus = service?.getSuperuserRuntimeStatus()
        ?: SuperuserRuntimeStatus.normal()
    var transition by rememberSaveable { mutableStateOf(SuperuserTransitionUiState.IDLE) }

    val superuserState = projectSuperuserPreferenceUiState(
        configured = configured,
        runtimeStatus = runtimeStatus,
        transition = transition,
        serviceAvailable = service != null,
    )

    LaunchedEffect(transition) {
        if (transition == SuperuserTransitionUiState.FAILED) {
            delay(3_000L)
            transition = SuperuserTransitionUiState.IDLE
        }
    }

    val autoStart = rememberPreferenceState(Constants.PREF_START_SERVICE_ON_BOOT, false)
    val broadcast = rememberPreferenceState(Constants.PREF_BROADCAST_SERVICE_CONTROL, false)
    val overwrite = rememberPreferenceState(Constants.PREF_ALLOW_OVERWRITE_FILES, false)

    SettingsScaffold(
        title = stringResource(R.string.category_behaviour),
    ) {
        item {
            SuperuserPreferenceRow(superuserState) { desired ->
                transition = if (desired) {
                    SuperuserTransitionUiState.ENABLING
                } else {
                    SuperuserTransitionUiState.DISABLING
                }
                service?.requestSuperuserMode(desired,
                    SyncthingService.SuperuserTransitionCallback { result ->
                        if (result.isSuccess) {
                            transition = SuperuserTransitionUiState.IDLE
                        } else {
                            transition = SuperuserTransitionUiState.FAILED
                            val message = when (result.error()) {
                                SuperuserErrorCode.AUTHORIZATION_DENIED,
                                SuperuserErrorCode.ROOT_UNAVAILABLE,
                                SuperuserErrorCode.TIMEOUT,
                                SuperuserErrorCode.UID_VERIFICATION_FAILED ->
                                    R.string.toast_root_denied
                                else -> R.string.use_root_unavailable
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    })
            }
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.behaviour_autostart_title)) },
                summary = { Text(stringResource(R.string.behaviour_autostart_summary)) },
                state = autoStart,
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.broadcast_service_control_title))},
                summary = { Text(stringResource(R.string.broadcast_service_control_summary))},
                state = broadcast,
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.allow_overwrite_files_title)) },
                summary = { Text(stringResource(R.string.allow_overwrite_files_summary))},
                state = overwrite,
            )
        }
    }
}
