/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Six masked Haze passes for the shared progressive header. No external blur
 * library is added: the project already depends on Haze (Apache-2.0).
 */
package com.nexamusic.music.ui.component

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.PowerManager
import android.view.Choreographer
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import com.nexamusic.music.ui.ios.compat.AppTheme

private enum class HeaderRenderMode { BLUR, SCRIM }

private object HeaderRenderPolicy {
    @Volatile private var cachedMode: HeaderRenderMode? = null
    val forceScrim = mutableStateOf(false)

    fun mode(context: Context): HeaderRenderMode = cachedMode ?: synchronized(this) {
        cachedMode ?: run {
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val performanceClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.VERSION.MEDIA_PERFORMANCE_CLASS else 0
            val capable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                power?.isPowerSaveMode != true &&
                activity?.isLowRamDevice != true &&
                (performanceClass == 0 || performanceClass >= 31)
            (if (capable) HeaderRenderMode.BLUR else HeaderRenderMode.SCRIM)
                .also { cachedMode = it }
        }
    }

    fun forceScrimForSession() {
        cachedMode = HeaderRenderMode.SCRIM
        forceScrim.value = true
    }
}

/** A frame watchdog can call this to use the scrim for the rest of this process session. */
fun forceSmoothHeaderScrimForSession() = HeaderRenderPolicy.forceScrimForSession()

@OptIn(ExperimentalHazeApi::class)
@Composable
fun ProgressiveBlurLayer(
    hazeState: HazeState,
    pageColor: Color,
    progressProvider: () -> Float,
    modifier: Modifier = Modifier,
    reduceTransparency: Boolean = false,
    startFade: Dp = 0.dp,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = remember(context) { context.findSmoothHeaderActivity()?.window }
    val darkTheme = AppTheme.colorScheme.background.luminance() <= 0.5f

    // System bars are transparent and follow the app theme, not the phone theme.
    SideEffect {
        window?.let { target ->
            target.statusBarColor = AndroidColor.TRANSPARENT
            target.navigationBarColor = AndroidColor.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                target.isStatusBarContrastEnforced = false
                target.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(target, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val density = LocalDensity.current
    val mode = remember(context) { HeaderRenderPolicy.mode(context) }
    // Recompose only when entering/leaving the active state. Scroll ticks are
    // read by graphicsLayer blocks, so lists and blur nodes are not rebuilt.
    val isVisible by remember(progressProvider) {
        derivedStateOf { SmoothHeaderTokens.quantizeProgress(progressProvider()) > 0f }
    }
    if (!isVisible) return
    val readProgress = {
        SmoothHeaderTokens.quantizeProgress(progressProvider())
    }

    val useScrim = reduceTransparency || mode == HeaderRenderMode.SCRIM || HeaderRenderPolicy.forceScrim.value
    val scrimPeak = if (reduceTransparency) {
        SmoothHeaderTokens.ReducedTransparencyOpacity
    } else {
        SmoothHeaderTokens.ScrimFallbackOpacity
    }

    // Measure frame intervals only while the quantized scroll progress is changing.
    // snapshotFlow cancels when the header leaves the active zone, avoiding an idle
    // Choreographer callback loop while a stationary blurred header is on screen.
    LaunchedEffect(useScrim, progressProvider) {
        if (!useScrim) {
            val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            val refreshRate = manager?.defaultDisplay?.refreshRate?.takeIf { it > 0f } ?: 60f
            val frameBudget = (1_000_000_000f / refreshRate *
                SmoothHeaderTokens.SlowFrameIntervalMultiplier).toLong()
            var previousFrame = 0L
            var slowFrames = 0
            snapshotFlow { readProgress() }
                .distinctUntilChanged()
                .collect {
                    val frameTimeNanos = withFrameNanos { it }
                    if (previousFrame != 0L &&
                        frameTimeNanos - previousFrame > frameBudget
                    ) {
                        slowFrames += 1
                    } else {
                        slowFrames = 0
                    }
                    previousFrame = frameTimeNanos
                    if (slowFrames >= SmoothHeaderTokens.ConsecutiveSlowFramesBeforeFallback) {
                        forceSmoothHeaderScrimForSession()
                    }
                }
        }
    }

    val saturation = remember {
        ColorMatrix().apply { setToSaturation(SmoothHeaderTokens.BlurLayerSaturation) }
    }
    val layoutDirection = LocalLayoutDirection.current
    val horizontalFade = if (startFade > 0.dp) {
        Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fadePx = with(density) { startFade.toPx() }
                val fadeWidth = with(density) { SmoothHeaderTokens.RailFadeWidth.toPx() }
                val stops = if (layoutDirection == LayoutDirection.Ltr) {
                    arrayOf(
                        0f to Color.Transparent,
                        ((fadePx - fadeWidth) / size.width).coerceIn(0f, 1f) to Color.Transparent,
                        ((fadePx + fadeWidth) / size.width).coerceIn(0f, 1f) to Color.Black,
                        1f to Color.Black,
                    )
                } else {
                    arrayOf(
                        0f to Color.Black,
                        (1f - (fadePx + fadeWidth) / size.width).coerceIn(0f, 1f) to Color.Black,
                        (1f - (fadePx - fadeWidth) / size.width).coerceIn(0f, 1f) to Color.Transparent,
                        1f to Color.Transparent,
                    )
                }
                drawRect(brush = Brush.horizontalGradient(*stops), blendMode = BlendMode.DstIn)
            }
    } else Modifier

    Box(modifier = modifier.then(horizontalFade)) {
        if (useScrim) {
            // Low-end / API <31 / battery saver / Reduce Transparency fallback.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = readProgress() }
                    .background(
                        Brush.verticalGradient(
                            0f to pageColor.copy(alpha = scrimPeak),
                            1f to pageColor.copy(alpha = 0f),
                        ),
                    ),
            )
        } else {
            // Each pass samples the same header backdrop with a progressively shorter mask.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = SmoothHeaderTokens.blurOpacity(readProgress()) },
            ) {
                SmoothHeaderTokens.BlurLayers.forEach { layer ->
                    val mask = remember(layer.fadeEndFraction) {
                        Brush.verticalGradient(
                            0f to Color.Black,
                            layer.fadeEndFraction to Color.Transparent,
                        )
                    }
                    val style = HazeStyle(tint = null, blurRadius = layer.radius.dp, noiseFactor = 0f)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                colorFilter = ColorFilter.colorMatrix(saturation)
                            }
                            .hazeEffect(state = hazeState, style = style) {
                                this.mask = mask
                                noiseFactor = 0f
                                inputScale = HazeInputScale.Fixed(
                                    if (layer.radius >= 12f) SmoothHeaderTokens.LowResolutionBlurScale
                                    else SmoothHeaderTokens.StandardBlurScale,
                                )
                            },
                    )
                }
            }

            // Theme tint is a separate pass: p opacity, not multiplied by blur-stack opacity.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = readProgress() }
                    .background(
                        Brush.verticalGradient(
                            0f to pageColor.copy(alpha = SmoothHeaderTokens.TintPeakOpacity),
                            1f to pageColor.copy(alpha = 0f),
                        ),
                    ),
            )
        }
    }
}

private tailrec fun Context.findSmoothHeaderActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findSmoothHeaderActivity()
    else -> null
}
