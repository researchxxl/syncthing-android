package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.TextFieldPreference


fun EntryProviderScope<SettingsRoute>.settingsImportExportEntry() {
    entry<SettingsRoute.ImportExport> {
        SettingsImportExportScreen()
    }
}


@Composable
fun SettingsImportExportScreen() {
    val backupPath = remember { mutableStateOf("backups/syncthing/config.zip") }
    val password = remember { mutableStateOf("") }

    SettingsScaffold(
        title = stringResource(R.string.category_backup),
    ) {
        PreferenceCategory(
            title = { Text(stringResource(R.string.preference_category_explanation)) },
        )
        Preference(
            title = { Text(stringResource(R.string.backup_password_summary)) },
        )

        PreferenceCategory(
            title = { Text(stringResource(R.string.preference_category_prepare)) },
        )
        TextFieldPreference(
            title = { Text(stringResource(R.string.backup_rel_path_to_zip)) },
            state = backupPath,
            textToValue = { it },
        )
        // TODO: new preference bc text field pref doesn't support widget container for eye button
        val passwordSummary = if (password.value.isBlank())
            stringResource(R.string.backup_password_not_set)
        else
            stringResource(R.string.backup_password_set_masked)
        TextFieldPreference(
            title = { Text(stringResource(R.string.backup_password_title)) },
            summary = { Text(passwordSummary) },
            state = password,
            textToValue = { it },
        )

        PreferenceCategory(
            title = { Text(stringResource(R.string.preference_category_actions)) },
        )
        Preference(
            title = { Text(stringResource(R.string.export_config)) },
            onClick = {
                // TODO: export config
            }
        )
        Preference(
            title = { Text(stringResource(R.string.import_config)) },
            onClick = {
                // TODO: import config
            }
        )
    }
}
