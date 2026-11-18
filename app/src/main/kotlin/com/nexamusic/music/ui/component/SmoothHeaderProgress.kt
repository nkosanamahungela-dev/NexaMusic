/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Scroll progress is scoped to a NavBackStackEntry. The app-level tab header
 * reads only the active entry's value, so retained destinations never share p.
 */
package com.nexamusic.music.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

/** Per-destination progress plus a legacy signal for older, local-only chrome. */
object SmoothHeaderProgress {
    private val byScreen = mutableStateMapOf<String, Float>()

    /** Legacy value; the persistent tab header must use [progressFor]. */
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
 * Nested-scroll bridge for custom containers without a Compose scroll-state API.
 * Pass the owning NavBackStackEntry.id; the default remains isolated legacy use.
 */
fun Modifier.smoothHeaderScrollProgress(screenKey: String? = null): Modifier = composed {
    val density = LocalDensity.current
    val scrollPx = remember { mutableFloatStateOf(0f) }
    val connection = remember(density, screenKey) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                scrollPx.floatValue = (scrollPx.floatValue - consumed.y).coerceAtLeast(0f)
                val value = SmoothHeaderTokens.progress(scrollPx.floatValue / density.density)
                if (screenKey == null) SmoothHeaderProgress.progress = value
                else SmoothHeaderProgress.setForScreen(screenKey, value)
                return Offset.Zero
            }
        }
    }
    nestedScroll(connection)
}

/** Reads restored list position directly; no animated or delta-based warm-up. */
@Composable
fun rememberSmoothHeaderProgress(state: LazyListState): () -> Float {
    val density = LocalDensity.current
    return remember(state, density) {
        {
            if (state.firstVisibleItemIndex > 0) 1f
            else SmoothHeaderTokens.progress(
                state.firstVisibleItemScrollOffset.coerceAtLeast(0) / density.density,
            )
        }
    }
}

/** Reads restored grid position directly; short grids and top overscroll stay clear. */
@Composable
fun rememberSmoothHeaderProgress(state: LazyGridState): () -> Float {
    val density = LocalDensity.current
    return remember(state, density) {
        {
            if (state.firstVisibleItemIndex > 0) 1f
            else SmoothHeaderTokens.progress(
                state.firstVisibleItemScrollOffset.coerceAtLeast(0) / density.density,
            )
        }
    }
}

/** Publishes one screen's actual progress under its unique back-stack entry key. */
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
    LaunchedEffect(progressProvider, screenKey) {
        kotlinx.coroutines.flow.snapshotFlow { progressProvider().coerceIn(0f, 1f) }
            .distinctUntilChanged()
            .collect { value ->
                val quantized = SmoothHeaderTokens.quantizeProgress(value)
                if (screenKey == "legacy") SmoothHeaderProgress.progress = quantized
                else SmoothHeaderProgress.setForScreen(screenKey, quantized)
            }
    }
}
