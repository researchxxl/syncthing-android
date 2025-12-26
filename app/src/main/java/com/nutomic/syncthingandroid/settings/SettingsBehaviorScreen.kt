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
fun EntryProviderScope<SettingsRoute>.settingsBehaviorEntry(backstack: NavBackStack<SettingsRoute>) {
    entry<SettingsRoute.Behavior>(
        metadata = ListDetailSceneStrategy.detailPane()
    ) {
        SettingsBehaviorScreen(onBack = { backstack.removeLastOrNull() })
    }
}


@Composable
fun SettingsBehaviorScreen(onBack: () -> Unit = {}) {
    SettingsScaffold(
        title = stringResource(R.string.category_behaviour),
        onBack = onBack,
    ) {
        Text(stringResource(R.string.category_behaviour))
    }
}
