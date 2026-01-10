package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference


fun EntryProviderScope<SettingsRoute>.settingsSyncthingOptionsEntry() {
    entry<SettingsRoute.SyncthingOptions> {
        SettingsSyncthingOptionsScreen()
    }
}


@Composable
fun SettingsSyncthingOptionsScreen() {
    val deviceName = remember { mutableStateOf("Android device") }
    val usageReporting = remember { mutableStateOf(false) }

    val listenAddresses = remember { mutableStateOf("default") }
    val incomingRateLimit = remember { mutableStateOf(0) }
    val outgoingRateLimit = remember { mutableStateOf(0) }
    val natTraversal = remember { mutableStateOf(false) }
    val localDiscovery = remember { mutableStateOf(false) }
    val globalDiscovery = remember { mutableStateOf(false) }
    val relaying = remember { mutableStateOf(false) }
    val globalServers = remember { mutableStateOf("default") }

    val webGuiPort = remember { mutableStateOf(Constants.DEFAULT_WEBGUI_TCP_PORT) }
    val webGuiRemoteAccess = remember { mutableStateOf(false) }
    val webGuiUsername = remember { mutableStateOf("syncthing") }
    val webGuiPassword = remember { mutableStateOf("") }

    val crashReporting = remember { mutableStateOf(true) }


    SettingsScaffold(
        title = stringResource(R.string.category_syncthing_options),
        description = stringResource(R.string.category_syncthing_options_summary),
    ) {
        PreferenceCategory(
            title = { Text(stringResource(R.string.category_general)) },
        )
        TextFieldPreference(
            title = { Text(stringResource(R.string.device_name)) },
            summary = { Text(deviceName.value) },
            state = deviceName,
            textToValue = { it },
        )
        Preference(
            title = { Text(stringResource(R.string.syncthing_api_key)) },
            summary = { Text("TODO: api key") },
            onClick = { /* TODO: copy to clipboard */ }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.usage_reporting)) },
            state = usageReporting,
        )

        PreferenceCategory(
            title = { Text(stringResource(R.string.category_connections)) },
        )
        TextFieldPreference(
            title = { Text(stringResource(R.string.listen_address)) },
            summary = { Text(listenAddresses.value) },
            state = listenAddresses,
            textToValue = { it },
        )
        TextFieldPreference(
            title = { Text(stringResource(R.string.max_recv_kbps)) },
            summary = { Text(incomingRateLimit.value.toString()) },
            state = incomingRateLimit,
            textToValue = { it.toIntOrNull() ?: 0 },
        )
        TextFieldPreference(
            title = { Text(stringResource(R.string.max_send_kbps)) },
            summary = { Text(outgoingRateLimit.value.toString()) },
            state = outgoingRateLimit,
            textToValue = { it.toIntOrNull() ?: 0 },
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.enable_nat_traversal)) },
            state = natTraversal,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.local_announce_enabled)) },
            state = localDiscovery,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.global_announce_enabled)) },
            state = globalDiscovery,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.enable_relaying)) },
            state = relaying,
        )
        TextFieldPreference(
            title = { Text(stringResource(R.string.global_announce_server)) },
            summary = { Text(globalServers.value) },
            state = globalServers,
            textToValue = { it },
        )

        PreferenceCategory(
            title = { Text(stringResource(R.string.web_gui_title)) },
        )
        TextFieldPreference(
            title = { Text(stringResource(R.string.webui_tcp_port_title)) },
            summary = { Text(webGuiPort.value.toString()) },
            state = webGuiPort,
            textToValue = { it.toIntOrNull() ?: Constants.DEFAULT_WEBGUI_TCP_PORT },
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.webui_remote_access_title)) },
            summary = { Text(stringResource(R.string.webui_remote_access_summary)) },
            state = webGuiRemoteAccess,
        )
        TextFieldPreference(
            title = { Text(stringResource(R.string.webui_username_title)) },
            summary = { Text(webGuiUsername.value) },
            state = webGuiUsername,
            textToValue = { it },
        )
        TextFieldPreference(
            title = { Text(stringResource(R.string.webui_password_title)) },
            state = webGuiPassword,
            textToValue = { it },
        )

        PreferenceCategory(
            title = { Text(stringResource(R.string.category_advanced)) },
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.crash_reporting)) },
            state = crashReporting,
        )
        Preference(
            title = { Text(stringResource(R.string.clear_stversions_title)) },
            summary = { Text(stringResource(R.string.clear_stversions_summary)) },
            onClick = { /* TODO: show alert and clear folders */ }
        )
        Preference(
            title = { Text(stringResource(R.string.download_support_bundle_title)) },
            onClick = { /* TODO: show alert and download support bundle */ }
        )
        Preference(
            title = { Text(stringResource(R.string.undo_ignored_devices_folders_title)) },
            onClick = { /* TODO: show alert and undo ignored devices and folders */ }
        )
    }
}
