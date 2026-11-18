/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Shared scroll signal consumed by the persistent app header. Screens attach
 * smoothHeaderScrollProgress() to their primary vertical scrolling container.
 */
package com.nexamusic.music.ui.component

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.collect

/** Single app-window signal; AppTopBar is a sibling of the NavHost destination. */
object SmoothHeaderProgress {
    var progress by mutableFloatStateOf(0f)
}

/**
 * Tracks the scroll delta without collecting list items or reading layout info.
 * The signal is quantized by the shared token helper to avoid noisy updates.
 */
fun Modifier.smoothHeaderScrollProgress(): Modifier = composed {
    val density = LocalDensity.current
    val scrollPx = remember { mutableFloatStateOf(0f) }
    val connection = remember(density) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                scrollPx.floatValue = (scrollPx.floatValue - consumed.y).coerceAtLeast(0f)
                val raw = SmoothHeaderTokens.progress(scrollPx.floatValue / density.density)
                SmoothHeaderProgress.progress = (raw / SmoothHeaderTokens.ProgressQuantum).toInt() * SmoothHeaderTokens.ProgressQuantum
                return Offset.Zero
            }
        }
    }
    nestedScroll(connection)
}


/** Restorable list-state progress for screens whose top chrome is not app-global. */
@androidx.compose.runtime.Composable
fun rememberSmoothHeaderProgress(state: LazyListState): () -> Float {
    val density = LocalDensity.current
    return remember(state, density) {
        {
            if (state.firstVisibleItemIndex > 0) 1f
            else SmoothHeaderTokens.progress(state.firstVisibleItemScrollOffset / density.density)
        }
    }
}


/** Keeps the app-level tab header in sync with the real restored list offset. */
@androidx.compose.runtime.Composable
fun SmoothHeaderProgressSync(progressProvider: () -> Float) {
    val progress by remember(progressProvider) {
        derivedStateOf { progressProvider().coerceIn(0f, 1f) }
    }
    SideEffect {
        SmoothHeaderProgress.progress =
            (progress / SmoothHeaderTokens.ProgressQuantum).toInt() *
                SmoothHeaderTokens.ProgressQuantum
    }
    LaunchedEffect(progressProvider) {
        snapshotFlow { progressProvider().coerceIn(0f, 1f) }
            .collect { value ->
                SmoothHeaderProgress.progress =
                    (value / SmoothHeaderTokens.ProgressQuantum).toInt() *
                        SmoothHeaderTokens.ProgressQuantum
            }
    }
}


@androidx.compose.runtime.Composable
fun rememberSmoothHeaderProgress(state: LazyGridState): () -> Float {
    val density = LocalDensity.current
    return remember(state, density) {
        {
            if (state.firstVisibleItemIndex > 0) 1f
            else SmoothHeaderTokens.progress(state.firstVisibleItemScrollOffset / density.density)
        }
    }
}
