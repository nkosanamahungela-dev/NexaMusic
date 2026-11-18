/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Shared destination wrapper for all scroll-driven top chrome. The navigation
 * entry key keeps progress isolated while destinations are retained in the
 * back stack. The page owns its scroll state and publishes its value through
 * SmoothHeaderProgressSync; this scaffold never guesses from nested deltas.
 */
package com.nexamusic.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

val LocalScrollHeaderProgressProvider = compositionLocalOf<() -> Float>({ { 0f } })

/**
 * Wraps the destination content in one shared header scope. Existing page
 * layouts retain their own content insets and artwork; the app-level header
 * and detail chrome read the same entry-scoped progress source.
 *
 * [pinnedRow] is reserved for pages whose current design pins a filter/source
 * selector directly beneath the compact title (Search uses this slot).
 */
@Composable
fun ScrollHeaderScaffold(
    screenKey: String,
    modifier: Modifier = Modifier,
    pinnedRow: (@Composable BoxScope.(() -> Float) -> Unit)? = null,
    content: @Composable BoxScope.(() -> Float) -> Unit,
) {
    val progressProvider = remember(screenKey) {
        { SmoothHeaderProgress.progressFor(screenKey) }
    }
    CompositionLocalProvider(
        LocalScrollHeaderProgressProvider provides progressProvider,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            content(progressProvider)
            pinnedRow?.invoke(this, progressProvider)
        }
    }
}
