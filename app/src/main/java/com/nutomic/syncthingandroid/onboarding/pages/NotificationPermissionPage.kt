package com.nutomic.syncthingandroid.onboarding.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.onboarding.OnboardingIcon
import com.nutomic.syncthingandroid.onboarding.OnboardingScaffold
import com.nutomic.syncthingandroid.onboarding.OnboardingUiState
import com.nutomic.syncthingandroid.onboarding.PermissionButton

@Composable
fun NotificationPermissionPage(
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
