package com.nexamusic.music.ui.component

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexamusic.music.lyrics.LyricsEntry
import com.nexamusic.music.lyrics.WordTimestamp
import com.nexamusic.music.ui.screens.settings.LyricsPosition
import com.nexamusic.music.ui.ios.compat.Text
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private data class FisheyeToken(
    val text: String,
    val startMs: Long?,
    val endMs: Long?,
    val seed: Int,
    val weight: FontWeight,
    val italic: Boolean,
    val scale: Float,
    val hero: Boolean,
    val mutedBox: Boolean,
    val underline: Boolean,
    val icon: FisheyeLyricsConfig.KeywordIcon?,
)

private data class FisheyeLine(
    val entry: LyricsEntry,
    val tokens: List<FisheyeToken>,
    val iconIndex: Int?,
)

private val cjkRegex = Regex("[\\u4E00-\\u9FFF\\u3040-\\u30FF\\uAC00-\\uD7AF]")
private val functionWords = FisheyeLyricsConfig.FunctionWords

/**
 * Word-layer fisheye renderer. LyricsEntry remains the source of truth for timing,
 * singer identity and background-vocal alignment; LazyColumn remains the scroll engine.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    songId: String = "",
    textPosition: LyricsPosition = LyricsPosition.CENTER,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    var activeIndex by remember(lines) { mutableIntStateOf(-1) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val cachedLines = remember(lines, songId) {
        lines.mapIndexed { lineIndex, entry ->
            val sourceWords = entry.words?.takeIf { it.isNotEmpty() }?.map {
                Triple(it.text, (it.startTime * 1000.0).toLong(), (it.endTime * 1000.0).toLong())
            } ?: splitFisheyeWords(entry.text).map { Triple(it, null, null) }
            val heroIndex = sourceWords.indices.maxByOrNull { sourceWords[it].first.length } ?: -1
            var iconIndex: Int? = null
            val tokens = sourceWords.mapIndexed { wordIndex, (raw, start, end) ->
                val seed = FisheyeLyricsConfig.seed(songId, lineIndex, wordIndex)
                val normalized = FisheyeLyricsConfig.normalizedWord(raw)
                val icon = if (iconIndex == null && !isCjk(raw)) FisheyeLyricsConfig.KeywordIcons[normalized] else null
                if (icon != null) iconIndex = wordIndex
                val weight = when (seed % 3) {
                    0 -> FontWeight.Medium
                    1 -> FontWeight.Bold
                    else -> FontWeight.ExtraBold
                }
                val isHero = wordIndex == heroIndex && normalized !in functionWords
                val factor = when {
                    isHero -> FisheyeLyricsConfig.HERO_WORD_SCALE
                    normalized in functionWords -> FisheyeLyricsConfig.FUNCTION_WORD_SCALE
                    else -> FisheyeLyricsConfig.MIN_WORD_SCALE_FACTOR +
                        ((seed ushr 4) % 1000) / 1000f *
                        (FisheyeLyricsConfig.MAX_WORD_SCALE_FACTOR - FisheyeLyricsConfig.MIN_WORD_SCALE_FACTOR)
                }
                FisheyeToken(
                    text = raw,
                    startMs = start,
                    endMs = end,
                    seed = seed,
                    weight = weight,
                    italic = seed % 4 == 0,
                    scale = factor,
                    hero = isHero,
                    mutedBox = seed % 5 == 0,
                    underline = seed % 4 == 1,
                    icon = icon,
                )
            }
            FisheyeLine(entry, tokens, iconIndex)
        }
    }

    LaunchedEffect(lines, isSynced) {
        if (!isSynced) {
            activeIndex = -1
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { }
            val now = positionMs()
            // Binary search avoids scanning long lyric lists every display frame.
            var low = 0
            var high = lines.lastIndex
            var found = -1
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (lines[mid].time <= now) {
                    found = mid
                    low = mid + 1
                } else high = mid - 1
            }
            if (found != activeIndex) activeIndex = found
        }
    }

    LaunchedEffect(activeIndex, autoScrollEnabled, viewportHeight, reduceMotion) {
        if (!autoScrollEnabled || activeIndex < 0 || viewportHeight == 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
        if (visible == null) {
            listState.animateScrollToItem(activeIndex)
        } else {
            val target = (viewportHeight * FisheyeLyricsConfig.VERTICAL_CENTRE).toInt()
            val current = visible.offset + visible.size / 2
            val delta = current - target
            if (abs(delta) > 2) {
                if (reduceMotion) listState.scrollBy(delta.toFloat())
                else listState.animateScrollToItem(activeIndex, scrollOffset = -target + visible.size / 2)
            }
        }
    }

    val lineGap = with(density) { (fontSize * lineSpacing * 0.65f).sp.toDp() }
    val wordGap = with(density) { (fontSize * FisheyeLyricsConfig.WORD_GAP_EM).sp.toDp() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeight = it.height }
            .background(Color.Transparent),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = with(density) { (viewportHeight * FisheyeLyricsConfig.VERTICAL_CENTRE).toDp() },
                bottom = with(density) { (viewportHeight * (1f - FisheyeLyricsConfig.VERTICAL_CENTRE)).toDp() },
                start = 8.dp,
                end = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(lineGap),
        ) {
            itemsIndexed(cachedLines, key = { index, line -> "$songId:$index:${line.entry.time}" }) { index, line ->
                val distance = index - activeIndex
                val active = isSynced && index == activeIndex
                val isAbove = distance < 0
                val alphaTarget = if (!isSynced) 0.7f else {
                    val falloff = (1f - abs(distance) / 7f).coerceIn(0f, 1f)
                    when {
                        active -> 1f
                        isAbove -> 0.20f + 0.35f * falloff
                        else -> 0.12f + 0.58f * falloff
                    }
                }
                val lineAlpha by animateFloatAsState(
                    targetValue = alphaTarget,
                    animationSpec = if (reduceMotion) tween(0) else spring(
                        stiffness = FisheyeLyricsConfig.SPRING_STIFFNESS,
                        dampingRatio = FisheyeLyricsConfig.SPRING_DAMPING_RATIO,
                    ),
                    label = "fisheye_line_alpha",
                )
                val singerAlign = singerLineAlignment(line.entry.agent, line.entry.isBackground, textPosition)
                val singerTextAlign = singerTextAlign(line.entry.agent, line.entry.isBackground, textPosition)
                val lineTime = line.entry.time
                val nextLineTime = lines.getOrNull(index + 1)?.time ?: (lineTime + FisheyeLyricsConfig.DEFAULT_LINE_MS)
                val isInterlude = line.entry.text.isBlank()
                val now = positionMs()
                val lineActive = active && isSynced
                val tokens = line.tokens
                val heroIndex = tokens.indexOfFirst { it.hero }.takeIf { it >= 0 } ?: 0
                val sungIndex = if (tokens.any { it.startMs != null && it.endMs != null }) {
                    tokens.indexOfLast { token -> token.startMs != null && token.endMs != null && now in token.startMs..token.endMs }
                } else if (lineActive) heroIndex else -1

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = lineAlpha }
                        .clickable { onSeek(lineTime.toInt()) }
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                        .clearAndSetSemantics { contentDescription = line.entry.text },
                    horizontalAlignment = singerAlign,
                ) {
                    if (isInterlude) {
                        FisheyeInterlude(
                            scale = lensScale(distance.toFloat(), 0f, viewportHeight.toFloat(), reduceMotion),
                            color = accent,
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = when (singerTextAlign) {
                                TextAlign.Left -> Arrangement.Start
                                TextAlign.Right -> Arrangement.End
                                else -> Arrangement.Center
                            },
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            tokens.forEachIndexed { wordIndex, token ->
                                val wordActive = lineActive && wordIndex == sungIndex
                                val wordSung = lineActive && token.endMs != null && now > token.endMs
                                val progress = if (wordActive && token.startMs != null && token.endMs != null) {
                                    ((now - token.startMs).toFloat() / (token.endMs - token.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                                } else if (wordSung) 1f else 0f
                                val xNorm = if (tokens.size <= 1) 0f else (wordIndex.toFloat() / (tokens.size - 1)) * 2f - 1f
                                val yNorm = (distance / 5f).coerceIn(-1f, 1f)
                                val radius = sqrt(xNorm * xNorm + yNorm * yNorm)
                                val lens = if (reduceMotion) 1f else (1f / (1f + FisheyeLyricsConfig.LENS_K * radius * radius))
                                val vertical = if (reduceMotion) 1f else 1f - FisheyeLyricsConfig.VERTICAL_COMPRESSION * yNorm * yNorm
                                val wordScale = (lens * vertical * token.scale).coerceIn(FisheyeLyricsConfig.MIN_WORD_SCALE, FisheyeLyricsConfig.MAX_WORD_SCALE)
                                val rotation = if (reduceMotion) 0f else -xNorm * yNorm * FisheyeLyricsConfig.MAX_ROTATION_DEGREES
                                val color = when {
                                    wordActive -> FisheyeLyricsConfig.ActiveInk
                                    wordSung -> FisheyeLyricsConfig.Sung
                                    lineActive -> FisheyeLyricsConfig.Upcoming
                                    else -> FisheyeLyricsConfig.Upcoming
                                }
                                val labelFill = when {
                                    wordActive -> FisheyeLyricsConfig.ActiveLabel
                                    wordSung || token.mutedBox -> FisheyeLyricsConfig.MutedLabel
                                    else -> null
                                }
                                val underlineProgress by animateFloatAsState(
                                    targetValue = if (lineActive && token.underline) 1f else if (index < activeIndex && token.underline) 1f else 0f,
                                    animationSpec = if (reduceMotion) tween(0) else tween(FisheyeLyricsConfig.UNDERLINE_DRAW_MS),
                                    label = "fisheye_underline_progress",
                                )
                                val pop by animateFloatAsState(
                                    targetValue = if (wordActive && !reduceMotion) FisheyeLyricsConfig.WORD_POP_SCALE else 1f,
                                    animationSpec = if (reduceMotion) tween(0) else spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = 500f,
                                        visibilityThreshold = 0.001f,
                                    ),
                                    label = "fisheye_word_pop",
                                )
                                Row(
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = wordScale * pop
                                            scaleY = wordScale * pop
                                            rotationZ = rotation
                                            // Keep the active word away from the clipped viewport edges.
                                            if (wordActive) translationX = 0f
                                        }
                                        .then(
                                            if (labelFill != null) Modifier.drawBehind {
                                                val padX = FisheyeLyricsConfig.LABEL_HORIZONTAL_PADDING_DP.dp.toPx()
                                                val padY = FisheyeLyricsConfig.LABEL_VERTICAL_PADDING_DP.dp.toPx()
                                                val left = -padX
                                                val top = -padY
                                                val right = size.width + padX
                                                val bottom = size.height + padY
                                                val path = Path().apply {
                                                    moveTo(left, top)
                                                    lineTo(right - 2.dp.toPx(), top)
                                                    val notch = (token.seed % 3 + 3) * 0.7.dp.toPx()
                                                    lineTo(right - notch, top + size.height * 0.22f)
                                                    lineTo(right, top + size.height * 0.38f)
                                                    lineTo(right - notch * 0.55f, top + size.height * 0.56f)
                                                    lineTo(right, top + size.height * 0.74f)
                                                    lineTo(right - notch, bottom)
                                                    lineTo(left, bottom)
                                                    close()
                                                }
                                                drawPath(path, labelFill)
                                            } else Modifier
                                        )
                                        .padding(horizontal = wordGap / 2, vertical = 2.dp)
                                        .drawWithCache {
                                            val underline = Path().apply {
                                                val y = size.height - 1.dp.toPx()
                                                moveTo(0f, y)
                                                cubicTo(size.width * 0.25f, y - 2.dp.toPx(), size.width * 0.7f, y + 2.dp.toPx(), size.width, y)
                                            }
                                            onDrawWithContent {
                                                drawContent()
                                                if (token.underline && underlineProgress > 0f) {
                                                    drawPath(underline, color.copy(alpha = 0.7f * underlineProgress), style = Stroke(FisheyeLyricsConfig.UNDERLINE_WIDTH_DP.dp.toPx(), cap = StrokeCap.Round))
                                                }
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (token.icon != null) {
                                        FisheyeKeywordIcon(
                                            icon = token.icon.icon,
                                            tint = if (wordActive) FisheyeLyricsConfig.ActiveInk else accent,
                                            modifier = Modifier.size((fontSize * 1.2f).sp.value.dp),
                                            active = wordActive,
                                        )
                                    }
                                    Text(
                                        text = token.text,
                                        fontFamily = fontFamily,
                                        fontSize = (fontSize * token.scale).sp,
                                        fontWeight = token.weight,
                                        fontStyle = if (token.italic) FontStyle.Italic else FontStyle.Normal,
                                        letterSpacing = if (token.weight == FontWeight.Medium) (-0.01f).em else (-0.025f).em,
                                        lineHeight = (fontSize * lineSpacing).sp,
                                        color = color,
                                        maxLines = 2,
                                    )
                                }


                            }
                        }
                    }
                }
            }
        }
    }
}

private fun splitFisheyeWords(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    if (isCjk(text)) return text.filterNot(Char::isWhitespace).chunked(2)
    return Regex("\\S+").findAll(text).map { it.value }.toList()
}

private fun isCjk(text: String): Boolean = cjkRegex.containsMatchIn(text)

private fun lensScale(distance: Float, x: Float, height: Float, reduceMotion: Boolean): Float {
    if (reduceMotion || height <= 0f) return 1f
    val y = (distance / 5f).coerceIn(-1f, 1f)
    val r2 = x * x + y * y
    return (1f / (1f + FisheyeLyricsConfig.LENS_K * r2)).coerceIn(FisheyeLyricsConfig.MIN_WORD_SCALE, 1f)
}

@Composable
private fun FisheyeInterlude(scale: Float, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp).graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(3) {
            Box(Modifier.padding(horizontal = 3.dp).size(5.dp).background(color.copy(alpha = 0.72f), RoundedCornerShape(50)))
        }
    }
}

/** Vector-only keyword glyphs; decorative and excluded from accessibility traversal. */
@Composable
private fun FisheyeKeywordIcon(
    icon: FisheyeLyricsConfig.Icon,
    tint: Color,
    modifier: Modifier = Modifier,
    active: Boolean,
) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.9f,
        animationSpec = tween(FisheyeLyricsConfig.ICON_ENTER_MS),
        label = "fisheye_icon_scale",
    )
    androidx.compose.foundation.Canvas(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clearAndSetSemantics { },
    ) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round)
        val centre = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.5f)
        when (icon) {
            FisheyeLyricsConfig.Icon.HOURGLASS -> {
                val p = Path().apply {
                    moveTo(w*.22f,h*.12f); lineTo(w*.78f,h*.12f); lineTo(w*.7f,h*.38f)
                    lineTo(w*.5f,h*.52f); lineTo(w*.3f,h*.68f); lineTo(w*.22f,h*.88f)
                    lineTo(w*.78f,h*.88f); lineTo(w*.7f,h*.68f); lineTo(w*.5f,h*.52f)
                    lineTo(w*.3f,h*.38f); close()
                }
                drawPath(p,tint,style=stroke)
            }
            FisheyeLyricsConfig.Icon.PERSON -> {
                drawCircle(tint,w*.19f,androidx.compose.ui.geometry.Offset(w*.5f,h*.28f))
                val p=Path().apply { moveTo(w*.16f,h*.9f); cubicTo(w*.18f,h*.56f,w*.82f,h*.56f,w*.84f,h*.9f); close() }
                drawPath(p,tint)
            }
            FisheyeLyricsConfig.Icon.HEART -> {
                val p=Path().apply {
                    moveTo(w*.5f,h*.88f); cubicTo(w*.35f,h*.72f,w*.05f,h*.48f,w*.12f,h*.27f)
                    cubicTo(w*.2f,h*.04f,w*.43f,h*.12f,w*.5f,h*.3f)
                    cubicTo(w*.57f,h*.12f,w*.8f,h*.04f,w*.88f,h*.27f)
                    cubicTo(w*.95f,h*.48f,w*.65f,h*.72f,w*.5f,h*.88f); close()
                }
                drawPath(p,tint)
            }
            FisheyeLyricsConfig.Icon.FLAME -> {
                val p=Path().apply { moveTo(w*.5f,h*.08f); cubicTo(w*.9f,h*.42f,w*.82f,h*.76f,w*.5f,h*.92f); cubicTo(w*.14f,h*.82f,w*.16f,h*.48f,w*.5f,h*.08f); close() }
                drawPath(p,tint)
            }
            FisheyeLyricsConfig.Icon.SUN, FisheyeLyricsConfig.Icon.STAR -> {
                drawCircle(tint,w*.22f,centre)
                repeat(8) { i ->
                    val angle=i*Math.PI/4
                    val x1=w*.5f+kotlin.math.cos(angle).toFloat()*w*.32f
                    val y1=h*.5f+kotlin.math.sin(angle).toFloat()*h*.32f
                    val x2=w*.5f+kotlin.math.cos(angle).toFloat()*w*.46f
                    val y2=h*.5f+kotlin.math.sin(angle).toFloat()*h*.46f
                    drawLine(tint,androidx.compose.ui.geometry.Offset(x1,y1),androidx.compose.ui.geometry.Offset(x2,y2),strokeWidth=stroke.width)
                }
            }
            FisheyeLyricsConfig.Icon.MOON -> drawArc(tint,40f,280f,false,androidx.compose.ui.geometry.Offset(w*.12f,h*.12f),androidx.compose.ui.geometry.Size(w*.76f,h*.76f),style=stroke)
            FisheyeLyricsConfig.Icon.RAIN, FisheyeLyricsConfig.Icon.DROP -> {
                val p=Path().apply { moveTo(w*.5f,h*.08f); cubicTo(w*.9f,h*.52f,w*.82f,h*.88f,w*.5f,h*.9f); cubicTo(w*.18f,h*.88f,w*.1f,h*.52f,w*.5f,h*.08f); close() }
                drawPath(p,tint)
            }
            FisheyeLyricsConfig.Icon.DANCE, FisheyeLyricsConfig.Icon.RUN -> {
                drawCircle(tint,w*.14f,androidx.compose.ui.geometry.Offset(w*.58f,h*.18f))
                drawLine(tint,androidx.compose.ui.geometry.Offset(w*.52f,h*.34f),androidx.compose.ui.geometry.Offset(w*.38f,h*.62f),strokeWidth=stroke.width)
                drawLine(tint,androidx.compose.ui.geometry.Offset(w*.46f,h*.44f),androidx.compose.ui.geometry.Offset(w*.82f,h*.48f),strokeWidth=stroke.width)
                drawLine(tint,androidx.compose.ui.geometry.Offset(w*.38f,h*.62f),androidx.compose.ui.geometry.Offset(w*.16f,h*.9f),strokeWidth=stroke.width)
                drawLine(tint,androidx.compose.ui.geometry.Offset(w*.38f,h*.62f),androidx.compose.ui.geometry.Offset(w*.76f,h*.86f),strokeWidth=stroke.width)
            }
            FisheyeLyricsConfig.Icon.HOME -> {
                val p=Path().apply { moveTo(w*.08f,h*.48f); lineTo(w*.5f,h*.12f); lineTo(w*.92f,h*.48f); lineTo(w*.82f,h*.48f); lineTo(w*.82f,h*.9f); lineTo(w*.18f,h*.9f); lineTo(w*.18f,h*.48f); close() }
                drawPath(p,tint)
            }
            FisheyeLyricsConfig.Icon.EYE -> {
                val p=Path().apply { moveTo(w*.05f,h*.5f); cubicTo(w*.28f,h*.18f,w*.72f,h*.18f,w*.95f,h*.5f); cubicTo(w*.72f,h*.82f,w*.28f,h*.82f,w*.05f,h*.5f); close() }
                drawPath(p,tint,style=stroke); drawCircle(tint,w*.12f,centre)
            }
            FisheyeLyricsConfig.Icon.CLOCK -> {
                drawCircle(tint,w*.38f,centre,style=stroke)
                drawLine(tint,centre,androidx.compose.ui.geometry.Offset(w*.5f,h*.28f),strokeWidth=stroke.width)
                drawLine(tint,centre,androidx.compose.ui.geometry.Offset(w*.68f,h*.58f),strokeWidth=stroke.width)
            }
            FisheyeLyricsConfig.Icon.PHONE -> {
                val p=Path().apply { moveTo(w*.28f,h*.12f); cubicTo(w*.1f,h*.18f,w*.22f,h*.68f,w*.74f,h*.88f); lineTo(w*.9f,h*.68f); lineTo(w*.68f,h*.5f); lineTo(w*.52f,h*.62f); lineTo(w*.38f,h*.4f); lineTo(w*.48f,h*.26f); close() }
                drawPath(p,tint)
            }
            FisheyeLyricsConfig.Icon.MUSIC -> {
                drawLine(tint,androidx.compose.ui.geometry.Offset(w*.62f,h*.12f),androidx.compose.ui.geometry.Offset(w*.62f,h*.72f),strokeWidth=stroke.width)
                drawLine(tint,androidx.compose.ui.geometry.Offset(w*.62f,h*.12f),androidx.compose.ui.geometry.Offset(w*.9f,h*.06f),strokeWidth=stroke.width)
                drawCircle(tint,w*.17f,androidx.compose.ui.geometry.Offset(w*.38f,h*.78f))
                drawCircle(tint,w*.17f,androidx.compose.ui.geometry.Offset(w*.78f,h*.7f))
            }
        }
    }
}
