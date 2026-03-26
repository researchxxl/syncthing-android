package com.nutomic.syncthingandroid.util

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope

val LocalActivityScope = staticCompositionLocalOf<CoroutineScope> {
    error("No activity scope provided")
}

val LocalPagerState = staticCompositionLocalOf<PagerState> {
    error("No pager state provided")
}
