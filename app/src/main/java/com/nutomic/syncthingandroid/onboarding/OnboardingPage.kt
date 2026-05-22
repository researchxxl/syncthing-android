package com.nutomic.syncthingandroid.onboarding

import androidx.compose.runtime.Composable
import com.nutomic.syncthingandroid.onboarding.pages.BatteryOptimizationPage
import com.nutomic.syncthingandroid.onboarding.pages.KeyGenerationPage
import com.nutomic.syncthingandroid.onboarding.pages.LocationPermissionPage
import com.nutomic.syncthingandroid.onboarding.pages.NotificationPermissionPage
import com.nutomic.syncthingandroid.onboarding.pages.StoragePermissionPage
import com.nutomic.syncthingandroid.onboarding.pages.WelcomePage

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
    onFinishOnboarding: () -> Unit,
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
        )
        OnboardingPage.BATTERY_OPTIMIZATION -> BatteryOptimizationPage(
            uiState = uiState,
            pageIndex = pageIndex,
            onBack = onBack,
            onContinue = onContinue,
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
            onFinishOnboarding = onFinishOnboarding,
        )
    }
}
