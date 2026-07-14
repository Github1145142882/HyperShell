package io.github.hypershell.ui.kit.component

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainPagerState(val pagerState: PagerState, private val scope: CoroutineScope) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set
    var isNavigating by mutableStateOf(false)
        private set
    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return
        navJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true
        val distance = abs(targetIndex - pagerState.currentPage).coerceAtLeast(2)
        val pageSize = pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing
        val pages = targetIndex - pagerState.currentPage - pagerState.currentPageOffsetFraction
        navJob = scope.launch {
            val ownJob = coroutineContext.job
            try {
                pagerState.animateScrollBy(pages * pageSize, tween(100 * distance + 100, easing = EaseInOut))
            } finally {
                if (navJob == ownJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) selectedPage = pagerState.currentPage
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) selectedPage = pagerState.currentPage
    }
}

@Composable
fun rememberMainPagerState(pagerState: PagerState, scope: CoroutineScope = rememberCoroutineScope()) =
    remember(pagerState, scope) { MainPagerState(pagerState, scope) }
