package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isSensitiveData
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.TextFieldPreference
import me.zhanghai.compose.preference.rememberPreferenceState


fun EntryProviderScope<SettingsRoute>.settingsImportExportEntry() {
    entry<SettingsRoute.ImportExport> {
        SettingsImportExportScreen()
    }
}


@Composable
fun SettingsImportExportScreen() {
    val backupPath = rememberPreferenceState(Constants.PREF_BACKUP_REL_PATH_TO_ZIP,"backups/syncthing/config.zip")
    val password = rememberPreferenceState(Constants.PREF_BACKUP_PASSWORD, "")

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
            summary = { Text(backupPath.value) },
            state = backupPath,
            textToValue = { it },
        )

        val passwordSummary = if (password.value.isBlank())
            stringResource(R.string.backup_password_not_set)
        else
            stringResource(R.string.backup_password_set_masked)
        TextFieldPreference(
            title = { Text(stringResource(R.string.backup_password_title)) },
            summary = { Text(passwordSummary) },
            modifier = Modifier.semantics {
                password()
                isSensitiveData = true
            },
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
