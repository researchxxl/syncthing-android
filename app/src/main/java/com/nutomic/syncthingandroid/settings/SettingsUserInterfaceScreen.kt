package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.rememberPreferenceState


fun EntryProviderScope<SettingsRoute>.settingsUserInterfaceEntry() {
    entry<SettingsRoute.UserInterface> {
        SettingsUserInterfaceScreen()
    }
}


@Composable
fun SettingsUserInterfaceScreen() {
    val themeNames = stringArrayResource(R.array.app_theme_names)
    val themeValues = stringArrayResource(R.array.app_theme_values)

    val theme = rememberPreferenceState(Constants.PREF_APP_THEME, Constants.APP_THEME_FOLLOW_SYSTEM)
    val expertMode = rememberPreferenceState(Constants.PREF_EXPERT_MODE, false)
    val startInWebGui = rememberPreferenceState(Constants.PREF_START_INTO_WEB_GUI, false)

    SettingsScaffold(
        title = stringResource(R.string.category_user_interface),
    ) {
        // TODO: update theme with configRouter and restart app
        ListPreference(
            title = { Text(stringResource(R.string.preference_app_theme_title)) },
            summary = { Text(themeNames[themeValues.indexOf(theme.value)]) },
            state = theme,
            values = themeValues.toList(),
            valueToText = { value -> AnnotatedString(themeNames[themeValues.indexOf(value)]) }
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
