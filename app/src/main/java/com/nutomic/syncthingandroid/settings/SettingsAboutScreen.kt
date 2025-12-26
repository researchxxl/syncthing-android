package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R


fun EntryProviderScope<SettingsRoute>.settingsAboutEntry() {
    entry<SettingsRoute.About> {
        SettingsAboutScreen()
    }
}


@Composable
fun SettingsAboutScreen() {
    SettingsScaffold(
        title = stringResource(R.string.category_backup),
    ) {
        Text(stringResource(R.string.category_backup))
    }
}
