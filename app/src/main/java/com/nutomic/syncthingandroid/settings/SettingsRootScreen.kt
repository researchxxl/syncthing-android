package com.nutomic.syncthingandroid.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavBackStack
import com.nutomic.syncthingandroid.R

@Composable
fun SettingsRootScreen(backstack: NavBackStack<SettingsRoute>) {
    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBack = { backstack.removeLastOrNull() }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
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
        }

    }
}