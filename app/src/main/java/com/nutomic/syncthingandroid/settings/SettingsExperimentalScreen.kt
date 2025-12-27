package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference


fun EntryProviderScope<SettingsRoute>.settingsExperimentalEntry() {
    entry<SettingsRoute.Experimental> {
        SettingsExperimentalScreen()
    }
}


@Composable
fun SettingsExperimentalScreen() {
    val useTor = remember { mutableStateOf(false) }
    val socksProxy = remember { mutableStateOf("") }
    val httpProxy = remember { mutableStateOf("") }

    SettingsScaffold(
        title = stringResource(R.string.category_experimental),
    ) {
        SwitchPreference(
            title = { Text(stringResource(R.string.use_tor_title)) },
            summary = { Text(stringResource(R.string.use_tor_summary)) },
            state = useTor,
        )

        val socksProxySummary = if (socksProxy.value.isBlank())
            "${stringResource(R.string.do_not_use_proxy)} ${stringResource(R.string.generic_example)}: ${stringResource(R.string.socks_proxy_address_example)}"
        else
            "${stringResource(R.string.use_proxy)} ${socksProxy.value}"
        TextFieldPreference(
            title = { Text(stringResource(R.string.socks_proxy_address_title)) },
            summary = { Text(socksProxySummary) },
            state = socksProxy,
            textToValue = { it },
        )

        val httpProxySummary = if (httpProxy.value.isBlank())
            "${stringResource(R.string.do_not_use_proxy)} ${stringResource(R.string.generic_example)}: ${stringResource(R.string.http_proxy_address_example)}"
        else
            "${stringResource(R.string.use_proxy)} ${httpProxy.value}"
        TextFieldPreference(
            title = { Text(stringResource(R.string.http_proxy_address_title)) },
            summary = { Text(httpProxySummary) },
            state = httpProxy,
            textToValue = { it },
        )
    }
}
