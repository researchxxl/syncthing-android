package com.nutomic.syncthingandroid.onboarding

import android.os.Build
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.R

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
fun OnboardingPage(
    page: OnboardingPage,
    uiState: OnboardingUiState,
    pageIndex: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onGrantStoragePermission: () -> Unit,
    onGrantIgnoreDozePermission: () -> Unit,
    onGrantLocationPermission: () -> Unit,
    onGrantNotificationPermission: () -> Unit,
) {
    when (page) {
        OnboardingPage.WELCOME -> WelcomePage(
            uiState = uiState,
            pageIndex = pageIndex,
            onBack = onBack,
            onContinue = onContinue,
        )
        OnboardingPage.STORAGE_PERMISSION -> StoragePermissionPage(
            uiState = uiState,
            pageIndex = pageIndex,
            onBack = onBack,
            onContinue = onContinue,
            onGrantStoragePermission = onGrantStoragePermission,
        )
        OnboardingPage.BATTERY_OPTIMIZATION -> BatteryOptimizationPage(
            uiState = uiState,
            pageIndex = pageIndex,
            onBack = onBack,
            onContinue = onContinue,
            onGrantIgnoreDozePermission = onGrantIgnoreDozePermission,
        )
        OnboardingPage.LOCATION_PERMISSION -> LocationPermissionPage(
            uiState = uiState,
            pageIndex = pageIndex,
            onBack = onBack,
            onContinue = onContinue,
            onGrantLocationPermission = onGrantLocationPermission,
        )
        OnboardingPage.NOTIFICATION_PERMISSION -> NotificationPermissionPage(
            uiState = uiState,
            pageIndex = pageIndex,
            onBack = onBack,
            onContinue = onContinue,
            onGrantNotificationPermission = onGrantNotificationPermission,
        )
        OnboardingPage.KEY_GENERATION -> KeyGenerationPage(
            uiState = uiState,
            pageIndex = pageIndex,
            onBack = onBack,
            onContinue = onContinue,
        )
    }
}

@Composable
private fun WelcomePage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    OnboardingScaffold(
        icon = OnboardingIcon.Logo,
        title = stringResource(R.string.welcome_title),
        description = stringResource(R.string.welcome_text),
        pageIndex = pageIndex,
        pageCount = uiState.pages.size,
        canGoBack = false,
        backVisible = false,
        nextLabel = stringResource(R.string.cont),
        onBack = onBack,
        onNext = onContinue,
    )
}

@Composable
private fun StoragePermissionPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onGrantStoragePermission: () -> Unit,
) {
    OnboardingScaffold(
        icon = OnboardingIcon.Vector(Icons.Outlined.Storage),
        title = stringResource(R.string.storage_permission_title),
        description = stringResource(R.string.storage_permission_desc),
        pageIndex = pageIndex,
        pageCount = uiState.pages.size,
        nextLabel = stringResource(R.string.cont),
        nextEnabled = uiState.hasStoragePermission,
        onBack = onBack,
        onNext = onContinue,
        action = {
            PermissionButton(
                granted = uiState.hasStoragePermission,
                onClick = onGrantStoragePermission,
            )
        },
    )
}

@Composable
private fun BatteryOptimizationPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onGrantIgnoreDozePermission: () -> Unit,
) {
    val description = if (uiState.isRunningOnTv) {
        stringResource(
            R.string.ignore_doze_permission_os_notice,
            stringResource(R.string.wiki_url),
            "Android-TV-preparations",
        ) + "\n\n" + stringResource(R.string.ignore_doze_permission_desc)
    } else {
        stringResource(R.string.ignore_doze_permission_desc)
    }

    OnboardingScaffold(
        icon = OnboardingIcon.Vector(Icons.Outlined.BatteryChargingFull),
        title = stringResource(R.string.ignore_doze_permission_title),
        description = description,
        pageIndex = pageIndex,
        pageCount = uiState.pages.size,
        nextLabel = stringResource(R.string.cont),
        onBack = onBack,
        onNext = onContinue,
        action = {
            PermissionButton(
                granted = uiState.hasIgnoreDozePermission,
                onClick = onGrantIgnoreDozePermission,
            )
        },
    )
}

@Composable
private fun LocationPermissionPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onGrantLocationPermission: () -> Unit,
) {
    val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        stringResource(R.string.location_permission_desc) + "\n\n" +
            stringResource(R.string.location_permission_desc_api_29)
    } else {
        stringResource(R.string.location_permission_desc)
    }

    OnboardingScaffold(
        icon = OnboardingIcon.Vector(Icons.Outlined.LocationOn),
        title = stringResource(R.string.location_permission_title),
        description = description,
        pageIndex = pageIndex,
        pageCount = uiState.pages.size,
        nextLabel = stringResource(R.string.cont),
        onBack = onBack,
        onNext = onContinue,
        action = {
            PermissionButton(
                granted = uiState.hasLocationPermission,
                onClick = onGrantLocationPermission,
            )
        },
    )
}

@Composable
private fun NotificationPermissionPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onGrantNotificationPermission: () -> Unit,
) {
    OnboardingScaffold(
        icon = OnboardingIcon.Vector(Icons.Outlined.Notifications),
        title = stringResource(R.string.notification_permission_title),
        description = stringResource(R.string.notification_permission_desc),
        pageIndex = pageIndex,
        pageCount = uiState.pages.size,
        nextLabel = stringResource(R.string.cont),
        nextEnabled = uiState.hasNotificationPermission,
        onBack = onBack,
        onNext = onContinue,
        action = {
            PermissionButton(
                granted = uiState.hasNotificationPermission,
                onClick = onGrantNotificationPermission,
            )
        },
    )
}

@Composable
private fun KeyGenerationPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    OnboardingScaffold(
        icon = OnboardingIcon.Vector(Icons.Outlined.Key),
        title = stringResource(R.string.key_generation_title),
        description = uiState.keyGenerationStatus,
        pageIndex = pageIndex,
        pageCount = uiState.pages.size,
        canGoBack = !uiState.keyGenerationRunning,
        nextLabel = stringResource(
            if (uiState.keyGenerationFailed) {
                R.string.open_log
            } else {
                R.string.finish
            }
        ),
        nextEnabled = uiState.keyGenerationFailed || uiState.hasConfig,
        onBack = onBack,
        onNext = onContinue,
        action = {
            if (uiState.keyGenerationRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp,
                )
            }
        },
    )
}
