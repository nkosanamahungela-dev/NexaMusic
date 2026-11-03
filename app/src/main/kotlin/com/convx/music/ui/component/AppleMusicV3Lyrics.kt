/**
 * Native Apple Music-inspired lyrics renderer for NexaMusic.
 *
 * This renderer is deliberately isolated behind LyricsAnimationStyle.APPLE_V3.
 * It uses Compose only; no WebView or browser runtime.
 */
package com.convx.music.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.withFrameNanos
import com.convx.music.lyrics.LyricsEntry
import kotlinx.coroutines.isActive

/**
 * Apple Music V3's native presentation. Word timestamps are absolute milliseconds
 * after conversion from the LyricsEntry model's seconds-based timestamps.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AppleMusicV3Lyrics(
    lines: List<LyricsEntry>,
    positionMs: () -> Long,
    fontFamily: FontFamily?,
    fontSize: Float,
    lineSpacing: Float,
    showTranslation: Boolean,
    textColor: Color,
    accentColor: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var framePosition by remember { mutableLongStateOf(positionMs()) }

    // Read the player clock at frame cadence so word highlighting does not inherit
    // the slower line-index polling interval used by the standard lyrics screen.
    LaunchedEffect(lines, positionMs) {
        while (isActive) {
            withFrameNanos {
                framePosition = positionMs()
            }
        }
    }

    val activeIndex = remember(lines, framePosition) {
        lines.indexOfLast { it.time <= framePosition }.coerceAtLeast(0)
    }

    LaunchedEffect(activeIndex, lines) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    val agents = remember(lines) { lines.mapNotNull { it.agent }.distinct() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val verticalInset = maxHeight * 0.40f
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = verticalInset, horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(
                items = lines,
                key = { index, entry -> "${entry.time}-$index" },
            ) { index, entry ->
                val distance = kotlin.math.abs(index - activeIndex)
                val isActive = index == activeIndex
                val isBackground = entry.isBackground
                val targetAlpha = when {
                    isActive -> 1f
                    distance == 1 -> 0.58f
                    distance == 2 -> 0.30f
                    else -> 0.16f
                } * if (isBackground) 0.72f else 1f
                val targetScale = when (distance) {
                    0 -> 1f
                    1 -> 0.91f
                    2 -> 0.84f
                    else -> 0.80f
                } * if (isBackground) 0.88f else 1f
                val alpha by animateFloatAsState(targetAlpha, spring(dampingRatio = 0.82f, stiffness = 260f), label = "appleV3Alpha")
                val scale by animateFloatAsState(targetScale, spring(dampingRatio = 0.78f, stiffness = 230f), label = "appleV3Scale")
                val blurRadius = if (distance >= 2) (distance - 1) * 1.1f else 0f
                val translation by entry.translatedTextFlow.collectAsStateWithLifecycle()
                val romanized by entry.romanizedTextFlow.collectAsStateWithLifecycle()

                val agentIndex = entry.agent?.let { agents.indexOf(it) } ?: -1
                val isDuetSide = agentIndex >= 0 && agents.size > 1
                val lineAlignment = when {
                    isDuetSide && agentIndex % 2 == 1 -> Alignment.End
                    else -> Alignment.Start
                }
                val lineTextAlign = when {
                    isDuetSide && agentIndex % 2 == 1 -> TextAlign.End
                    else -> TextAlign.Start
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                        }
                        .blur(blurRadius.dp)
                        .clickable { onSeek(entry.time) }
                        .padding(
                            horizontal = if (isBackground) 28.dp else 0.dp,
                            vertical = if (isBackground) 2.dp else 0.dp,
                        ),
                    horizontalAlignment = lineAlignment,
                ) {
                    val words = remember(entry) {
                        val timed = entry.words.orEmpty()
                        if (timed.isNotEmpty()) {
                            timed.map { word ->
                                Triple(
                                    word.text,
                                    (word.startTime * 1000.0).toLong(),
                                    (word.endTime * 1000.0).toLong().coerceAtLeast((word.startTime * 1000.0).toLong() + 1L),
                                )
                            }
                        } else {
                            val tokens = entry.text.split(Regex("(?<=\\s)|(?=\\s)")).filter { it.isNotEmpty() }
                            val nextTime = lines.getOrNull(index + 1)?.time ?: (entry.time + 4000L)
                            val duration = (nextTime - entry.time).coerceAtLeast(800L)
                            val totalWeight = tokens.sumOf { it.length.coerceAtLeast(1) }.coerceAtLeast(1)
                            var cursor = entry.time
                            tokens.map { token ->
                                val end = cursor + (duration * token.length.coerceAtLeast(1) / totalWeight).coerceAtLeast(1)
                                Triple(token, cursor, end).also { cursor = end }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = when (lineTextAlign) {
                            TextAlign.End -> Arrangement.End
                            TextAlign.Center -> Arrangement.Center
                            else -> Arrangement.Start
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            words.forEach { (word, start, end) ->
                                val progress = when {
                                    framePosition <= start -> 0f
                                    framePosition >= end -> 1f
                                    else -> ((framePosition - start).toFloat() / (end - start).coerceAtLeast(1L)).coerceIn(0f, 1f)
                                }
                                val wordAlpha = if (isActive) 0.40f + progress * 0.60f else 0.86f
                                val wordColor = if (isActive && progress > 0.02f) {
                                    Color(
                                        red = textColor.red + (accentColor.red - textColor.red) * progress * 0.12f,
                                        green = textColor.green + (accentColor.green - textColor.green) * progress * 0.12f,
                                        blue = textColor.blue + (accentColor.blue - textColor.blue) * progress * 0.12f,
                                        alpha = wordAlpha,
                                    )
                                } else textColor.copy(alpha = wordAlpha)
                                Text(
                                    text = word,
                                    color = wordColor,
                                    fontFamily = fontFamily,
                                    fontSize = (fontSize * if (isBackground) 0.88f else 1.32f).sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp,
                                    lineHeight = ((fontSize * if (isBackground) 0.88f else 1.32f) * lineSpacing.coerceIn(1.12f, 1.45f)).sp,
                                    style = TextStyle(
                                        shadow = if (isActive && progress > 0f) {
                                            Shadow(color = accentColor.copy(alpha = progress * 0.24f), blurRadius = 16f)
                                        } else Shadow(Color.Transparent, blurRadius = 0f)
                                    ),
                                )
                            }
                        }
                    }

                    if (showTranslation && !translation.isNullOrBlank()) {
                        Text(
                            text = translation!!,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            color = textColor.copy(alpha = if (isActive) 0.72f else 0.44f),
                            fontFamily = fontFamily,
                            fontSize = (fontSize * 0.78f).sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = lineTextAlign,
                            lineHeight = (fontSize * 0.78f).sp,
                        )
                    }
                    if (!romanized.isNullOrBlank()) {
                        Text(
                            text = romanized!!,
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                            color = textColor.copy(alpha = if (isActive) 0.62f else 0.42f),
                            fontFamily = fontFamily,
                            fontSize = (fontSize * 0.68f).sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = lineTextAlign,
                            lineHeight = (fontSize * 0.72f).sp,
                        )
                    }
                }
            }
        }
    }
}
