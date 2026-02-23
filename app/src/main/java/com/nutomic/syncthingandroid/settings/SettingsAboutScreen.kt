package com.nutomic.syncthingandroid.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.util.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.Preference


private const val TAG = "SettingsAboutScreen"

fun EntryProviderScope<SettingsRoute>.settingsAboutEntry() {
    entry<SettingsRoute.About> {
        SettingsAboutScreen()
    }
}


@Composable
fun SettingsAboutScreen() {
    val context = LocalContext.current
    val navigator = LocalSettingsNavigator.current
    val uriHandler = LocalUriHandler.current
    val stService = LocalSyncthingService.current
    val stServiceTick = LocalServiceUpdateTick.current

    val loading = stringResource(R.string.state_loading)
    val unknown = stringResource(R.string.state_unknown)

    val state by produceState(initialValue = AboutState(
            appVersion = loading,
            coreVersion = loading,
            dbSize = loading,
            fileLimit = loading,
        ), stService, stServiceTick) {
        value = withContext(Dispatchers.IO) {
            AboutState(
                appVersion = getAppVersion(context) ?: unknown,
                coreVersion = stService?.api?.version ?: unknown,
                dbSize = getDatabaseSize(context) ?: unknown,
                fileLimit = getOpenFileLimit() ?: unknown,
            )
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.category_about),
    ) {
        item {
            Preference(
                title = { Text(stringResource(R.string.app_version_title)) },
                summary = { Text(state.appVersion) },
            )
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.syncthing_version_title)) },
                summary = { Text(state.coreVersion) },
            )
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.syncthing_database_size)) },
                summary = { Text(state.dbSize) },
            )
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.os_open_file_limit)) },
                summary = { Text(state.fileLimit) },
            )
        }
        item {
            val stForumUri = stringResource(R.string.syncthing_forum_url)
            Preference(
                title = { Text(stringResource(R.string.syncthing_forum_title)) },
                summary = { Text(stringResource(R.string.syncthing_forum_summary)) },
                onClick = { uriHandler.openUri(stForumUri) },
            )
        }
        item {
            val stPrivacyPolicyUri = stringResource(R.string.privacy_policy_url)
            Preference(
                title = { Text(stringResource(R.string.privacy_title)) },
                summary = { Text(stringResource(R.string.privacy_summary)) },
                onClick = { uriHandler.openUri(stPrivacyPolicyUri) },
            )
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.open_source_licenses_title)) },
                summary = { Text(stringResource(R.string.open_source_licenses_summary)) },
                onClick = { navigator.navigateTo(SettingsRoute.Licenses) },
            )
        }
    }
}

data class AboutState(
    val appVersion: String = "",
    val coreVersion: String = "",
    val dbSize: String = "",
    val fileLimit: String = ""
)

private fun getAppVersion(context: Context): String? {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        "v${packageInfo.versionName}"
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e(TAG, "Failed to get app version name")
        null
    }
}

private fun getOpenFileLimit(): String? {
    val shellCommand = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        "/system/bin/ulimit -n"
    else
        "ulimit -n"

    val result = Util.runShellCommandGetOutput(shellCommand)
    return if (result.isNullOrBlank()) null else result.trim()
}

private fun getDatabaseSize(context: Context): String? {
    val dbPath = Constants.getIndexDbFolder(context).absolutePath
    val result = Util.runShellCommandGetOutput("/system/bin/du -sh $dbPath")

    if (result.isNullOrBlank()) {
        return null
    }

    // Split by whitespace and grab the first part (the size)
    val resultParts = result.trim().split(Regex("\\s+"))
    return resultParts.firstOrNull()
}
