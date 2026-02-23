package com.nutomic.syncthingandroid.settings

import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.util.Util
import eu.chainfire.libsuperuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.rememberPreferenceState


fun EntryProviderScope<SettingsRoute>.settingsBehaviorEntry() {
    entry<SettingsRoute.Behavior> {
        SettingsBehaviorScreen()
    }
}


@Composable
fun SettingsBehaviorScreen() {
    val context = LocalContext.current
    val scope = LocalActivityScope.current

    val autoStart = rememberPreferenceState(Constants.PREF_START_SERVICE_ON_BOOT, false)
    val broadcast = rememberPreferenceState(Constants.PREF_BROADCAST_SERVICE_CONTROL, false)
    val overwrite = rememberPreferenceState(Constants.PREF_ALLOW_OVERWRITE_FILES, false)
    val useRoot = rememberPreferenceState(Constants.PREF_USE_ROOT, false)

    val toggleRoot = { enabled: Boolean ->
        scope.launch(Dispatchers.IO) {
            if (enabled) {
                if (Shell.SU.available()) {
                    useRoot.value = true
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            R.string.toast_root_denied,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } else {
                Util.fixAppDataPermissions(context)
                useRoot.value = false
            }
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.category_behaviour),
    ) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.behaviour_autostart_title)) },
                summary = { Text(stringResource(R.string.behaviour_autostart_summary)) },
                state = autoStart,
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.broadcast_service_control_title))},
                summary = { Text(stringResource(R.string.broadcast_service_control_summary))},
                state = broadcast,
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.allow_overwrite_files_title)) },
                summary = { Text(stringResource(R.string.allow_overwrite_files_summary))},
                state = overwrite,
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.use_root_title)) },
                summary = { Text(stringResource(R.string.use_root_summary)) },
                value = useRoot.value,
                onValueChange = {
                    toggleRoot(it)
                },
            )
        }
    }
}
