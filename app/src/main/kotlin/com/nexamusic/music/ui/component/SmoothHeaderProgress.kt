/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Scroll progress is scoped to a NavBackStackEntry. Screens with Compose
 * scroll states read their restored offset directly; custom scroll containers
 * use the keyed nested-scroll bridge below.
 */
package com.nexamusic.music.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Progress is stored by navigation-entry ID. The legacy value remains only
 * for older detail chrome that has not yet been migrated to a keyed provider.
 */
object SmoothHeaderProgress {
    private val byScreen = mutableStateMapOf<String, Float>()

    var progress by mutableFloatStateOf(0f)

    fun progressFor(screenKey: String): Float = byScreen[screenKey] ?: 0f

    fun setForScreen(screenKey: String, value: Float) {
        byScreen[screenKey] = SmoothHeaderTokens.quantizeProgress(value)
    }

    fun clearScreen(screenKey: String) {
        byScreen.remove(screenKey)
    }
}

/**
 * Nested-scroll bridge for custom scroll containers without a Compose state
 * API. The owning back-stack key is mandatory in new call sites; overscroll
 * at the top contributes no consumed distance and therefore keeps p at zero.
 */
fun Modifier.smoothHeaderScrollProgress(screenKey: String? = null): Modifier = composed {
    val density = LocalDensity.current
    val scrollPx = remember(screenKey) { mutableFloatStateOf(0f) }
    val connection = remember(density, screenKey) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val nextOffset = (scrollPx.floatValue - consumed.y).coerceAtLeast(0f)
                scrollPx.floatValue = nextOffset
                val value = SmoothHeaderTokens.progress(nextOffset / density.density)
                if (screenKey == null) SmoothHeaderProgress.progress = value
                else SmoothHeaderProgress.setForScreen(screenKey, value)
                return Offset.Zero
            }
        }
    }
    nestedScroll(connection)
}

/** Direct, first-frame progress from the restored LazyListState offset. */
@Composable
fun rememberSmoothHeaderProgress(state: LazyListState): () -> Float {
    val density = LocalDensity.current
    val collapseDistancePx = with(density) { SmoothHeaderTokens.HeaderScrollDistance.roundToPx() }
    val firstItemSizePx = remember(state) { mutableIntStateOf(0) }
    SideEffect {
        if (state.firstVisibleItemIndex == 0) {
            state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                ?.let { firstItemSizePx.intValue = it.size }
        }
    }
    return remember(state, density, collapseDistancePx, firstItemSizePx) {
        {
            val layout = state.layoutInfo
            val visible = layout.visibleItemsInfo
            val firstVisible = visible.firstOrNull()
            val scrolledPx = when {
                state.firstVisibleItemIndex == 0 -> state.firstVisibleItemScrollOffset
                state.firstVisibleItemIndex == 1 && firstItemSizePx.intValue > 0 ->
                    firstItemSizePx.intValue + (-(firstVisible?.offset ?: 0)).coerceAtLeast(0)
                else -> collapseDistancePx
            }
            val contentFitsViewport = layout.totalItemsCount > 0 &&
                visible.size >= layout.totalItemsCount &&
                ((visible.maxOfOrNull { it.offset + it.size } ?: 0) -
                    (visible.minOfOrNull { it.offset } ?: 0)) <= layout.viewportSize.height
            if (contentFitsViewport) 0f
            else SmoothHeaderTokens.progress(scrolledPx.coerceAtLeast(0) / density.density)
        }
    }
}

/** Direct, first-frame progress from the restored LazyGridState offset. */
@Composable
fun rememberSmoothHeaderProgress(state: LazyGridState): () -> Float {
    val density = LocalDensity.current
    val collapseDistancePx = with(density) { SmoothHeaderTokens.HeaderScrollDistance.roundToPx() }
    return remember(state, density, collapseDistancePx) {
        {
            val layout = state.layoutInfo
            val visible = layout.visibleItemsInfo
            val contentFitsViewport = layout.totalItemsCount > 0 &&
                visible.size >= layout.totalItemsCount &&
                ((visible.maxOfOrNull { it.offset.y + it.size.height } ?: 0) -
                    (visible.minOfOrNull { it.offset.y } ?: 0)) <= layout.viewportSize.height
            when {
                contentFitsViewport -> 0f
                state.firstVisibleItemIndex > 0 -> 1f
                else -> SmoothHeaderTokens.progress(
                    state.firstVisibleItemScrollOffset.coerceAtLeast(0) / density.density,
                )
            }
        }
    }
}

/**
 * Publishes only this destination's value. The initial SideEffect publishes
 * restored scroll position immediately (without an animated warm-up), while
 * disposal removes stale entries so long navigation sessions do not grow
 * the progress map indefinitely.
 */
@Composable
fun SmoothHeaderProgressSync(
    progressProvider: () -> Float,
    screenKey: String = "legacy",
) {
    val progress by remember(progressProvider) {
        derivedStateOf { progressProvider().coerceIn(0f, 1f) }
    }
    SideEffect {
        val value = SmoothHeaderTokens.quantizeProgress(progress)
        if (screenKey == "legacy") SmoothHeaderProgress.progress = value
        else SmoothHeaderProgress.setForScreen(screenKey, value)
    }
    DisposableEffect(screenKey) {
        onDispose {
            if (screenKey == "legacy") SmoothHeaderProgress.progress = 0f
            else SmoothHeaderProgress.clearScreen(screenKey)
        }
    }
    LaunchedEffect(progressProvider, screenKey) {
        snapshotFlow { progressProvider().coerceIn(0f, 1f) }
            .distinctUntilChanged()
            .collect { value ->
                val quantized = SmoothHeaderTokens.quantizeProgress(value)
                if (screenKey == "legacy") SmoothHeaderProgress.progress = quantized
                else SmoothHeaderProgress.setForScreen(screenKey, quantized)
            }
    }
}
