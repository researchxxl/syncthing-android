package com.nutomic.syncthingandroid.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

/**
 * The list of onboarding pages shown in order.
 */

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onFinishOnboarding: () -> Unit,
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
            requestTvFocus = pagerState.currentPage == pageIdx && !pagerState.isScrollInProgress,
            onBack = onBack,
            onContinue = onContinue,
            onFinishOnboarding = onFinishOnboarding,
            onGrantLocationPermission = onGrantLocationPermission,
            onGrantNotificationPermission = onGrantNotificationPermission,
        )
    }
}
