/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Single source of truth for every Smooth Blur Header dimension, threshold,
 * opacity curve, blur radius, fallback and motion duration.
 */
package com.nexamusic.music.ui.component

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

object SmoothHeaderTokens {
    // Geometry
    val HeaderScrollDistance = 90.dp
    val ScrollDistance = HeaderScrollDistance
    val HeaderContentHeight = 80.dp
    val TopBarContentHeight = 52.dp
    val PinnedRowHeight = 48.dp
    val RailFadeWidth = 28.dp

    // Scroll progress and compositing
    const val ProgressQuantum = 0.001f
    const val HeaderTintPeak = 0.62f
    const val HeaderBlurOpacityMultiplier = 1.3f
    const val ScrimFallbackPeak = 0.85f
    const val ReducedTransparencyPeak = 0.92f
    const val BlurLayerSaturation = 1.2f
    const val LowResolutionBlurScale = 0.5f
    const val StandardBlurScale = 1f
    // Stable aliases retained for existing call sites during the migration.
    val HeaderTopInsetContentHeight = HeaderContentHeight
    val BlurStackOpacityMultiplier = HeaderBlurOpacityMultiplier
    val TintPeakOpacity = HeaderTintPeak
    val ScrimFallbackOpacity = ScrimFallbackPeak
    val ReducedTransparencyOpacity = ReducedTransparencyPeak
    const val BigTitleLetterSpacingEm = -0.04f
    const val Saturation = BlurLayerSaturation

    // Title and profile motion
    val BigTitleSize = 40.sp
    val BigTitleLineHeight = 46.sp
    val SmallTitleSize = 17.sp
    val SmallTitleSideInset = 88.dp
    val BigTitleLetterSpacing = (-1.6).sp
    val BigTitleTravel = 14.dp
    const val BigTitleScale = 0.20f
    const val LargeTitleOpacitySlope = 1.7f
    const val SmallTitleStartProgress = 0.45f
    const val SmallTitleOpacitySlope = 2f
    val SmallTitleTravel = 8.dp
    const val ProfileOpacitySlope = 1.6f
    const val ProfileScale = 0.25f
    const val ReduceMotionCrossfadeMillis = 150

    // Frame-time fallback: repeated frames slower than 1.8 refresh intervals.
    const val SlowFrameIntervalMultiplier = 1.8f
    const val ConsecutiveSlowFramesBeforeFallback = 3

    data class BlurLayer(val radius: Float, val fadeEndFraction: Float)

    val BlurLayers = listOf(
        BlurLayer(0.5f, 1.00f),
        BlurLayer(1.5f, 0.84f),
        BlurLayer(3f, 0.68f),
        BlurLayer(6f, 0.52f),
        BlurLayer(12f, 0.36f),
        BlurLayer(24f, 0.20f),
    )

    fun quantizeProgress(progress: Float): Float =
        (progress.coerceIn(0f, 1f) / ProgressQuantum).roundToInt() * ProgressQuantum

    fun progress(scrollOffsetDp: Float): Float =
        quantizeProgress(scrollOffsetDp / HeaderScrollDistance.value)

    fun blurOpacity(progress: Float): Float =
        (progress * HeaderBlurOpacityMultiplier).coerceIn(0f, 1f)

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
