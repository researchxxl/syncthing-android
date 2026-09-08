package com.nutomic.syncthingandroid.onboarding.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.onboarding.OnboardingIcon
import com.nutomic.syncthingandroid.onboarding.OnboardingScaffold
import com.nutomic.syncthingandroid.onboarding.OnboardingUiState
import com.nutomic.syncthingandroid.onboarding.PermissionButton

@Composable
fun LocalNetworkPermissionPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    requestTvFocus: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onGrantLocalNetworkPermission: () -> Unit,
) {
    val actionFocusRequester = remember { FocusRequester() }

    OnboardingScaffold(
        icon = OnboardingIcon.Vector(Icons.Outlined.Devices),
        title = stringResource(R.string.local_network_permission_title),
        description = stringResource(R.string.local_network_permission_desc),
        pageIndex = pageIndex,
        pageCount = uiState.pages.size,
        nextLabel = stringResource(R.string.cont),
        nextEnabled = uiState.hasLocalNetworkPermission,
        requestTvFocus = requestTvFocus,
        onBack = onBack,
        onNext = onContinue,
        actionFocusRequester = actionFocusRequester,
        action = {
            PermissionButton(
                granted = uiState.hasLocalNetworkPermission,
                onClick = onGrantLocalNetworkPermission,
                focusRequester = actionFocusRequester,
            )
        },
    )
}
