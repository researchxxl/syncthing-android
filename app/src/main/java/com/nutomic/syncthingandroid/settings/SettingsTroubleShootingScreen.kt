package com.nutomic.syncthingandroid.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.activities.LogActivity
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.SyncthingService
import me.zhanghai.compose.preference.MultiSelectListPreference
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import me.zhanghai.compose.preference.rememberPreferenceState


fun EntryProviderScope<SettingsRoute>.settingsTroubleshootingEntry() {
    entry<SettingsRoute.Troubleshooting> {
        SettingsTroubleshootingScreen()
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTroubleshootingScreen() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val issueTrackerUrl = stringResource(R.string.issue_tracker_url)

    val verboseLog = rememberPreferenceState(Constants.PREF_VERBOSE_LOG, false)
    val stTraceOptions by rememberPreferenceState(Constants.PREF_DEBUG_FACILITIES_AVAILABLE, getFallbackDebugFacilities())
    val stTraceSelection = rememberPreferenceState(Constants.PREF_DEBUG_FACILITIES_ENABLED, setOf<String>())
    val envVars = rememberPreferenceState(Constants.PREF_ENVIRONMENT_VARIABLES, "")

    var showResetDeltaConfirmation by remember { mutableStateOf(false) }
    var showResetDbConfirmation by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.category_debug),
    ) {
        Preference(
            title = { Text(stringResource(R.string.report_issue_title)) },
            summary = { Text(stringResource(R.string.open_issue_tracker_summary, issueTrackerUrl)) },
            onClick = { uriHandler.openUri(issueTrackerUrl) }
        )
        // TODO: show dialog for app restart for toggling verbose logs
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

        val selectedValues = stTraceSelection.value.intersect(stTraceOptions)
        MultiSelectListPreference(
            title = { Text(stringResource(R.string.sttrace_title)) },
            summary = { Text(stTraceSelection.value.joinToString()) },
            value = selectedValues,
            onValueChange = { stTraceSelection.value = it },
            values = stTraceOptions.sorted(),
        )

        TextFieldPreference(
            title = { Text(stringResource(R.string.environment_variables)) },
            state = envVars,
            textToValue = { validateEnvVars(it, context) }
        )

        Preference(
            title = { Text(stringResource(R.string.st_reset_database_title)) },
            onClick = { showResetDbConfirmation = true }
        )
        if (showResetDbConfirmation) {
            AlertDialog(
                title = { Text(stringResource(R.string.st_reset_database_title)) },
                text = { Text(stringResource(R.string.st_reset_database_question)) },
                onDismissRequest = { showResetDbConfirmation = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetDbConfirmation = false
                            val intent = Intent(context, SyncthingService::class.java).apply {
                                action = SyncthingService.ACTION_RESET_DATABASE
                            }
                            context.startService(intent)
                            Toast.makeText(context, R.string.st_reset_database_done, Toast.LENGTH_LONG).show()
                        }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showResetDbConfirmation = false }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        Preference(
            title = { Text(stringResource(R.string.st_reset_deltas_title)) },
            onClick = { showResetDeltaConfirmation = true }
        )
        if (showResetDeltaConfirmation) {
            AlertDialog(
                title = { Text(stringResource(R.string.st_reset_deltas_title)) },
                text = { Text(stringResource(R.string.st_reset_deltas_question)) },
                onDismissRequest = { showResetDeltaConfirmation = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetDeltaConfirmation = false
                            val intent = Intent(context, SyncthingService::class.java).apply {
                                action = SyncthingService.ACTION_RESET_DELTAS
                            }
                            context.startService(intent)
                            Toast.makeText(context, R.string.st_reset_deltas_done, Toast.LENGTH_LONG).show()
                        }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showResetDeltaConfirmation = false }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}


private fun validateEnvVars(input: String, context: Context): String? {
    if (input.isEmpty()) {
        return input
    }

    val isValid = input.split(" ").all { pair ->
        pair.split("=", limit = 2).size == 2
    }

    if (isValid) {
        return input
    }

    // Found an invalid "VAR=VALUE" pair.
    Toast.makeText(context, R.string.toast_invalid_environment_variables, Toast.LENGTH_SHORT).show()
    return null
}

// Syncthing v0.14.47 debug facilities.
private fun getFallbackDebugFacilities(): Set<String> = setOf(
    "beacon",
    "config",
    "connections",
    "db",
    "dialer",
    "discover",
    "events",
    "fs",
    "http",
    "main",
    "model",
    "nat",
    "pmp",
    "protocol",
    "scanner",
    "sha256",
    "stats",
    "sync",
    "upgrade",
    "upnp",
    "versioner",
    "walkfs",
    "watchaggregator",
)
