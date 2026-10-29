package com.convx.music.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.music.lyrics.LyricsEntry
import com.convx.music.ui.ios.compat.Text
import kotlin.math.abs

/**
 * Perspective lyrics renderer: the active line sits on the centre of a curved field,
 * while neighbouring lines rotate away and shrink toward the edges like a fisheye lens.
 */
@Composable
fun FisheyeLyrics(
    lines: List<LyricsEntry>,
    fontFamily: FontFamily,
    fontSize: Float,
    lineSpacing: Float,
    positionMs: () -> Long,
    isSynced: Boolean,
    autoScrollEnabled: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onSeek: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    var activeIndex by remember(lines) { mutableIntStateOf(-1) }

    LaunchedEffect(lines, isSynced) {
        if (!isSynced) {
            activeIndex = -1
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { }
            val position = positionMs()
            val found = lines.indexOfLast { it.time <= position }
            if (found != activeIndex) activeIndex = found
        }
    }

    LaunchedEffect(activeIndex, autoScrollEnabled) {
        if (autoScrollEnabled && activeIndex >= 0 && activeIndex < lines.size) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    val density = LocalDensity.current
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 120.dp,
            bottom = 120.dp,
            start = 20.dp,
            end = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(lines, key = { index, line -> "$index-${line.time}" }) { index, line ->
            val distance = if (activeIndex < 0) 0 else index - activeIndex
            val absoluteDistance = abs(distance).coerceAtMost(5)
            val targetScale = when (absoluteDistance) {
                0 -> 1.18f
                1 -> 1.02f
                2 -> 0.88f
                3 -> 0.75f
                else -> 0.64f
            }
            val scale by animateFloatAsState(targetScale, tween(360), label = "fisheyeScale")
            val rotation by animateFloatAsState(
                (distance.coerceIn(-4, 4) * 7.5f),
                tween(420),
                label = "fisheyeRotation",
            )
            val alpha by animateFloatAsState(
                when (absoluteDistance) {
                    0 -> 1f
                    1 -> 0.82f
                    2 -> 0.62f
                    3 -> 0.42f
                    else -> 0.25f
                },
                tween(360),
                label = "fisheyeAlpha",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        cameraDistance = 18f * density.density
                        rotationX = rotation
                        scaleX = scale * (1f - absoluteDistance * 0.045f)
                        scaleY = scale
                        translationY = distance * 2.5f * density.density
                        this.alpha = alpha
                    }
                    .clickable { onSeek(line.time.toInt()) }
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    fontFamily = fontFamily,
                    text = line.text,
                    fontSize = (fontSize * scale).sp,
                    fontWeight = if (index == activeIndex) FontWeight.ExtraBold else FontWeight.Bold,
                    lineHeight = (fontSize * lineSpacing).sp,
                    color = if (index == activeIndex) accent else accent.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
