package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R


fun EntryProviderScope<SettingsRoute>.settingsSyncthingOptionsEntry() {
    entry<SettingsRoute.SyncthingOptions> {
        SettingsSyncthingOptionsScreen()
    }
}


@Composable
fun SettingsSyncthingOptionsScreen() {
    SettingsScaffold(
        title = stringResource(R.string.category_syncthing_options),
        description = stringResource(R.string.category_syncthing_options_summary),
    ) {
        Text(stringResource(R.string.category_syncthing_options))
    }
}
