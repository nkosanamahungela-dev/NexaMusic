package com.convx.music.ui.onboarding

import android.graphics.PathMeasure
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Exact vector paths supplied for the five NexaMusic intro screens only. */
object NexaIntroIconPaths {
    const val Lyrics = "M4 6h10|M4 11h7|M4 16h5|M17 18V8l3 1|M17 18a2 2 0 1 1-4 0 2 2 0 0 1 4 0z"
    const val Cloud = "M7 18a4.5 4.5 0 0 1-.5-9A6 6 0 0 1 18 9.5a4 4 0 0 1-.5 8.5|M12 10v8|M9 15l3 3 3-3"
    const val Bell = "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z|M10 20a2 2 0 0 0 4 0"
    const val Phone = "M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z|M12 8v6|M9.5 12l2.5 2.5 2.5-2.5"
    const val Note = "M9 17V5l10-2v12|M9 17a3 3 0 1 1-6 0 3 3 0 0 1 6 0z|M19 15a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"
    const val Star = "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"
    const val Chat = "M4 5h16v11H9l-5 4z|M8.5 10.5h.01|M12 10.5h.01|M15.5 10.5h.01"

    const val StrokeWidth = 1.9f
    const val DrawDelayMs = 500
    const val DrawDurationMs = 1100
}

private fun introIconPaths(name: String): List<String> = when (name) {
    "lyrics" -> NexaIntroIconPaths.Lyrics
    "cloud", "cloud-download" -> NexaIntroIconPaths.Cloud
    "bell" -> NexaIntroIconPaths.Bell
    "phone", "phone-download" -> NexaIntroIconPaths.Phone
    "note", "music-note" -> NexaIntroIconPaths.Note
    "star" -> NexaIntroIconPaths.Star
    "chat" -> NexaIntroIconPaths.Chat
    else -> error("Unknown Nexa intro line icon: $name")
}.split('|')

/**
 * Reusable self-drawing vector icon. The caller controls play so off-screen intro
 * pages can stop the draw animation; setting play true again replays it.
 */
@Composable
fun AnimatedLineIcon(
    name: String,
    iconSize: androidx.compose.ui.unit.Dp,
    colour: Color,
    play: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    var progress by remember(name) { mutableFloatStateOf(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(name, play, reduceMotion) {
        if (reduceMotion) {
            progress = 1f
        } else if (play) {
            progress = 0f
            delay(NexaIntroIconPaths.DrawDelayMs.toLong())
            progress = 1f
        } else {
            progress = 0f
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else NexaIntroIconPaths.DrawDurationMs,
            easing = FastOutSlowInEasing,
        ),
        label = "nexa-line-icon-draw",
    )
    val paths = remember(name) {
        introIconPaths(name).map { d -> PathParser().parsePathString(d).toPath() }
    }
    Canvas(
        modifier = modifier.size(iconSize).semantics { contentDescription = name },
    ) {
        drawContext.transform.scale(this.size.width / 24f, this.size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero)
        paths.forEach { source ->
            val nativePath = source.asAndroidPath()
            val measure = PathMeasure(nativePath, false)
            val length = measure.length
            val segment = android.graphics.Path()
            if (animatedProgress >= 1f || reduceMotion) {
                drawPath(source, colour, style = Stroke(NexaIntroIconPaths.StrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else if (animatedProgress > 0f && length > 0f) {
                measure.getSegment(0f, length * animatedProgress, segment, true)
                drawPath(segment.asComposePath(), colour, style = Stroke(NexaIntroIconPaths.StrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
