package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R

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
        TextButton(onClick = { navigator.navigateTo(SettingsRoute.RunConditions) }) {
            Text("RunConditions")
        }
        TextButton(onClick = { navigator.navigateTo(SettingsRoute.UserInterface) }) {
            Text("UserInterface")
        }
        TextButton(onClick = { navigator.navigateTo(SettingsRoute.Behavior) }) {
            Text("Behavior")
        }
        TextButton(onClick = { navigator.navigateTo(SettingsRoute.SyncthingOptions) }) {
            Text("SyncthingOptions")
        }
        TextButton(onClick = { navigator.navigateTo(SettingsRoute.ImportExport) }) {
            Text("ImportExport")
        }
        TextButton(onClick = { navigator.navigateTo(SettingsRoute.Troubleshooting) }) {
            Text("Troubleshooting")
        }
        TextButton(onClick = { navigator.navigateTo(SettingsRoute.Experimental) }) {
            Text("Experimental")
        }
        TextButton(onClick = { navigator.navigateTo(SettingsRoute.About) }) {
            Text("About")
        }
    }
}
