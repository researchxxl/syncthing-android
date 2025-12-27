package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.SwitchPreference


fun EntryProviderScope<SettingsRoute>.settingsUserInterfaceEntry() {
    entry<SettingsRoute.UserInterface> {
        SettingsUserInterfaceScreen()
    }
}


@Composable
fun SettingsUserInterfaceScreen() {
    val themeNames = stringArrayResource(R.array.app_theme_names)
    val themeValues = stringArrayResource(R.array.app_theme_values)

    val theme = remember { mutableStateOf(themeValues[0]) }
    val expertMode = remember { mutableStateOf(false) }
    val startInWebGui = remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.category_user_interface),
    ) {
        ListPreference(
            title = { Text(stringResource(R.string.preference_app_theme_title)) },
            state = theme,
            values = themeValues.toList(),
            valueToText = { value -> buildAnnotatedString { append(themeNames[themeValues.indexOf(value)]) } }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.expert_mode_title)) },
            summary = { Text(stringResource(R.string.expert_mode_summary)) },
            state = expertMode,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.start_into_web_gui_title)) },
            summary = { Text(stringResource(R.string.start_into_web_gui_summary)) },
            state = startInWebGui,
        )
    }
}
