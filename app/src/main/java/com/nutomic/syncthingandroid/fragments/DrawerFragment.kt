package com.nutomic.syncthingandroid.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment

import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.activities.MainActivity
import com.nutomic.syncthingandroid.activities.RecentChangesActivity
import com.nutomic.syncthingandroid.activities.WebGuiActivity
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.service.SyncthingService.OnServiceStateChangeListener
import com.nutomic.syncthingandroid.settings.SettingsActivity
import com.nutomic.syncthingandroid.theme.ApplicationTheme
import com.nutomic.syncthingandroid.util.isTelevision

class DrawerFragment : Fragment(), OnServiceStateChangeListener {

    private val stServiceRunning = mutableStateOf(false)
    private val focusRequested = mutableStateOf(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        setContent {
            ApplicationTheme {
                DrawerContent(stServiceRunning.value, focusRequested)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val drawerContainer = requireActivity().findViewById<View>(R.id.drawer)

        // inset correction
        ViewCompat.setOnApplyWindowInsetsListener(drawerContainer) { v, i ->
            v.setPadding(0, 0, 0, 0)
            i
        }
        ViewCompat.requestApplyInsets(drawerContainer)

        // width restriction
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val maxWidthPx = (360 * displayMetrics.density).toInt()
        val minGapPx = (56 * displayMetrics.density).toInt()

        val desiredWidth = minOf(maxWidthPx, screenWidth - minGapPx)

        drawerContainer.layoutParams = drawerContainer.layoutParams.apply {
            width = desiredWidth
        }
    }

    override fun onServiceStateChange(currentState: SyncthingService.State?) {
        stServiceRunning.value = currentState == SyncthingService.State.ACTIVE
    }

    fun drawerOpened() {
        focusRequested.value = true
    }
}


@Composable
private fun DrawerContent(
    stServiceRunning: Boolean,
    focusRequested: MutableState<Boolean>,
) {
    val activity = LocalActivity.current as MainActivity
    val config = LocalConfiguration.current

    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequested.value) {
        if (focusRequested.value) {
            firstItemFocusRequester.requestFocus()
            focusRequested.value = false
        }
    }

    ModalDrawerSheet(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            DrawerHeader()

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            LazyColumn(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .weight(1f)
            ) {
                item {
                    DrawerItem(
                        icon = { Icon(Icons.Outlined.QrCode2, null) },
                        label = { Text(stringResource(R.string.show_device_id)) },
                        onClick = {
                            activity.showQrCodeDialog()
                            activity.closeDrawer()
                        },
                        modifier = Modifier.focusRequester(firstItemFocusRequester)
                    )
                }
                item {
                    DrawerItem(
                        icon = { Icon(Icons.Outlined.Restore, null) },
                        label = { Text(stringResource(R.string.recent_changes_title)) },
                        onClick = {
                            val intent = Intent(activity, RecentChangesActivity::class.java)
                            activity.startActivity(intent)
                            activity.closeDrawer()
                        },
                        enabled = stServiceRunning,
                    )
                }

                if (Constants.isDebuggable(activity) || !config.isTelevision) {
                    item {
                        DrawerItem(
                            icon = { Icon(Icons.AutoMirrored.Outlined.ViewQuilt, null) },
                            label = { Text(stringResource(R.string.web_gui_title)) },
                            onClick = {
                                activity.startActivity(Intent(activity, WebGuiActivity::class.java))
                                activity.closeDrawer()
                            },
                            enabled = stServiceRunning,
                        )
                    }
                }

                item {
                    DrawerItem(
                        icon = { Icon(Icons.Outlined.ImportExport, null) },
                        label = { Text(stringResource(R.string.category_backup)) },
                        onClick = {
                            val intent = Intent(activity, SettingsActivity::class.java).apply {
                                putExtra(SettingsActivity.EXTRA_START_DESTINATION, "ImportExport")
                            }
                            activity.startActivity(intent)
                            activity.closeDrawer()
                        },
                    )
                }
                item {
                    RestartDrawerItem(stServiceRunning)
                }
            }

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                DrawerItem(
                    icon = { Icon(Icons.Outlined.Settings, null) },
                    label = { Text(stringResource(R.string.settings_title)) },
                    onClick = {
                        activity.startActivity(Intent(activity, SettingsActivity::class.java))
                        activity.closeDrawer()
                    },
                )
                ExitDrawerItem()
            }
        }
    }
}

@Composable
private fun RestartDrawerItem(stServiceRunning: Boolean) {
    val activity = LocalActivity.current as MainActivity
    var showAlert by rememberSaveable { mutableStateOf(false) }

    DrawerItem(
        icon = { Icon(Icons.Outlined.Autorenew, null) },
        label = { Text(stringResource(R.string.restart)) },
        onClick = {
            showAlert = true
            activity.closeDrawer()
        },
        enabled = stServiceRunning,
    )
    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text(stringResource(R.string.dialog_confirm_restart)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(activity, SyncthingService::class.java).apply {
                            action = SyncthingService.ACTION_RESTART
                        }
                        activity.startService(intent)
                        showAlert = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAlert = false },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ExitDrawerItem() {
    val activity = LocalActivity.current as MainActivity
    val prefs = activity.mPreferences

    var showAlert by rememberSaveable { mutableStateOf(false) }

    DrawerItem(
        icon = { Icon(Icons.AutoMirrored.Outlined.ExitToApp, null) },
        label = { Text(stringResource(R.string.exit)) },
        onClick = {
            val isAutostartOn = prefs.getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false)
            if (isAutostartOn) {
                showAlert = true
                activity.closeDrawer()
            } else {
                activity.doExit()
            }
        },
    )
    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text(stringResource(R.string.dialog_exit_while_running_as_service_title)) },
            text = { Text(stringResource(R.string.dialog_exit_while_running_as_service_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        activity.doExit()
                    },
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAlert = false },
                ) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

// drawer item wrapper because NavigationDrawerItem doesn't support `enabled`
@Composable
private fun DrawerItem(
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (enabled) {
        NavigationDrawerItem(
            icon = icon,
            label = label,
            onClick = onClick,
            selected = false,
            modifier = modifier
        )
    } else {
        val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        Surface(
            color = Color.Transparent,
            modifier = modifier
                .heightIn(min = 56.dp)
                .fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 24.dp)
            ) {
                CompositionLocalProvider(LocalContentColor provides color, content = icon)
                Spacer(Modifier.width(12.dp))
                CompositionLocalProvider(LocalContentColor provides color, content = label)
            }
        }
    }
}

@Composable
private fun DrawerHeader() {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .heightIn(min = 56.dp)
            .fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 24.dp, bottom = 16.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_monochrome),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        scaleX = 1.6f
                        scaleY = 1.6f
                    },
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
