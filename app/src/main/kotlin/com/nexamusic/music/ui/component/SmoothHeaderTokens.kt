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
    val ScrollDistance = HeaderScrollDistance
    // All design dimensions are Compose dp equivalents of the supplied point values.
    val HeaderContentHeight = 80.dp
    val HeaderScrollDistance = 90.dp
    const val ProgressQuantum = 0.001f
    const val HeaderTintPeak = 0.62f
    const val HeaderBlurOpacityMultiplier = 1.3f
    const val ScrimFallbackPeak = 0.85f
    const val ReducedTransparencyPeak = 0.92f
    const val LowResolutionBlurScale = 0.5f
    const val StandardBlurScale = 1f
    const val LargeTitleOpacitySlope = 1.7f
    const val SmallTitleStartProgress = 0.45f
    const val SmallTitleOpacitySlope = 2f
    const val ProfileOpacitySlope = 1.6f
    val HeaderTopInsetContentHeight = HeaderContentHeight
    val BigTitleLetterSpacingEm = -0.04f
    val BlurLayerSaturation = 1.2f
    val BigTitleSize = 40.dp
    val SmallTitleSize = 17.dp
    val BigTitleTravel = 14.dp
    val BigTitleScale = 0.20f
    val SmallTitleTravel = 8.dp
    val ProfileScale = 0.25f
    val BlurStackOpacityMultiplier = HeaderBlurOpacityMultiplier
    val TintPeakOpacity = HeaderTintPeak
    val ScrimFallbackOpacity = ScrimFallbackPeak
    val ReducedTransparencyOpacity = ReducedTransparencyPeak
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
        (1f - LargeTitleOpacitySlope * progress).coerceIn(0f, 1f)

    fun bigTitleScale(progress: Float): Float =
        1f - BigTitleScale * progress.coerceIn(0f, 1f)

    fun smallTitleOpacity(progress: Float): Float =
        ((progress - SmallTitleStartProgress) * SmallTitleOpacitySlope).coerceIn(0f, 1f)

    fun profileOpacity(progress: Float): Float =
        (1f - ProfileOpacitySlope * progress).coerceIn(0f, 1f)

    fun profileScale(progress: Float): Float =
        1f - ProfileScale * progress.coerceIn(0f, 1f)
}
