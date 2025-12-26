package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R


fun EntryProviderScope<SettingsRoute>.settingsRunConditionsEntry() {
    entry<SettingsRoute.RunConditions> {
        SettingsRunConditionsScreen()
    }
}


@Composable
fun SettingsRunConditionsScreen() {
    SettingsScaffold(
        title = stringResource(R.string.run_conditions_title),
        description = stringResource(R.string.run_conditions_summary),
    ) {
        Text(stringResource(R.string.run_conditions_title))
    }
}
