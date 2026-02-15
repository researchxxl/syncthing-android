package com.nutomic.syncthingandroid.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.SyncthingService
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import me.zhanghai.compose.preference.rememberPreferenceState


fun EntryProviderScope<SettingsRoute>.settingsRunConditionsEntry() {
    entry<SettingsRoute.RunConditions> {
        SettingsRunConditionsScreen()
    }
}


@Composable
fun SettingsRunConditionsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val stService = LocalSyncthingService.current

    var pendingEvaluation by remember { mutableStateOf(false) }

    var runOnWifi by rememberPreferenceState(Constants.PREF_RUN_ON_WIFI, true)
    var runOnMeteredWifi by rememberPreferenceState(Constants.PREF_RUN_ON_METERED_WIFI, false)
    var runOnSpecifiedSsid by rememberPreferenceState(Constants.PREF_USE_WIFI_SSID_WHITELIST, false)

    var runOnMobileData by rememberPreferenceState(Constants.PREF_RUN_ON_MOBILE_DATA, false)
    var runOnRoaming by rememberPreferenceState(Constants.PREF_RUN_ON_ROAMING, false)

    val powerSourceNames = stringArrayResource(R.array.power_source_entries)
    val powerSourceValues = stringArrayResource(R.array.power_source_values)
    var powerSource by rememberPreferenceState(Constants.PREF_POWER_SOURCE, powerSourceValues[0])

    var respectBatterySaving by rememberPreferenceState(Constants.PREF_RESPECT_BATTERY_SAVING, true)
    var respectMasterSync by rememberPreferenceState(Constants.PREF_RESPECT_MASTER_SYNC, false)
    var flightMode by rememberPreferenceState(Constants.PREF_RUN_IN_FLIGHT_MODE, false)

    var runScheduled by rememberPreferenceState(Constants.PREF_RUN_ON_TIME_SCHEDULE, false)
    var syncDuration by rememberPreferenceState(Constants.PREF_SYNC_DURATION_MINUTES, "5")
    var sleepInterval by rememberPreferenceState(Constants.PREF_SLEEP_INTERVAL_MINUTES, "60")


    DisposableEffect(lifecycleOwner, stService) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && pendingEvaluation && stService != null) {
                stService.evaluateRunConditions()
                pendingEvaluation = false
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            if (pendingEvaluation && stService != null) {
                stService.evaluateRunConditions()
                pendingEvaluation = false
            }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    SettingsScaffold(
        title = stringResource(R.string.run_conditions_title),
        description = stringResource(R.string.run_conditions_summary),
    ) {
        PreferenceCategory(
            title = { Text(stringResource(R.string.category_wifi)) },
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.run_on_wifi_title)) },
            summary = { Text(stringResource(R.string.run_on_wifi_summary)) },
            value = runOnWifi,
            onValueChange = {
                runOnWifi = it
                pendingEvaluation = true
            }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.run_on_metered_wifi_title)) },
            summary = { Text(stringResource(R.string.run_on_metered_wifi_summary)) },
            value = runOnMeteredWifi,
            onValueChange = {
                runOnMeteredWifi = it
                pendingEvaluation = true
            },
            enabled = runOnWifi,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.run_on_whitelisted_wifi_title)) },
            summary = { Text(stringResource(R.string.run_on_whitelisted_wifi_summary)) },
            value = runOnSpecifiedSsid,
            onValueChange = {
                runOnSpecifiedSsid = it
                pendingEvaluation = true
            },
            enabled = runOnWifi,
        )
        WifiSsidPreference(
            enabled = runOnWifi && runOnSpecifiedSsid,
            setPendingEvaluation = {
                pendingEvaluation = true
            }
        )

        PreferenceCategory(
            title = { Text(stringResource(R.string.category_mobile_data)) }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.run_on_mobile_data_title)) },
            summary = { Text(stringResource(R.string.run_on_mobile_data_summary)) },
            value = runOnMobileData,
            onValueChange = {
                runOnMobileData = it
                pendingEvaluation = true
            }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.run_on_roaming_title)) },
            summary = { Text(stringResource(R.string.run_on_roaming_summary)) },
            value = runOnRoaming,
            onValueChange = {
                runOnRoaming = it
                pendingEvaluation = true
            },
            enabled = runOnMobileData,
        )

        PreferenceCategory(
            title = { Text(stringResource(R.string.category_battery)) },
        )
        ListPreference(
            title = { Text(stringResource(R.string.power_source_title)) },
            summary = { Text(powerSourceNames[powerSourceValues.indexOf(powerSource)]) },
            value = powerSource,
            onValueChange = {
                powerSource = it
                pendingEvaluation = true
            },
            values = powerSourceValues.toList(),
            valueToText = { value ->
                AnnotatedString(powerSourceNames[powerSourceValues.indexOf(value)])
            }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.respect_battery_saving_title)) },
            summary = { Text(stringResource(R.string.respect_battery_saving_summary)) },
            value = respectBatterySaving,
            onValueChange = {
                respectBatterySaving = it
                pendingEvaluation = true
            }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.respect_master_sync_title)) },
            summary = { Text(stringResource(R.string.respect_master_sync_summary)) },
            value = respectMasterSync,
            onValueChange = {
                respectMasterSync = it
                pendingEvaluation = true
            }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.run_in_flight_mode_title)) },
            summary = { Text(stringResource(R.string.run_in_flight_mode_summary)) },
            value = flightMode,
            onValueChange = {
                flightMode = it
                pendingEvaluation = true
            }
        )

        PreferenceCategory(
            title = { Text(stringResource(R.string.category_schedule)) },
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.run_on_time_schedule_title)) },
            summary = { Text(stringResource(R.string.run_on_time_schedule_summary)) },
            value = runScheduled,
            onValueChange = {
                runScheduled = it
                pendingEvaluation = true
            }
        )
        val syncDurationError = stringResource(R.string.invalid_integer_value, 1, 1440/* 24h */)
        TextFieldPreference(
            title = { Text(stringResource(R.string.sync_duration_minutes_title)) },
            summary = { Text(stringResource(R.string.sync_duration_minutes_summary, syncDuration)) },
            value = syncDuration,
            onValueChange = {
                syncDuration = it
                pendingEvaluation = true
            },
            textToValue = { text ->
                val mins = text.toIntOrNull()
                if (mins == null || mins !in 1..1440) {
                    Toast.makeText(context, syncDurationError, Toast.LENGTH_LONG).show()
                    null
                } else {
                    text
                }
            }
        )
        val sleepIntervalError = stringResource(R.string.invalid_integer_value, 1, 30240/* 3w */)
        TextFieldPreference(
            title = { Text(stringResource(R.string.sleep_interval_minutes_title)) },
            summary = { Text(stringResource(R.string.sync_duration_minutes_summary, sleepInterval)) },
            value = sleepInterval,
            onValueChange = {
                sleepInterval = it
                pendingEvaluation = true
            },
            textToValue = { text ->
                val mins = text.toIntOrNull()
                if (mins == null || mins !in 1..30240) {
                    Toast.makeText(context, sleepIntervalError, Toast.LENGTH_LONG).show()
                    null
                } else {
                    text
                }
            }
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun WifiSsidPreference(
    enabled: Boolean,
    setPendingEvaluation: () -> Unit,
) {
    val context = LocalContext.current
    val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    var specifiedSsids by rememberPreferenceState(Constants.PREF_WIFI_SSID_WHITELIST, setOf<String>())
    var knownSsids by rememberPreferenceState(Constants.PREF_KNOWN_WIFI_SSIDS, setOf<String>())

    var showSelectionAlert by rememberSaveable { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Manifest.permission.ACCESS_FINE_LOCATION
    } else {
        Manifest.permission.ACCESS_COARSE_LOCATION
    }

    val permissionState = rememberPermissionState(permission) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                context,
                R.string.sync_only_wifi_ssids_location_permission_rejected_dialog_content,
                Toast.LENGTH_LONG
            ).show()
        } else {
            val intent = Intent(context, SyncthingService::class.java)
                .setAction(SyncthingService.ACTION_REFRESH_NETWORK_INFO)
            context.startService(intent)
        }
    }

    val isPermissionGranted = permissionState.status == PermissionStatus.Granted
    val isLocationEnabled = LocationManagerCompat.isLocationEnabled(locationManager)

    LaunchedEffect(isPermissionGranted) {
        val currentSsid = getCurrentWifiSsid(context)
        var updatedKnown = knownSsids
        var updatedSelection = specifiedSsids

        if (!currentSsid.isNullOrBlank()) {
            updatedKnown = knownSsids + currentSsid
        }
        val toRemoveSelected = specifiedSsids - updatedKnown
        if (toRemoveSelected.isNotEmpty()) {
            updatedSelection = specifiedSsids - toRemoveSelected
        }

        knownSsids = updatedKnown
        specifiedSsids =  updatedSelection
    }

    val specifiedSsidSummary = if (specifiedSsids.isNotEmpty())
        stringResource(R.string.run_on_whitelisted_wifi_networks, specifiedSsids.joinToString())
    else
        stringResource(R.string.wifi_ssid_whitelist_empty)

    Preference(
        enabled = enabled,
        title = { Text(stringResource(R.string.specify_wifi_ssid_whitelist)) },
        summary = { Text(specifiedSsidSummary) },
        onClick = {
            if (isPermissionGranted && isLocationEnabled) {
                showSelectionAlert = true
            } else if (!isPermissionGranted) {
                permissionState.launchPermissionRequest()
            } else if (knownSsids.isEmpty()) {
                Toast.makeText(
                    context,
                    R.string.sync_only_wifi_ssids_wifi_turn_on_wifi,
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    context,
                    R.string.sync_only_wifi_ssids_wifi_turn_on_location,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    )
    if (showSelectionAlert) {
        var dialogValue by rememberSaveable { mutableStateOf(specifiedSsids) }
        val onOk = {
            specifiedSsids = dialogValue
            showSelectionAlert = false
            setPendingEvaluation()
        }
        AlertDialog(
            onDismissRequest = { showSelectionAlert = false },
            title = { Text(stringResource(R.string.specify_wifi_ssid_whitelist)) },
            text = {
                val lazyListState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    state = lazyListState,
                ) {
                    items(knownSsids.toList()) { itemValue ->
                        val checked = itemValue in dialogValue
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .toggleable(checked, true, Role.Checkbox) {
                                        dialogValue =
                                            if (it) dialogValue + itemValue else dialogValue - itemValue
                                    }
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(24.dp))
                            Text(
                                text = AnnotatedString(
                                    itemValue.replace("^\"".toRegex(), "")
                                        .replace("\"$".toRegex(), "")
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onOk) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSelectionAlert = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

private fun getCurrentWifiSsid(context: Context): String? {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val ssid = wifiManager.connectionInfo?.ssid

    return if (ssid.isNullOrBlank() || ssid == WifiManager.UNKNOWN_SSID)
        null
    else
        ssid
}
