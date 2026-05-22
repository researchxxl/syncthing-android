package com.nutomic.syncthingandroid.onboarding.pages

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.onboarding.OnboardingActivity.Companion.REQUEST_WRITE_STORAGE
import com.nutomic.syncthingandroid.onboarding.OnboardingIcon
import com.nutomic.syncthingandroid.onboarding.OnboardingScaffold
import com.nutomic.syncthingandroid.onboarding.OnboardingUiState
import com.nutomic.syncthingandroid.onboarding.PermissionButton
import com.nutomic.syncthingandroid.util.PermissionUtil

@Composable
fun StoragePermissionPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val activity = LocalActivity.current

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
                onClick = {
                    activity?.let {
                        PermissionUtil.requestStoragePermission(it, REQUEST_WRITE_STORAGE)
                    }
                },
            )
        },
    )
}
