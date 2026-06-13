package com.nutomic.syncthingandroid.onboarding.pages

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.activities.LogActivity
import com.nutomic.syncthingandroid.onboarding.OnboardingIcon
import com.nutomic.syncthingandroid.onboarding.OnboardingScaffold
import com.nutomic.syncthingandroid.onboarding.OnboardingUiState

@Composable
fun KeyGenerationPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    requestTvFocus: Boolean,
    onBack: () -> Unit,
    onFinishOnboarding: () -> Unit,
) {
    val context = LocalContext.current

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
        requestTvFocus = requestTvFocus,
        onBack = onBack,
        onNext = {
            if (uiState.keyGenerationFailed) {
                val intent = Intent(context, LogActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(intent)
                (context as? Activity)?.finish()
            } else if (uiState.hasConfig) {
                onFinishOnboarding()
            }
        },
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
