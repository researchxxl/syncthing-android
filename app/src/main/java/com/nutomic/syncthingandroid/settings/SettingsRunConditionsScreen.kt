package com.nutomic.syncthingandroid.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nutomic.syncthingandroid.R

@Composable
fun SettingsRunConditionsScreen() {
    SettingsScaffold(
        title = stringResource(R.string.run_conditions_title),
        description = stringResource(R.string.run_conditions_summary)
    )
}