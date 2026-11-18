/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Shared six-pass progressive backdrop blur. All passes sample the same Haze
 * source; the progressively shorter masks make the lower edge melt away.
 */
package com.nexamusic.music.ui.component

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.PowerManager
import android.view.Choreographer
import android.view.WindowManager
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nexamusic.music.ui.ios.compat.AppTheme

private enum class HeaderRenderMode { BLUR, SCRIM }

/** Resolve device capability once per process; accessibility overrides remain live. */
private object HeaderRenderPolicy {
    @Volatile private var cachedMode: HeaderRenderMode? = null
    val forceScrim = mutableStateOf(false)

    fun mode(context: Context): HeaderRenderMode = cachedMode ?: synchronized(this) {
        cachedMode ?: run {
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val performanceClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.VERSION.MEDIA_PERFORMANCE_CLASS
            } else 0
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
    val view = LocalView.current
    val window = remember(context) { context.findSmoothHeaderActivity()?.window }
    val darkTheme = AppTheme.colorScheme.background.luminance() <= 0.5f
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
    val progress by remember(progressProvider) { derivedStateOf { progressProvider().coerceIn(0f, 1f) } }

    if (progress <= 0f) return

    val useScrim = reduceTransparency || mode == HeaderRenderMode.SCRIM || HeaderRenderPolicy.forceScrim.value
    val scrimPeak = if (reduceTransparency) {
        SmoothHeaderTokens.ReducedTransparencyOpacity
    } else {
        SmoothHeaderTokens.ScrimFallbackOpacity
    }
    val stackOpacity = SmoothHeaderTokens.blurOpacity(progress)
    DisposableEffect(useScrim, progress > 0f) {
        if (useScrim || progress <= 0f) {
            onDispose { }
        } else {
            val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            val refreshRate = manager?.defaultDisplay?.refreshRate?.takeIf { it > 0f } ?: 60f
            val slowFrameNanos = (1_000_000_000f / refreshRate * SmoothHeaderTokens.SlowFrameIntervalMultiplier).toLong()
            val choreographer = Choreographer.getInstance()
            var lastFrame = 0L
            var consecutiveSlowFrames = 0
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (lastFrame != 0L && frameTimeNanos - lastFrame > slowFrameNanos) {
                        consecutiveSlowFrames += 1
                    } else {
                        consecutiveSlowFrames = 0
                    }
                    lastFrame = frameTimeNanos
                    if (consecutiveSlowFrames >= SmoothHeaderTokens.ConsecutiveSlowFramesBeforeFallback) {
                        forceSmoothHeaderScrimForSession()
                    } else {
                        choreographer.postFrameCallback(this)
                    }
                }
            }
            choreographer.postFrameCallback(callback)
            onDispose { choreographer.removeFrameCallback(callback) }
        }
    }

    val saturation = remember {
        ColorMatrix().apply { setToSaturation(SmoothHeaderTokens.BlurLayerSaturation) }
    }

    val horizontalFade = if (startFade > 0.dp) {
        Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fadePx = with(density) { startFade.toPx() }
                val fadeWidth = with(density) { SmoothHeaderTokens.RailFadeWidth.toPx() }
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        ((fadePx - fadeWidth) / size.width).coerceIn(0f, 1f) to Color.Transparent,
                        ((fadePx + fadeWidth) / size.width).coerceIn(0f, 1f) to Color.Black,
                        1f to Color.Black,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
    } else Modifier

    Box(
        modifier = modifier
            .then(horizontalFade)
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
                val style = remember {
                    HazeStyle(blurRadius = 0.dp, noiseFactor = 0f)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            colorFilter = ColorFilter.colorMatrix(saturation)
                        }
                        .hazeEffect(state = hazeState, style = style) {
                            this.blurRadius = radiusPx
                            this.mask = mask
                            this.noiseFactor = 0f
                            this.inputScale = HazeInputScale.Fixed(
                                if (layer.radius >= 12f) SmoothHeaderTokens.LowResolutionBlurScale else SmoothHeaderTokens.StandardBlurScale,
                            )
                        },
                )
            }
        }

        // Theme tint is independent from the blur and always follows the same p.
        // The fallback gradient already contains its specified 85% / 92% tint.
        if (!useScrim) {
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
}


private tailrec fun Context.findSmoothHeaderActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findSmoothHeaderActivity()
    else -> null
}
