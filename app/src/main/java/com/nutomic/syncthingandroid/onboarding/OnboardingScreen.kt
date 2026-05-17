package com.nutomic.syncthingandroid.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.nutomic.syncthingandroid.util.LocalPagerState

/**
 * The list of onboarding pages shown in order.
 */

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onBack: () -> Unit,
    onContinue: (OnboardingPage) -> Unit,
    onGrantStoragePermission: () -> Unit,
    onGrantIgnoreDozePermission: () -> Unit,
    onGrantLocationPermission: () -> Unit,
    onGrantNotificationPermission: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { uiState.pages.size })

    BackHandler(onBack = onBack)

    LaunchedEffect(uiState.currentPage) {
        if (uiState.currentPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(uiState.currentPage)
        }
    }

    CompositionLocalProvider(LocalPagerState provides pagerState) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
        ) { pageIdx ->
            val page = uiState.pages[pageIdx]
            OnboardingPage(
                page = page,
                uiState = uiState,
                pageIndex = pageIdx,
                onBack = onBack,
                onContinue = { onContinue(page) },
                onGrantStoragePermission = onGrantStoragePermission,
                onGrantIgnoreDozePermission = onGrantIgnoreDozePermission,
                onGrantLocationPermission = onGrantLocationPermission,
                onGrantNotificationPermission = onGrantNotificationPermission,
            )
        }
    }
}
