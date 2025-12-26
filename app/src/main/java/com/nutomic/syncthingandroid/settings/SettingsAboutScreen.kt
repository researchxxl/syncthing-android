package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import com.nutomic.syncthingandroid.R


@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<SettingsRoute>.settingsAboutEntry(backstack: NavBackStack<SettingsRoute>) {
    entry<SettingsRoute.About>(
        metadata = ListDetailSceneStrategy.detailPane()
    ) {
        SettingsAboutScreen(onBack = { backstack.removeLastOrNull() })
    }
}


@Composable
fun SettingsAboutScreen(onBack: () -> Unit = {}) {
    SettingsScaffold(
        title = stringResource(R.string.category_backup),
        onBack = onBack,
    ) {
        Text(stringResource(R.string.category_backup))
    }
}
