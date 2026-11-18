/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.nexamusic.music.ui.component

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

data class SmoothHeaderTitleMotion(
    val largeAlpha: () -> Float,
    val compactAlpha: () -> Float,
    val transformsEnabled: Boolean,
)

/**
 * Respect Android's system reduce-motion setting. Only the reduced-motion
 * handoff uses a 150ms animation; normal mode remains finger-synchronous.
 */
@Composable
fun rememberSmoothHeaderTitleMotion(
    progressProvider: () -> Float,
): SmoothHeaderTitleMotion {
    val reduceMotion = !ValueAnimator.areAnimatorsEnabled()
    val compactThreshold by remember(progressProvider) {
        derivedStateOf { progressProvider() >= 0.5f }
    }
    val compactFade by animateFloatAsState(
        targetValue = if (reduceMotion && compactThreshold) 1f else 0f,
        animationSpec = tween(durationMillis = SmoothHeaderTokens.ReduceMotionCrossfadeMillis),
        label = "smoothHeaderReducedMotion",
    )
    return remember(reduceMotion, compactFade, progressProvider) {
        SmoothHeaderTitleMotion(
            largeAlpha = {
                if (reduceMotion) 1f - compactFade
                else SmoothHeaderTokens.bigTitleOpacity(progressProvider())
            },
            compactAlpha = {
                if (reduceMotion) compactFade
                else SmoothHeaderTokens.smallTitleOpacity(progressProvider())
            },
            transformsEnabled = !reduceMotion,
        )
    }
}
