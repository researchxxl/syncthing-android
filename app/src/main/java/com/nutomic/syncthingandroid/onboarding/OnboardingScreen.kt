package com.nutomic.syncthingandroid.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.nutomic.syncthingandroid.util.LocalPagerState

/**
 * The list of onboarding pages shown in order.
 */

@Composable
fun OnboardingScreen(
    pages: List<OnboardingPage>
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })

    CompositionLocalProvider(LocalPagerState provides pagerState) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
        ) { pageIdx ->
            OnboardingPage(page = pages[pageIdx])
        }
    }
}
