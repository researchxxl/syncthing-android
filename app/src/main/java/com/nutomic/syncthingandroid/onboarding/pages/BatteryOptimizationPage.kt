package com.nutomic.syncthingandroid.onboarding.pages

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.onboarding.OnboardingIcon
import com.nutomic.syncthingandroid.onboarding.OnboardingScaffold
import com.nutomic.syncthingandroid.onboarding.OnboardingUiState
import com.nutomic.syncthingandroid.onboarding.PermissionButton

private const val TAG = "BatteryOptimizationPage"

@Composable
fun BatteryOptimizationPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    var showSkipConfirmation by rememberSaveable { mutableStateOf(false) }
    var skipConfirmed by rememberSaveable { mutableStateOf(false) }
    val description = if (uiState.isRunningOnTv) {
        stringResource(
            R.string.ignore_doze_permission_os_notice,
            stringResource(R.string.wiki_url),
            "Android-TV-preparations",
        ) + "\n\n" + stringResource(R.string.ignore_doze_permission_desc)
    } else {
        stringResource(R.string.ignore_doze_permission_desc)
    }
    val canContinue = uiState.hasIgnoreDozePermission || skipConfirmed || uiState.isRunningOnTv

    OnboardingScaffold(
        icon = OnboardingIcon.Vector(Icons.Outlined.BatteryChargingFull),
        title = stringResource(R.string.ignore_doze_permission_title),
        description = description,
        pageIndex = pageIndex,
        pageCount = uiState.pages.size,
        nextLabel = stringResource(R.string.cont),
        onBack = onBack,
        onNext = {
            if (canContinue) {
                onContinue()
            } else {
                showSkipConfirmation = true
            }
        },
        action = {
            PermissionButton(
                granted = uiState.hasIgnoreDozePermission,
                onClick = { requestIgnoreDozePermission(context) },
            )
        },
    )

    if (showSkipConfirmation) {
        BatteryOptimizationSkipAlert(
            onConfirm = {
                showSkipConfirmation = false
                skipConfirmed = true
                onContinue()
            },
            onDismiss = {
                showSkipConfirmation = false
            }
        )
    }
}

fun requestIgnoreDozePermission(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = "package:${context.packageName}".toUri()
        val componentName = intent.resolveActivity(context.packageManager)
        if (componentName != null) {
            val className = componentName.className
            if (!className.equals("com.android.tv.settings.EmptyStubActivity", ignoreCase = true)) {
                context.startActivity(intent)
                return
            }
        } else {
            Log.w(TAG, "Request ignore battery optimizations not supported")
        }
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "Request ignore battery optimizations not supported", e)
    }
    Toast.makeText(context, R.string.dialog_disable_battery_optimizations_not_supported, Toast.LENGTH_LONG).show()
}

@Composable
fun BatteryOptimizationSkipAlert(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(text = stringResource(R.string.dialog_confirm_skip_ignore_doze_permission))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) {
                Text(text = stringResource(R.string.yes))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(text = stringResource(R.string.no))
            }
        },
    )
}
