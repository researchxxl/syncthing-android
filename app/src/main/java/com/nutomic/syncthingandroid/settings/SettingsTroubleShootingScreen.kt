package com.nutomic.syncthingandroid.settings

import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.activities.LogActivity
import me.zhanghai.compose.preference.MultiSelectListPreference
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference


fun EntryProviderScope<SettingsRoute>.settingsTroubleshootingEntry() {
    entry<SettingsRoute.Troubleshooting> {
        SettingsTroubleshootingScreen()
    }
}


@Composable
fun SettingsTroubleshootingScreen() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val issueTrackerUrl = stringResource(R.string.issue_tracker_url)

    val verboseLog = remember { mutableStateOf(false) }
    val stTraceOptions = remember { mutableStateOf(setOf<String>()) }
    val envVars = remember { mutableStateOf("") }

    SettingsScaffold(
        title = stringResource(R.string.category_debug),
    ) {
        Preference(
            title = { Text(stringResource(R.string.report_issue_title)) },
            summary = { Text(stringResource(R.string.open_issue_tracker_summary, issueTrackerUrl)) },
            onClick = { uriHandler.openUri(issueTrackerUrl) }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.verbose_log_title)) },
            summary = { Text(stringResource(R.string.verbose_log_summary)) },
            state = verboseLog,
        )
        Preference(
            title = { Text(stringResource(R.string.open_log)) },
            summary = { Text(stringResource(R.string.open_log_summary)) },
            onClick = {
                val intent = Intent(context, LogActivity::class.java)
                context.startActivity(intent)
            }
        )
        MultiSelectListPreference(
            title = { Text(stringResource(R.string.sttrace_title)) },
            state = stTraceOptions,
            // TODO: values according to com.nutomic.syncthingandroid.views.SttracePreference.getDebugFacilities
            values = listOf("beacon", "config", "connections", "db", "dialer", "discover"),
        )
        TextFieldPreference(
            title = { Text(stringResource(R.string.environment_variables)) },
            state = envVars,
            textToValue = { it }
        )
        Preference(
            title = { Text(stringResource(R.string.st_reset_database_title)) },
            onClick = {
                // TODO: show alert and call reset database
            }
        )
        Preference(
            title = { Text(stringResource(R.string.st_reset_deltas_title)) },
            onClick = {
                // TODO: show alert and call reset deltas
            }
        )
    }
}
