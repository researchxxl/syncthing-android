package com.nutomic.syncthingandroid.settings

import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.MultiSelectListPreference
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
    var specifiedSsids by rememberPreferenceState(Constants.PREF_WIFI_SSID_WHITELIST, setOf<String>())

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

        val specifiedSsidSummary = if (specifiedSsids.isNotEmpty())
            stringResource(R.string.run_on_whitelisted_wifi_networks, specifiedSsids.joinToString())
        else
            stringResource(R.string.wifi_ssid_whitelist_empty)
        MultiSelectListPreference(
            title = { Text(stringResource(R.string.specify_wifi_ssid_whitelist)) },
            summary = { Text(specifiedSsidSummary) },
            value = specifiedSsids,
            onValueChange = {
                specifiedSsids = it
                pendingEvaluation = true
            },
            enabled = runOnWifi && runOnSpecifiedSsid,
            // TODO: implement logics from com.nutomic.syncthingandroid.views.WifiSsidPreference and get ssid list
            values = listOf("ssid1", "ssid2", "ssid3")
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
