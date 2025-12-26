package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import com.nutomic.syncthingandroid.R

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<SettingsRoute>.settingsRootEntry(backstack: NavBackStack<SettingsRoute>) {
    entry<SettingsRoute.Root>(
        metadata = ListDetailSceneStrategy.listPane()
    ) {
        SettingsRootScreen(backstack)
    }
}

@Composable
fun SettingsRootScreen(backstack: NavBackStack<SettingsRoute>) {
    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBack = { backstack.removeLastOrNull() }
    ) {
        TextButton(onClick = { backstack.add(SettingsRoute.RunConditions) }) {
            Text("RunConditions")
        }
        TextButton(onClick = { backstack.add(SettingsRoute.UserInterface) }) {
            Text("UserInterface")
        }
        TextButton(onClick = { backstack.add(SettingsRoute.Behavior) }) {
            Text("Behavior")
        }
        TextButton(onClick = { backstack.add(SettingsRoute.SyncthingOptions) }) {
            Text("SyncthingOptions")
        }
        TextButton(onClick = { backstack.add(SettingsRoute.ImportExport) }) {
            Text("ImportExport")
        }
        TextButton(onClick = { backstack.add(SettingsRoute.Troubleshooting) }) {
            Text("Troubleshooting")
        }
        TextButton(onClick = { backstack.add(SettingsRoute.Experimental) }) {
            Text("Experimental")
        }
        TextButton(onClick = { backstack.add(SettingsRoute.About) }) {
            Text("About")
        }
    }
}
