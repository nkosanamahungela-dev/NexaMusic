/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * The only entry point for Haze sources and effects used by NexaMusic.
 */
package com.nexamusic.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import com.nexamusic.music.BuildConfig
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

internal data class HeaderHazeStyle(
    val pageColor: Color,
    val hazeStyle: HazeStyle,
    val blurAllowed: Boolean,
)

/** Pure resolver so both the root default and every header use identical validation. */
internal fun createHeaderHazeStyle(
    pageColor: Color,
    darkTheme: Boolean,
    debugBuild: Boolean,
): HeaderHazeStyle {
    val isOpaqueSpecified = pageColor.isSpecified && pageColor.alpha == 1f
    check(isOpaqueSpecified || !debugBuild) {
        "Smooth Blur Header requires a specified, fully opaque page colour."
    }

    // Release builds never pass an invalid colour to Haze. The affected header
    // switches to its gradient scrim instead of risking a draw-time exception.
    val safePageColor = if (isOpaqueSpecified) {
        pageColor
    } else if (darkTheme) {
        Color.Black
    } else {
        Color.White
    }

    return HeaderHazeStyle(
        pageColor = safePageColor,
        hazeStyle = HazeStyle(
            backgroundColor = safePageColor,
            tint = HazeTint(safePageColor.copy(alpha = SmoothHeaderTokens.HeaderTintPeak)),
            blurRadius = SmoothHeaderTokens.HeaderBlurRadius,
            noiseFactor = SmoothHeaderTokens.HeaderBlurNoiseFactor,
        ),
        blurAllowed = isOpaqueSpecified,
    )
}

/**
 * Rebuilt synchronously when the app page colour or Appearance setting changes.
 * Call before any restored-scroll early return so the first scrolled frame has a
 * fully resolved style.
 */
@Composable
internal fun rememberHeaderHazeStyle(
    pageColor: Color,
    darkTheme: Boolean,
): HeaderHazeStyle = remember(pageColor, darkTheme) {
    createHeaderHazeStyle(
        pageColor = pageColor,
        darkTheme = darkTheme,
        debugBuild = BuildConfig.DEBUG,
    )
}

/** The single supported Haze source modifier entry point. */
internal fun Modifier.nexaHazeSource(state: HazeState): Modifier = hazeSource(state)

/**
 * The single supported Haze effect modifier entry point. Haze 1.3.1 supports
 * HazeProgressive, so the header uses one 24dp effect with a top-to-bottom ramp.
 */
@OptIn(ExperimentalHazeApi::class)
internal fun Modifier.nexaHeaderBlur(
    state: HazeState,
    headerStyle: HeaderHazeStyle,
    inputScale: Float = SmoothHeaderTokens.HeaderBlurInputScale,
): Modifier {
    if (!headerStyle.blurAllowed) {
        return drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to headerStyle.pageColor.copy(alpha = SmoothHeaderTokens.ScrimFallbackOpacity),
                    1f to Color.Transparent,
                ),
            )
        }
    }

    return hazeEffect(state = state, style = headerStyle.hazeStyle) {
        // Highest-precedence guard: even an accidental future style/local regression
        // cannot leave the actual draw scope with an unspecified background.
        backgroundColor = headerStyle.pageColor
        noiseFactor = SmoothHeaderTokens.HeaderBlurNoiseFactor
        progressive = HazeProgressive.verticalGradient(
            startIntensity = 1f,
            endIntensity = 0f,
            preferPerformance = true,
        )
        this.inputScale = HazeInputScale.Fixed(inputScale)
    }
}
