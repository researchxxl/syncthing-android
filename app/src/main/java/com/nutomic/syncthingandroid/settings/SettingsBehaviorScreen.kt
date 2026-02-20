package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.rememberPreferenceState


fun EntryProviderScope<SettingsRoute>.settingsBehaviorEntry() {
    entry<SettingsRoute.Behavior> {
        SettingsBehaviorScreen()
    }
}


@Composable
fun SettingsBehaviorScreen() {

    val autoStart = rememberPreferenceState(Constants.PREF_START_SERVICE_ON_BOOT, false)
    val broadcast = rememberPreferenceState(Constants.PREF_BROADCAST_SERVICE_CONTROL, false)
    val overwrite = rememberPreferenceState(Constants.PREF_ALLOW_OVERWRITE_FILES, false)

    SettingsScaffold(
        title = stringResource(R.string.category_behaviour),
    ) {
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
