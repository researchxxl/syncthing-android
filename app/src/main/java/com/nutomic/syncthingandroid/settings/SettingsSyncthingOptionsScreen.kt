package com.nutomic.syncthingandroid.settings

import android.content.ClipData
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isSensitiveData
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.navigation3.runtime.EntryProviderScope
import com.google.common.base.Splitter
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.model.Folder
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.RestApi
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.MapPreferences
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.Preferences
import me.zhanghai.compose.preference.ProvidePreferenceFlow
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import me.zhanghai.compose.preference.rememberPreferenceState
import org.mindrot.jbcrypt.BCrypt
import java.io.File
import kotlin.time.Duration.Companion.seconds

private const val TAG = "SettingsSyncthingOptionsScreen"

fun EntryProviderScope<SettingsRoute>.settingsSyncthingOptionsEntry() {
    entry<SettingsRoute.SyncthingOptions> {
        SettingsSyncthingOptionsScreen()
    }
}

private object Keys {
    const val DEVICE_NAME = "device_name"
    const val API_KEY = "api_key"
    const val USAGE_REPORTING = "usage_reporting"

    const val LISTEN_ADDRESSES = "listen_addresses"
    const val INCOMING_RATE_LIMIT = "incoming_rate_limit"
    const val OUTGOING_RATE_LIMIT = "outgoing_rate_limit"
    const val NAT_TRAVERSAL = "nat_traversal"
    const val LOCAL_DISCOVERY = "local_discovery"
    const val GLOBAL_DISCOVERY = "global_discovery"
    const val RELAYING = "relaying"
    const val GLOBAL_SERVERS = "global_servers"

    const val WEB_GUI_PORT = "web_gui_port"
    const val WEB_GUI_REMOTE_ACCESS = "web_gui_remote_access"
    const val WEB_GUI_USERNAME = "web_gui_username"
    const val WEB_GUI_PASSWORD = Constants.PREF_WEBUI_PASSWORD

    const val CRASH_REPORTING = "crash_reporting"
}

private const val BIND_ALL = "0.0.0.0"
private const val BIND_LOCALHOST = "127.0.0.1"


@OptIn(FlowPreview::class)
@Composable
fun SettingsSyncthingOptionsScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val stService = LocalSyncthingService.current
    val stServiceTick = LocalServiceUpdateTick.current
    val scope = rememberCoroutineScope()

    val sharedPrefWebGuiPassword = rememberPreferenceState(Constants.PREF_WEBUI_PASSWORD, "")

    val isStServiceActive by remember(stService, stServiceTick) { derivedStateOf {
        stService?.currentState == SyncthingService.State.ACTIVE && stService.api?.isConfigLoaded == true
    } }
    val apiPrefFlow: MutableStateFlow<Preferences> = remember {
        MutableStateFlow(stService?.api?.preferences ?: MapPreferences())
    }

    LaunchedEffect(stService?.api, isStServiceActive) {
        val currentApi = stService?.api
        if (currentApi != null && isStServiceActive) {
            apiPrefFlow.value = currentApi.preferences
            apiPrefFlow
                .drop(1)
                .debounce(0.5.seconds)
                .collect { currentApi.preferences = it }
        }
    }


    ProvidePreferenceFlow(apiPrefFlow) {
        val deviceName = rememberPreferenceState(Keys.DEVICE_NAME, "")
        val apiKey by rememberPreferenceState(Keys.API_KEY, "")
        val usageReporting = rememberPreferenceState(Keys.USAGE_REPORTING, false)

        val listenAddresses = rememberPreferenceState(Keys.LISTEN_ADDRESSES, "default")
        val incomingRateLimit = rememberPreferenceState(Keys.INCOMING_RATE_LIMIT, 0)
        val outgoingRateLimit = rememberPreferenceState(Keys.OUTGOING_RATE_LIMIT, 0)
        val natTraversal = rememberPreferenceState(Keys.NAT_TRAVERSAL, false)
        val localDiscovery = rememberPreferenceState(Keys.LOCAL_DISCOVERY, false)
        val globalDiscovery = rememberPreferenceState(Keys.GLOBAL_DISCOVERY, false)
        val relaying = rememberPreferenceState(Keys.RELAYING, false)
        val globalServers = rememberPreferenceState(Keys.GLOBAL_SERVERS, "default")

        val webGuiPort = rememberPreferenceState(Keys.WEB_GUI_PORT, Constants.DEFAULT_WEBGUI_TCP_PORT)
        val webGuiRemoteAccess = rememberPreferenceState(Keys.WEB_GUI_REMOTE_ACCESS, false)
        val webGuiUsername = rememberPreferenceState(Keys.WEB_GUI_USERNAME, "syncthing")
        val webGuiPassword = rememberPreferenceState(Keys.WEB_GUI_PASSWORD, sharedPrefWebGuiPassword.value)

        val crashReporting = rememberPreferenceState(Keys.CRASH_REPORTING, true)


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
                enabled = isStServiceActive,
            )
            val apiKeyTitle = stringResource(R.string.syncthing_api_key)
            Preference(
                title = { Text(apiKeyTitle) },
                summary = { Text(apiKey) },
                modifier = Modifier.semantics(true) {
                    isSensitiveData = true
                    password()
                },
                onClick = {
                    val clipData = ClipData.newPlainText(apiKeyTitle, apiKey).toClipEntry()
                    scope.launch {
                        clipboard.setClipEntry(clipData)
                        // Android 13+ shows a system confirmation automatically
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(
                                context,
                                R.string.api_key_copied_to_clipboard,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                enabled = isStServiceActive,
            )
            SwitchPreference(
                title = { Text(stringResource(R.string.usage_reporting)) },
                state = usageReporting,
                enabled = isStServiceActive,
            )

            PreferenceCategory(
                title = { Text(stringResource(R.string.category_connections)) },
            )
            TextFieldPreference(
                title = { Text(stringResource(R.string.listen_address)) },
                summary = { Text(listenAddresses.value) },
                state = listenAddresses,
                textToValue = { it },
                enabled = isStServiceActive,
            )
            val rateLimitError = stringResource(R.string.invalid_integer_value, 0, Int.MAX_VALUE)
            TextFieldPreference(
                title = { Text(stringResource(R.string.max_recv_kbps)) },
                summary = { Text(incomingRateLimit.value.toString()) },
                state = incomingRateLimit,
                textToValue = {
                    val newVal = it.toIntOrNull()
                    if (newVal == null) {
                        Toast.makeText(context, rateLimitError, Toast.LENGTH_LONG).show()
                        null
                    } else {
                        newVal
                    }
                },
                enabled = isStServiceActive,
            )
            TextFieldPreference(
                title = { Text(stringResource(R.string.max_send_kbps)) },
                summary = { Text(outgoingRateLimit.value.toString()) },
                state = outgoingRateLimit,
                textToValue = {
                    val newVal = it.toIntOrNull()
                    if (newVal == null) {
                        Toast.makeText(context, rateLimitError, Toast.LENGTH_LONG).show()
                        null
                    } else {
                        newVal
                    }
                },
                enabled = isStServiceActive,
            )
            SwitchPreference(
                title = { Text(stringResource(R.string.enable_nat_traversal)) },
                state = natTraversal,
                enabled = isStServiceActive,
            )
            SwitchPreference(
                title = { Text(stringResource(R.string.local_announce_enabled)) },
                state = localDiscovery,
                enabled = isStServiceActive,
            )
            SwitchPreference(
                title = { Text(stringResource(R.string.global_announce_enabled)) },
                state = globalDiscovery,
                enabled = isStServiceActive,
            )
            SwitchPreference(
                title = { Text(stringResource(R.string.enable_relaying)) },
                state = relaying,
                enabled = isStServiceActive,
            )
            TextFieldPreference(
                title = { Text(stringResource(R.string.global_announce_server)) },
                summary = { Text(globalServers.value) },
                state = globalServers,
                textToValue = { it },
                enabled = isStServiceActive,
            )

            PreferenceCategory(
                title = { Text(stringResource(R.string.web_gui_title)) },
            )
            val portError = stringResource(R.string.invalid_port_number, 1024, 65535)
            TextFieldPreference(
                title = { Text(stringResource(R.string.webui_tcp_port_title)) },
                summary = { Text(webGuiPort.value.toString()) },
                state = webGuiPort,
                textToValue = {
                    val newValue = it.toIntOrNull()
                    if (newValue == null || newValue !in 1024..65535) {
                        Toast.makeText(context, portError, Toast.LENGTH_LONG).show()
                        null
                    } else {
                        newValue
                    }
                },
                enabled = isStServiceActive,
            )
            SwitchPreference(
                title = { Text(stringResource(R.string.webui_remote_access_title)) },
                summary = { Text(stringResource(R.string.webui_remote_access_summary)) },
                state = webGuiRemoteAccess,
                enabled = isStServiceActive,
            )
            TextFieldPreference(
                title = { Text(stringResource(R.string.webui_username_title)) },
                summary = { Text(webGuiUsername.value) },
                state = webGuiUsername,
                textToValue = { it },
                enabled = isStServiceActive,
            )
            TextFieldPreference(
                title = { Text(stringResource(R.string.webui_password_title)) },
                value = webGuiPassword.value,
                onValueChange = {
                    webGuiPassword.value = it
                    sharedPrefWebGuiPassword.value = it
                },
                textToValue = { it },
                enabled = isStServiceActive,
            )

            PreferenceCategory(
                title = { Text(stringResource(R.string.category_advanced)) },
            )
            SwitchPreference(
                title = { Text(stringResource(R.string.crash_reporting)) },
                state = crashReporting,
                enabled = isStServiceActive,
            )
            ClearStVersionPreference(
                stService = stService,
                enabled = isStServiceActive,
            )
            SupportBundlePreference(
                stService = stService,
                enabled = isStServiceActive,
            )
            UndoIgnoredDevicesFoldersPreference(
                stService = stService,
                enabled = isStServiceActive,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearStVersionPreference(
    stService: SyncthingService?,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val scope = LocalActivityScope.current
    var showAlert by rememberSaveable { mutableStateOf(false) }

    Preference(
        title = { Text(stringResource(R.string.clear_stversions_title)) },
        summary = { Text(stringResource(R.string.clear_stversions_summary)) },
        onClick = { showAlert = true },
        enabled = enabled,
    )
    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text(stringResource(R.string.clear_stversions_title)) },
            text = { Text(stringResource(R.string.clear_stversions_question)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        stService?.api?.let { api ->
                            scope.launch(Dispatchers.IO) {
                                val folders = api.folders
                                if (clearStVersions(folders)) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            R.string.clear_stversions_done,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                                showAlert = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAlert = false }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UndoIgnoredDevicesFoldersPreference(
    stService: SyncthingService?,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val scope = LocalActivityScope.current
    var showAlert by rememberSaveable { mutableStateOf(false) }

    Preference(
        title = { Text(stringResource(R.string.undo_ignored_devices_folders_title)) },
        onClick = { showAlert = true },
        enabled = enabled,
    )
    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text(stringResource(R.string.undo_ignored_devices_folders_title)) },
            text = { Text(stringResource(R.string.undo_ignored_devices_folders_question)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        stService?.api?.let { api ->
                            scope.launch(Dispatchers.IO) {
                                api.undoIgnoredDevicesAndFolders()
                                api.sendConfig()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        R.string.undo_ignored_devices_folders_done,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                showAlert = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAlert = false }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}


@Composable
private fun SupportBundlePreference(
    stService: SyncthingService?,
    enabled: Boolean,
) {
    val scope = LocalActivityScope.current
    var supportBundleState by remember { mutableStateOf(SupportBundleDownloadState.INIT) }
    var supportBundleFileName by remember { mutableStateOf("") }

    Preference(
        title = { Text(stringResource(R.string.download_support_bundle_title)) },
        summary = {
            when (supportBundleState) {
                SupportBundleDownloadState.INIT -> {}
                SupportBundleDownloadState.DOWNLOADING -> {
                    Text(stringResource(R.string.download_support_bundle_in_progress))
                }
                SupportBundleDownloadState.SUCCESS -> {
                    Text(stringResource(R.string.download_support_bundle_succeeded, supportBundleFileName))
                }
                SupportBundleDownloadState.FAILED -> {
                    Text(stringResource(R.string.download_support_bundle_failed))
                }
            }
        },
        onClick = {
            stService?.api?.let { api ->
                supportBundleState = SupportBundleDownloadState.DOWNLOADING
                scope.launch(Dispatchers.IO) {
                    val deviceName = api.localDevice.displayName
                    val downloadDir = FileUtils.getExternalStorageDownloadsDirectory()
                    val targetFileName = "$downloadDir/syncthing-support-bundle_$deviceName.zip"
                    supportBundleFileName = targetFileName
                    val targetFile = File(targetFileName)
                    api.downloadSupportBundle(targetFile) { success ->
                        supportBundleState = if (success)
                            SupportBundleDownloadState.SUCCESS
                        else
                            SupportBundleDownloadState.FAILED
                    }
                }
            }
        },
        enabled = enabled && supportBundleState != SupportBundleDownloadState.DOWNLOADING,
    )
}

private enum class SupportBundleDownloadState {
    INIT, DOWNLOADING, SUCCESS, FAILED
}

private var RestApi.preferences: Preferences
    get() {
        if (!this.isConfigLoaded) return MapPreferences()

        val values = mutableMapOf<String, Any>()
        values[Keys.DEVICE_NAME] = this.localDevice.name
        values[Keys.API_KEY] = this.apiKey
        values[Keys.USAGE_REPORTING] = this.isUsageReportingAccepted

        this.options?.let { options ->
            values[Keys.LISTEN_ADDRESSES] = options.listenAddresses.joinToString()
            values[Keys.INCOMING_RATE_LIMIT] = options.maxRecvKbps
            values[Keys.OUTGOING_RATE_LIMIT] = options.maxSendKbps
            values[Keys.NAT_TRAVERSAL] = options.natEnabled
            values[Keys.LOCAL_DISCOVERY] = options.localAnnounceEnabled
            values[Keys.GLOBAL_DISCOVERY] = options.globalAnnounceEnabled
            values[Keys.RELAYING] = options.relaysEnabled
            values[Keys.GLOBAL_SERVERS] = options.globalAnnounceServers.joinToString()
            values[Keys.CRASH_REPORTING] = options.crashReportingEnabled
        }

        this.gui?.let { gui ->
            values[Keys.WEB_GUI_PORT] = gui.bindPort.toIntOrNull() ?: Constants.DEFAULT_WEBGUI_TCP_PORT
            values[Keys.WEB_GUI_REMOTE_ACCESS] = gui.bindAddress != BIND_LOCALHOST
            values[Keys.WEB_GUI_USERNAME] = gui.user
            // use password saved in shared preference only
            // values[Keys.WEB_GUI_PASSWORD] = gui.password
        }

        return MapPreferences(values)
    }
    set(value) {
        val splitter = Splitter.on(",").trimResults().omitEmptyStrings()
        val valueMap = value.asMap()

        // update calls that change internal state of RestApi
        valueMap[Keys.USAGE_REPORTING]?.let { this.setUsageReporting(it as Boolean) }

        // assignments to options and gui
        val options = this.options
        val gui = this.gui
        for ((key, mapValue) in valueMap) {
            when (key) {
                Keys.LISTEN_ADDRESSES -> {
                    val addresses = splitter.splitToList(mapValue as String)
                    options.listenAddresses = addresses.toTypedArray()
                }
                Keys.INCOMING_RATE_LIMIT -> {
                    options.maxRecvKbps = mapValue as Int
                }
                Keys.OUTGOING_RATE_LIMIT -> {
                    options.maxSendKbps = mapValue as Int
                }
                Keys.NAT_TRAVERSAL -> {
                    options.natEnabled = mapValue as Boolean
                }
                Keys.LOCAL_DISCOVERY -> {
                    options.localAnnounceEnabled = mapValue as Boolean
                }
                Keys.GLOBAL_DISCOVERY -> {
                    options.globalAnnounceEnabled = mapValue as Boolean
                }
                Keys.RELAYING -> {
                    options.relaysEnabled = mapValue as Boolean
                }
                Keys.GLOBAL_SERVERS -> {
                    val servers = splitter.splitToList(mapValue as String)
                    options.globalAnnounceServers = servers.toTypedArray()
                }
                Keys.CRASH_REPORTING -> {
                    options.crashReportingEnabled = mapValue as Boolean
                }
                Keys.WEB_GUI_REMOTE_ACCESS -> {
                    val address = if (mapValue as Boolean) BIND_ALL else BIND_LOCALHOST
                    val port = (valueMap[Keys.WEB_GUI_PORT] as Int?) ?: Constants.DEFAULT_WEBGUI_TCP_PORT
                    gui.address = "$address:$port"
                }
                Keys.WEB_GUI_PORT -> {/* Ignore here, handled in WEB_GUI_REMOTE_ACCESS */}
                Keys.WEB_GUI_USERNAME -> {
                    gui.user = mapValue as String
                }
                Keys.WEB_GUI_PASSWORD -> {
                    val hashed = BCrypt.hashpw(mapValue as String, BCrypt.gensalt(4))
                    gui.password = hashed
                }
            }
        }
        // does not call sendConfig
        this.editSettings(gui, options)

        val localDevice = this.localDevice
        val deviceName = valueMap[Keys.DEVICE_NAME] as String? ?: localDevice.name
        localDevice.name = deviceName
        // this calls the sendConfig method
        this.updateDevice(localDevice)
    }

private fun clearStVersions(folders: List<Folder>): Boolean {
    for (folder in folders) {
        val dir = File(folder.path + "/" + Constants.FOLDER_NAME_STVERSIONS)
        if (dir.exists() && dir.isDirectory) {
            Log.d(TAG, "Delete dir: $dir")
            deleteContents(dir)
        }
    }
    return true
}

private fun deleteContents(dir: File) {
    val files = dir.listFiles()
    files?.let {
        for (file in it) {
            if (file.isDirectory) {
                deleteContents(file)
            }
            file.delete()
        }
    }
}
