/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.nexamusic.music.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.calculateTopPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

typealias ScrollHeaderPinnedRowContent = @Composable () -> Unit

val LocalScrollHeaderProgressProvider = compositionLocalOf<() -> Float> { { 0f } }
private val LocalScrollHeaderPinnedRowRegistrar =
    compositionLocalOf<(String, ScrollHeaderPinnedRowContent?) -> Unit> { { _, _ -> } }

/** Registers a destination-owned row beneath the compact title. */
@Composable
fun PinnedScrollHeaderRow(
    screenKey: String,
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val register = LocalScrollHeaderPinnedRowRegistrar.current
    val latestContent = rememberUpdatedState(content)
    val stableContent: ScrollHeaderPinnedRowContent = remember(screenKey) {
        { latestContent.value.invoke() }
    }
    SideEffect { register(screenKey, if (visible) stableContent else null) }
    DisposableEffect(screenKey, register) {
        onDispose { register(screenKey, null) }
    }
}

/** Shared entry-scoped header and pinned-row host. */
@Composable
fun ScrollHeaderScaffold(
    screenKey: String,
    modifier: Modifier = Modifier,
    pinnedRow: (@Composable BoxScope.(() -> Float) -> Unit)? = null,
    content: @Composable BoxScope.(() -> Float) -> Unit,
) {
    val progressProvider = remember(screenKey) { { SmoothHeaderProgress.progressFor(screenKey) } }
    var registeredPinnedRow by remember(screenKey) {
        mutableStateOf<ScrollHeaderPinnedRowContent?>(null)
    }
    val register: (String, ScrollHeaderPinnedRowContent?) -> Unit = remember(screenKey) {
        { owner, row -> if (owner == screenKey) registeredPinnedRow = row }
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scaledTitleHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
        SmoothHeaderTokens.BigTitleLineHeight.toDp()
    }
    val headerContentHeight = maxOf(SmoothHeaderTokens.HeaderContentHeight, scaledTitleHeight + 38.dp)

    CompositionLocalProvider(
        LocalScrollHeaderProgressProvider provides progressProvider,
        LocalScrollHeaderPinnedRowRegistrar provides register,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            content(progressProvider)
            pinnedRow?.invoke(this, progressProvider)
            AnimatedVisibility(
                visible = registeredPinnedRow != null,
                enter = fadeIn(tween(140)),
                exit = fadeOut(tween(100)),
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .padding(top = topInset + headerContentHeight),
            ) {
                Box(Modifier.fillMaxWidth().height(SmoothHeaderTokens.PinnedRowHeight)) {
                    registeredPinnedRow?.invoke()
                }
            }
        }
    }
}
