/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Shared geometry and motion contract for the Smooth Blur Header.
 * Values are Compose dp equivalents of the design's point values.
 */
package com.nexamusic.music.ui.component

import androidx.compose.ui.unit.dp

object SmoothHeaderTokens {
    val ScrollDistance = 90.dp
    val HeaderContentHeight = 80.dp
    val BigTitleSize = 40.dp
    val SmallTitleSize = 17.dp
    val BigTitleTravel = 14.dp
    val BigTitleScale = 0.20f
    val SmallTitleTravel = 8.dp
    val ProfileScale = 0.25f
    val BlurStackOpacityMultiplier = 1.3f
    val TintPeakOpacity = 0.62f
    val ScrimFallbackOpacity = 0.85f
    val ReducedTransparencyOpacity = 0.92f
    val ReduceMotionCrossfadeMillis = 150
    val Saturation = 1.2f

    data class BlurLayer(val radius: Float, val fadeEndFraction: Float)

    val BlurLayers = listOf(
        BlurLayer(0.5f, 1.00f),
        BlurLayer(1.5f, 0.84f),
        BlurLayer(3f, 0.68f),
        BlurLayer(6f, 0.52f),
        BlurLayer(12f, 0.36f),
        BlurLayer(24f, 0.20f),
    )

    fun progress(scrollOffsetDp: Float): Float =
        (scrollOffsetDp / ScrollDistance.value).coerceIn(0f, 1f)

    fun blurOpacity(progress: Float): Float =
        (progress * BlurStackOpacityMultiplier).coerceIn(0f, 1f)

    fun bigTitleOpacity(progress: Float): Float =
        (1f - 1.7f * progress).coerceIn(0f, 1f)

    fun bigTitleScale(progress: Float): Float =
        1f - BigTitleScale * progress.coerceIn(0f, 1f)

    fun smallTitleOpacity(progress: Float): Float =
        ((progress - 0.45f) * 2f).coerceIn(0f, 1f)

    fun profileOpacity(progress: Float): Float =
        (1f - 1.6f * progress).coerceIn(0f, 1f)

    fun profileScale(progress: Float): Float =
        1f - ProfileScale * progress.coerceIn(0f, 1f)
}
