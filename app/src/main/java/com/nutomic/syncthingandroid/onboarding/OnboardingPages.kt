package com.nutomic.syncthingandroid.onboarding

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.util.LocalPagerState
import kotlinx.coroutines.launch

/**
 * Specifies the type and content of each onboarding page.
 */
enum class OnboardingPage {
    WELCOME,
    STORAGE_PERMISSION,
    BATTERY_OPTIMIZATION,
    LOCATION_PERMISSION,
    NOTIFICATION_PERMISSION,
    KEY_GENERATION,
}

/**
 * Renders the correct onboarding page for a given [OnboardingPage].
 */
@Composable
fun OnboardingPage(page: OnboardingPage) {
    when (page) {
        OnboardingPage.WELCOME -> WelcomePage()
        OnboardingPage.STORAGE_PERMISSION -> StoragePermissionPage()
        OnboardingPage.BATTERY_OPTIMIZATION -> BatteryOptimizationPage()
        OnboardingPage.LOCATION_PERMISSION -> LocationPermissionPage()
        OnboardingPage.NOTIFICATION_PERMISSION -> NotificationPermissionPage()
        OnboardingPage.KEY_GENERATION -> KeyGenerationPage()
    }
}

@Composable
private fun WelcomePage() {
    val scope = rememberCoroutineScope()
    val pagerState = LocalPagerState.current

    OnboardingScaffold(
        icon = OnboardingIcon.Logo,
        title = stringResource(R.string.welcome_title),
        description = stringResource(R.string.welcome_text),
        next = {
            NextButton(
                label = stringResource(R.string.cont),
                onClick = {
                    scope.launch {
                        val currentPage = pagerState.currentPage
                        pagerState.animateScrollToPage(currentPage + 1)
                    }
                }
            )
        },
    )
}

@Composable
private fun StoragePermissionPage() {
    val scope = rememberCoroutineScope()
    val pagerState = LocalPagerState.current

    var permissionGranted by remember { mutableStateOf(false) }

    OnboardingScaffold(
        icon = OnboardingIcon.Vector(Icons.Outlined.Storage),
        title = stringResource(R.string.storage_permission_title),
        description = stringResource(R.string.storage_permission_desc),
        action = {
            PermissionButton(
                granted = permissionGranted,
                onClick = {
                    permissionGranted = true
                }
            )
        },
        next = {
            NextButton(
                enabled = permissionGranted,
                label = stringResource(R.string.cont),
                onClick = {
                    scope.launch {
                        val currentPage = pagerState.currentPage
                        pagerState.animateScrollToPage(currentPage + 1)
                    }
                }
            )
        },
    )
}

@Composable
private fun BatteryOptimizationPage() {
    val scope = rememberCoroutineScope()
    val pagerState = LocalPagerState.current

    var permissionGranted by remember { mutableStateOf(false) }

    OnboardingScaffold(
        icon = OnboardingIcon.Vector(Icons.Outlined.BatteryChargingFull),
        title = stringResource(R.string.ignore_doze_permission_title),
        description = stringResource(R.string.ignore_doze_permission_desc),
        action = {
            PermissionButton(
                granted = permissionGranted,
                onClick = {
                    permissionGranted = true
                }
            )
        },
        next = {
            NextButton(
                enabled = permissionGranted,
                label = stringResource(R.string.cont),
                onClick = {
                    scope.launch {
                        val currentPage = pagerState.currentPage
                        pagerState.animateScrollToPage(currentPage + 1)
                    }
                }
            )
        },
    )
}

@Composable
private fun LocationPermissionPage() {
    val scope = rememberCoroutineScope()
    val pagerState = LocalPagerState.current

    var permissionGranted by remember { mutableStateOf(false) }

    OnboardingScaffold(
        icon = OnboardingIcon.Vector(Icons.Outlined.LocationOn),
        title = stringResource(R.string.location_permission_title),
        description = stringResource(R.string.location_permission_desc),
        action = {
            PermissionButton(
                granted = permissionGranted,
                onClick = {
                    permissionGranted = true
                }
            )
        },
        next = {
            NextButton(
                enabled = permissionGranted,
                label = stringResource(R.string.cont),
                onClick = {
                    scope.launch {
                        val currentPage = pagerState.currentPage
                        pagerState.animateScrollToPage(currentPage + 1)
                    }
                }
            )
        },
    )
}

@Composable
private fun NotificationPermissionPage() {
    val scope = rememberCoroutineScope()
    val pagerState = LocalPagerState.current

    var permissionGranted by remember { mutableStateOf(false) }

    OnboardingScaffold(
        icon = OnboardingIcon.Vector(Icons.Outlined.Notifications),
        title = stringResource(R.string.notification_permission_title),
        description = stringResource(R.string.notification_permission_desc),
        action = {
            PermissionButton(
                granted = permissionGranted,
                onClick = {
                    permissionGranted = true
                }
            )
        },
        next = {
            NextButton(
                enabled = permissionGranted,
                label = stringResource(R.string.cont),
                onClick = {
                    scope.launch {
                        val currentPage = pagerState.currentPage
                        pagerState.animateScrollToPage(currentPage + 1)
                    }
                }
            )
        },
    )
}

@Composable
private fun KeyGenerationPage() {
    val activity = LocalActivity.current as OnboardingActivity

    OnboardingScaffold(
        icon = OnboardingIcon.Vector(Icons.Outlined.Key),
        title = stringResource(R.string.key_generation_title),
        description = stringResource(R.string.web_gui_creating_key),
        action = {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
            )
        },
        next = {
            NextButton(
                label = stringResource(R.string.cont),
                onClick = {
                    activity.startApp()
                }
            )
        },
    )
}
