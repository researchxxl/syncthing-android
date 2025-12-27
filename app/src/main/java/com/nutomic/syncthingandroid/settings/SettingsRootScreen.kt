package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import me.zhanghai.compose.preference.Preference

fun EntryProviderScope<SettingsRoute>.settingsRootEntry() {
    entry<SettingsRoute.Root> {
        SettingsRootScreen()
    }
}

@Composable
fun SettingsRootScreen() {
    val navigator = LocalSettingsNavigator.current

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
    ) {
        Preference(
            title = { Text(stringResource(R.string.run_conditions_title)) },
            summary = { Text(stringResource(R.string.run_conditions_summary)) },
            onClick = { navigator.navigateTo(SettingsRoute.RunConditions) },
        )
        Preference(
            title = { Text(stringResource(R.string.category_user_interface)) },
            onClick = { navigator.navigateTo(SettingsRoute.UserInterface) },
        )
        Preference(
            title = { Text(stringResource(R.string.category_behaviour)) },
            onClick = { navigator.navigateTo(SettingsRoute.Behavior) },
        )
        Preference(
            title = { Text(stringResource(R.string.category_syncthing_options)) },
            summary = { Text(stringResource(R.string.category_syncthing_options_summary)) },
            onClick = { navigator.navigateTo(SettingsRoute.SyncthingOptions) },
        )
        Preference(
            title = { Text(stringResource(R.string.category_backup)) },
            onClick = { navigator.navigateTo(SettingsRoute.ImportExport) },
        )
        Preference(
            title = { Text(stringResource(R.string.category_debug)) },
            onClick = { navigator.navigateTo(SettingsRoute.Troubleshooting) },
        )
        Preference(
            title = { Text(stringResource(R.string.category_experimental)) },
            onClick = { navigator.navigateTo(SettingsRoute.Experimental) },
        )
        Preference(
            title = { Text(stringResource(R.string.category_about)) },
            onClick = { navigator.navigateTo(SettingsRoute.About) },
        )
    }
}
