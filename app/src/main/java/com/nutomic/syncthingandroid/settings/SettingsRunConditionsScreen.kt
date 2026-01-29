package com.nutomic.syncthingandroid.settings

import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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

    val runOnWifi = rememberPreferenceState(Constants.PREF_RUN_ON_WIFI, true)
    val runOnMeteredWifi = rememberPreferenceState(Constants.PREF_RUN_ON_METERED_WIFI, false)
    val runOnSpecifiedSsid = rememberPreferenceState(Constants.PREF_USE_WIFI_SSID_WHITELIST, false)
    val specifiedSsids = rememberPreferenceState(Constants.PREF_WIFI_SSID_WHITELIST, setOf<String>())

    val runOnMobileData = rememberPreferenceState(Constants.PREF_RUN_ON_MOBILE_DATA, false)
    val runOnRoaming = rememberPreferenceState(Constants.PREF_RUN_ON_ROAMING, false)

    val powerSourceNames = stringArrayResource(R.array.power_source_entries)
    val powerSourceValues = stringArrayResource(R.array.power_source_values)
    val powerSource = rememberPreferenceState(Constants.PREF_POWER_SOURCE, powerSourceValues[0])

    val respectBatterySaving = rememberPreferenceState(Constants.PREF_RESPECT_BATTERY_SAVING, true)
    val respectMasterSync = rememberPreferenceState(Constants.PREF_RESPECT_MASTER_SYNC, false)
    val flightMode = rememberPreferenceState(Constants.PREF_RUN_IN_FLIGHT_MODE, false)

    val runScheduled = rememberPreferenceState(Constants.PREF_RUN_ON_TIME_SCHEDULE, false)
    val syncDuration = rememberPreferenceState(Constants.PREF_SYNC_DURATION_MINUTES, 5)
    val sleepInterval = rememberPreferenceState(Constants.PREF_SLEEP_INTERVAL_MINUTES, 60)

    // TODO: re-evaluate run condition on any preference changed

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
            state = runOnWifi,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.run_on_metered_wifi_title)) },
            summary = { Text(stringResource(R.string.run_on_metered_wifi_summary)) },
            state = runOnMeteredWifi,
            enabled = runOnWifi.value,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.run_on_whitelisted_wifi_title)) },
            summary = { Text(stringResource(R.string.run_on_whitelisted_wifi_summary)) },
            state = runOnSpecifiedSsid,
            enabled = runOnWifi.value,
        )

        val specifiedSsidSummary = if (specifiedSsids.value.isNotEmpty())
            stringResource(R.string.run_on_whitelisted_wifi_networks, specifiedSsids.value.joinToString())
        else
            stringResource(R.string.wifi_ssid_whitelist_empty)
        MultiSelectListPreference(
            title = { Text(stringResource(R.string.specify_wifi_ssid_whitelist)) },
            summary = { Text(specifiedSsidSummary) },
            state = specifiedSsids,
            enabled = runOnWifi.value && runOnSpecifiedSsid.value,
            // TODO: implement logics from com.nutomic.syncthingandroid.views.WifiSsidPreference and get ssid list
            values = listOf("ssid1", "ssid2", "ssid3")
        )

        PreferenceCategory(
            title = { Text(stringResource(R.string.category_mobile_data)) }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.run_on_mobile_data_title)) },
            summary = { Text(stringResource(R.string.run_on_mobile_data_summary)) },
            state = runOnMobileData,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.run_on_roaming_title)) },
            summary = { Text(stringResource(R.string.run_on_roaming_summary)) },
            state = runOnRoaming,
            enabled = runOnMobileData.value,
        )

        PreferenceCategory(
            title = { Text(stringResource(R.string.category_battery)) },
        )
        ListPreference(
            title = { Text(stringResource(R.string.power_source_title)) },
            summary = { Text(powerSourceNames[powerSourceValues.indexOf(powerSource.value)]) },
            state = powerSource,
            values = powerSourceValues.toList(),
            valueToText = { value -> AnnotatedString(powerSourceNames[powerSourceValues.indexOf(value)]) }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.respect_battery_saving_title)) },
            summary = { Text(stringResource(R.string.respect_battery_saving_summary)) },
            state = respectBatterySaving,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.respect_master_sync_title)) },
            summary = { Text(stringResource(R.string.respect_master_sync_summary)) },
            state = respectMasterSync,
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.run_in_flight_mode_title)) },
            summary = { Text(stringResource(R.string.run_in_flight_mode_summary)) },
            state = flightMode,
        )

        PreferenceCategory(
            title = { Text(stringResource(R.string.category_schedule)) },
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.run_on_time_schedule_title)) },
            summary = { Text(stringResource(R.string.run_on_time_schedule_summary)) },
            state = runScheduled,
        )
        val syncDurationError = stringResource(R.string.invalid_integer_value, 1, 1440/* 24h */)
        TextFieldPreference(
            title = { Text(stringResource(R.string.sync_duration_minutes_title)) },
            summary = { Text(stringResource(R.string.sync_duration_minutes_summary, syncDuration.value)) },
            state = syncDuration,
            textToValue = { text ->
                val mins = text.toIntOrNull()
                if (mins == null || mins !in 1..1440) {
                    Toast.makeText(context, syncDurationError, Toast.LENGTH_LONG).show()
                    null
                } else {
                    mins
                }
            }
        )
        val sleepIntervalError = stringResource(R.string.invalid_integer_value, 1, 30240/* 3w */)
        TextFieldPreference(
            title = { Text(stringResource(R.string.sleep_interval_minutes_title)) },
            summary = { Text(stringResource(R.string.sync_duration_minutes_summary, sleepInterval.value)) },
            state = sleepInterval,
            textToValue = { text ->
                val mins = text.toIntOrNull()
                if (mins == null || mins !in 1..30240) {
                    Toast.makeText(context, sleepIntervalError, Toast.LENGTH_LONG).show()
                    null
                } else {
                    mins
                }
            }
        )
    }
}
