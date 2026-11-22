/*
 * Adapted from BitChord PlayerSkipMotion.kt
 * Copyright (C) BitChord contributors.
 * Original: https://github.com/kushagrasinghx/BitChord
 * Licensed under GPL-3.0-or-later; see LICENSE.
 *
 * The conveyor motion, retargeting, contour splitting and draw geometry are
 * retained from upstream. Lifecycle visibility is adapted to Android.
 */
package com.nexamusic.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.isFinished
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.nexamusic.music.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import kotlinx.coroutines.flow.first

/**
 * Tuned to the requested conveyor choreography, not published Apple constants.
 * Progress is elapsed time / 600ms on an isolated tap; only a retarget changes
 * its speed. All geometry comes from the existing drawable, never a new triangle.
 */
private object SkipMotion {
    const val DurationMs = 600
    // Give FRONT 210ms: gentler initial acceleration and smaller adjacent-frame
    // changes at every refresh rate, without retiming SURVIVOR or NEW.
    const val FrontGone = 0.35f
    const val Settled = 0.90f
    const val NewOnset = 0.28f
    const val CircleDiameter = 1.7f
    const val CircleAlpha = 0.08f
    // A broad, uniform translucent disc; soften only the outer 8% of its radius.
    const val CircleFeatherStart = 0.92f
    const val CircleInMs = 45
    const val CircleOutMs = 100
    const val CircleHoldMs = 125
    // A cubic Hermite easing with start slope <= 2 and end slope 1 is monotone.
    const val MaxStartSlope = 2f
    const val MillisPerSecond = 1000f
}

/** Visual intent only. The owner still calls its original playback action on click. */
class PlayerSkipMotion internal constructor(
    internal val active: State<Boolean>,
    internal val reduceMotion: State<Boolean>,
) {
    // AnimationState retains the last frame time as well as value/velocity on
    // cancellation. Retargets can continue without another zero-time frame.
    internal var cycles by mutableStateOf(AnimationState(0f))
    internal var requestedCycles by mutableIntStateOf(0)
    internal var targetCycles by mutableStateOf(0f)

    fun onTap() {
        targetCycles = playerSkipTarget(cycles.value, targetCycles)
        requestedCycles++
    }
}

/** Keep the interrupted cycle and at most one pending cycle, never a tap backlog. */
internal fun playerSkipTarget(cycles: Float, target: Float): Float {
    val boundary = cycles - cycles % 1f + 1f
    return if (cycles < target) boundary + 1f else boundary
}

@Composable
fun rememberPlayerSkipMotion(active: Boolean, reduceMotion: Boolean): PlayerSkipMotion {
    val foreground = rememberNexaPlayerForeground()
    val activeState = rememberUpdatedState(active && foreground)
    val reduceMotionState = rememberUpdatedState(reduceMotion)
    val motion = remember { PlayerSkipMotion(activeState, reduceMotionState) }
    LaunchedEffect(motion.requestedCycles, active, foreground, reduceMotion) {
        val cycles = motion.cycles
        val target = motion.targetCycles
        val distance = target - cycles.value
        if (!active || !foreground || reduceMotion) {
            // Consume hidden/reduced-motion taps with no old clock to replay.
            motion.cycles = AnimationState(target)
        } else if (distance > 0f) {
            val continuing = !cycles.isFinished
            val velocity = if (continuing && cycles.velocity > 0f) cycles.velocity else {
                distance * SkipMotion.MillisPerSecond / SkipMotion.DurationMs
            }
            // Finish within one tap window, even when several taps coalesce into
            // one frame. Shorten it if needed to retain velocity without overshoot.
            val duration = minOf(
                SkipMotion.DurationMs,
                (SkipMotion.MaxStartSlope * distance * SkipMotion.MillisPerSecond / velocity)
                    .toInt(),
            ).coerceAtLeast(1)
            val slope = (velocity * duration / (distance * SkipMotion.MillisPerSecond))
                .coerceIn(0f, SkipMotion.MaxStartSlope)
            cycles.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = duration,
                    easing = Easing { t ->
                        // Hermite(0, 1, slope, 1). At slope=1 this is exactly linear.
                        (slope - 1f) * t * t * t + 2f * (1f - slope) * t * t + slope * t
                    },
                ),
                // Use the interrupted frame's timestamp, not a fresh frame at
                // play time zero. The Hermite slope above carries its speed.
                sequentialAnimation = continuing,
            )
        }
    }
    return motion
}

internal data class SkipPose(
    val frontScale: Float,
    val frontAlpha: Float,
    val survivorTravel: Float,
    val newScale: Float,
    val newAlpha: Float,
)

/** Pure, non-overshooting conveyor pose; endpoint contours are the original slots. */
internal fun playerSkipPose(progress: Float): SkipPose {
    val p = progress.coerceIn(0f, 1f)
    val contraction = (p / SkipMotion.FrontGone).coerceIn(0f, 1f)
    val frontScale = 1f - skipSmoothStep(contraction)
    // Scale already reduces visible area quadratically. Fade later so FRONT
    // remains a recognizable triangle through the middle of its contraction.
    val frontAlpha = 1f - skipSmoothStep(contraction * contraction)
    val travel = skipTravel(p / SkipMotion.Settled)
    val growth = skipSmoothStep(
        (p - SkipMotion.NewOnset) / (SkipMotion.Settled - SkipMotion.NewOnset),
    )
    return SkipPose(frontScale, frontAlpha, travel, growth, growth)
}

private fun skipSmoothStep(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun skipTravel(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    // One cubic Hermite trip (start slope 1, end slope 0): less front-loaded
    // than the old quadratic, without a midpoint stop or endpoint overshoot.
    return t + t * t * (1f - t)
}

/**
 * Use the original painter at rest, including the final 10% of each cycle.
 * During motion, split its actual contours, not clipped halves of the painter:
 * the current rounded arrowheads' bounding boxes overlap slightly.
 */
@Composable
fun PlayerSkipGlyph(
    motion: PlayerSkipMotion,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    previous: Boolean = false,
) {
    val resource = if (previous) R.drawable.ic_player_previous else R.drawable.ic_player_next
    val vector = vectorResource(resource)
    // Compose resources can return an empty vector while their asynchronous
    // resource state loads. Keep the original painter, rather than index root[0].
    if (vector.root.size == 0) {
        Icon(
            painter = painterResource(resource),
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier,
        )
        return
    }
    val nodes = (vector.root[0] as VectorPath).pathData
    // Built once per drawable and kept across recompositions. A drawWithCache
    // block would be rebuilt every time this recomposes (its lambda is new).
    val arrows = remember(nodes) { SkipArrows(nodes) }
    Icon(
        painter = painterResource(resource),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.drawWithContent {
            val p = motion.cycles.value % 1f
            if (!motion.active.value || motion.reduceMotion.value || p == 0f || p >= SkipMotion.Settled) {
                drawContent()
            } else {
                val pose = playerSkipPose(p)
                // Nothing here allocates or rebuilds a Path: every frame is three
                // draws of two cached paths under canvas transforms.
                scale(size.width / vector.viewportWidth, size.height / vector.viewportHeight,
                    pivot = Offset.Zero) {
                    if (pose.frontAlpha > 0f && pose.frontScale > 0f) {
                        scale(pose.frontScale, pivot = arrows.frontCenter) {
                            drawPath(arrows.front, tint, alpha = pose.frontAlpha)
                        }
                    }
                    // The two arrowheads are the same contour 419.25 (next) /
                    // -415.95 (previous) viewport units apart, within 0.1 of
                    // 960 units: far below a pixel, so the trailing one is
                    // translated rather than re-interpolated node by node.
                    translate(arrows.shiftX * pose.survivorTravel, arrows.shiftY * pose.survivorTravel) {
                        drawPath(arrows.back, tint)
                    }
                    if (pose.newAlpha > 0f && pose.newScale > 0f) {
                        scale(pose.newScale, pivot = arrows.backCenter) {
                            drawPath(arrows.back, tint, alpha = pose.newAlpha)
                        }
                    }
                }
            }
        },
    )
}

/** The drawable's two arrowhead contours, split once at its last MoveTo. */
private class SkipArrows(nodes: List<PathNode>) {
    val back: Path
    val front: Path
    val backCenter: Offset
    val frontCenter: Offset
    val shiftX: Float
    val shiftY: Float

    init {
        val split = nodes.indexOfLast { it is PathNode.MoveTo }
        back = nodes.subList(0, split).toPath()
        front = nodes.subList(split, nodes.size).toPath()
        backCenter = back.getBounds().center
        frontCenter = front.getBounds().center
        shiftX = frontCenter.x - backCenter.x
        shiftY = frontCenter.y - backCenter.y
    }
}


/** Lifecycle-aware visibility gate matching BitChord's foreground motion policy. */
@Composable
private fun rememberNexaPlayerForeground(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return foreground
}
