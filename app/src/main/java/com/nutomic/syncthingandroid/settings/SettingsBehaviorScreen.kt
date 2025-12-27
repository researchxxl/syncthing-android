package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import me.zhanghai.compose.preference.SwitchPreference


fun EntryProviderScope<SettingsRoute>.settingsBehaviorEntry() {
    entry<SettingsRoute.Behavior> {
        SettingsBehaviorScreen()
    }
}


@Composable
fun SettingsBehaviorScreen() {
    // temp code to make it work
    val autoStart = remember { mutableStateOf(false) }
    val broadcast = remember { mutableStateOf(false) }
    val overwrite = remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.category_behaviour),
    ) {
        SwitchPreference(
            title = { Text(stringResource(R.string.behaviour_autostart_title)) },
            summary = { Text(stringResource(R.string.behaviour_autostart_summary)) },
            state = autoStart,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.broadcast_service_control_title))},
            summary = { Text(stringResource(R.string.broadcast_service_control_summary))},
            state = broadcast,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.allow_overwrite_files_title)) },
            summary = { Text(stringResource(R.string.allow_overwrite_files_summary))},
            state = overwrite,
        )
    }
}
