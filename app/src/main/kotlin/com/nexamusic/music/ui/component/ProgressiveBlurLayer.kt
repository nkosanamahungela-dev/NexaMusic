/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Shared six-pass progressive backdrop blur. All passes sample the same Haze
 * source; the progressively shorter masks make the lower edge melt away.
 */
package com.nexamusic.music.ui.component

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

private enum class HeaderRenderMode { BLUR, SCRIM }

/** Resolve device capability once per process; accessibility overrides remain live. */
private object HeaderRenderPolicy {
    @Volatile private var cachedMode: HeaderRenderMode? = null

    fun mode(context: Context): HeaderRenderMode = cachedMode ?: synchronized(this) {
        cachedMode ?: run {
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val capable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                power?.isPowerSaveMode != true &&
                activity?.isLowRamDevice != true
            (if (capable) HeaderRenderMode.BLUR else HeaderRenderMode.SCRIM)
                .also { cachedMode = it }
        }
    }

    fun forceScrimForSession() {
        cachedMode = HeaderRenderMode.SCRIM
    }
}

/** Call from a frame-time watchdog after repeated missed frames to disable blur for this session. */
fun forceSmoothHeaderScrimForSession() = HeaderRenderPolicy.forceScrimForSession()

/**
 * Renders nothing while progress is zero. The scrim fallback is theme-derived;
 * no black surface or fixed header rectangle is introduced.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun ProgressiveBlurLayer(
    hazeState: HazeState,
    pageColor: Color,
    progressProvider: () -> Float,
    modifier: Modifier = Modifier,
    reduceTransparency: Boolean = false,
    startFade: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val mode = remember(context) { HeaderRenderPolicy.mode(context) }
    val progress by remember { derivedStateOf { progressProvider().coerceIn(0f, 1f) } }

    if (progress <= 0f) return

    val useScrim = reduceTransparency || mode == HeaderRenderMode.SCRIM
    val scrimPeak = if (reduceTransparency) {
        SmoothHeaderTokens.ReducedTransparencyOpacity
    } else {
        SmoothHeaderTokens.ScrimFallbackOpacity
    }
    val stackOpacity = SmoothHeaderTokens.blurOpacity(progress)
    val saturation = remember {
        ColorMatrix().apply { setToSaturation(SmoothHeaderTokens.BlurLayerSaturation) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (useScrim) 1f else stackOpacity },
    ) {
        if (useScrim) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to pageColor.copy(alpha = scrimPeak * progress),
                            1f to pageColor.copy(alpha = 0f),
                        ),
                    ),
            )
        } else {
            SmoothHeaderTokens.BlurLayers.forEach { layer ->
                val fadeEnd = layer.fadeEndFraction
                val mask = remember(fadeEnd) {
                    Brush.verticalGradient(
                        0f to Color.Black,
                        fadeEnd.coerceIn(0.001f, 1f) to Color.Transparent,
                    )
                }
                val radiusPx = with(density) { layer.radius.dp.toPx() }
                val style = remember(radiusPx) {
                    HazeStyle(blurRadius = radiusPx, noiseFactor = 0f)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            colorFilter = ColorFilter.colorMatrix(saturation)
                        }
                        .hazeEffect(state = hazeState, style = style) {
                            this.mask = mask
                            this.noiseFactor = 0f
                            this.inputScale = HazeInputScale.Fixed(
                                if (layer.radius >= 12f) 0.5f else 1f,
                            )
                        },
                )
            }
        }

        // Theme tint is independent from the blur and always follows the same p.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to pageColor.copy(alpha = SmoothHeaderTokens.TintPeakOpacity * progress),
                        1f to pageColor.copy(alpha = 0f),
                    ),
                ),
        )
    }
}
