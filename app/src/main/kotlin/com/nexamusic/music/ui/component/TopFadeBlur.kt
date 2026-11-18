/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.nexamusic.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexamusic.music.ui.component.backdrop.Backdrop
import com.nexamusic.music.utils.rememberPreference
import com.nexamusic.music.constants.ReduceDynamicBlurKey
import dev.chrisbanes.haze.HazeState

/**
 * Shared progressive header zone. The old fixed-height colour band and single
 * blur ramp are replaced by the six masked passes in [ProgressiveBlurLayer].
 *
 * [backdrop] and [scrimColor] remain in the signature for existing call sites;
 * the current theme page colour is deliberately the only tint source.
 */
@Composable
fun TopFadeBlur(
    hazeState: HazeState,
    pageColor: Color,
    modifier: Modifier = Modifier,
    scrimColor: Color = pageColor,
    scrimOpacity: Float = SmoothHeaderTokens.TintPeakOpacity,
    heightOverride: Dp? = null,
    backdrop: Backdrop? = null,
    startFade: Dp = 0.dp,
    progressProvider: () -> Float = { SmoothHeaderProgress.progress },
) {
    val reduceDynamicBlur = rememberPreference(ReduceDynamicBlurKey, false).value
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val zoneHeight = heightOverride ?: (topInset + SmoothHeaderTokens.HeaderContentHeight)

    Box(modifier.fillMaxWidth().height(zoneHeight)) {
        ProgressiveBlurLayer(
            hazeState = hazeState,
            pageColor = pageColor,
            progressProvider = progressProvider,
            modifier = Modifier.fillMaxSize(),
            reduceTransparency = reduceDynamicBlur,
            startFade = startFade,
        )
    }
}
