/*
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * The compact Now Playing lyric strip. It reads the same LyricsEntity and parsed
 * LyricsEntry/WordTimestamp data as the full lyrics view; it does not own a source
 * lookup or a second synchronization engine.
 */
package com.nexamusic.music.ui.player

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberTextMeasurer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nexamusic.music.LocalPlayerConnection
import com.nexamusic.music.constants.LyricsTextPositionKey
import com.nexamusic.music.constants.PlayerLyricLineEnabledKey
import com.nexamusic.music.constants.ShowLyricsKey
import com.nexamusic.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.nexamusic.music.lyrics.LyricsEntry
import com.nexamusic.music.lyrics.LyricsUtils.parseLyrics
import com.nexamusic.music.ui.screens.settings.LyricsPosition
import com.nexamusic.music.ui.ios.compat.AppTheme
import com.nexamusic.music.ui.ios.compat.Text
import com.nexamusic.music.ui.ios.foundation.IosTheme
import com.nexamusic.music.utils.rememberEnumPreference
import com.nexamusic.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min

private val PlayerLyricLineHeight = 64.dp
private const val PlayerLyricLineCollapseMs = 250
private const val PlayerLyricLineExitMs = 150
private const val PlayerLyricLineEnterMs = 220
private const val PlayerLyricLineUnsungAlpha = 0.55f
private const val PlayerLyricLineMinFontSp = 18f
private const val PlayerLyricLineMaxFontSp = 28f
private const val PlayerLyricLinePositionPollMs = 80L

/**
 * Shared line for the default and Apple-style player layouts.
 *
 * The playback ticker is local to this component. Line-timed lyrics update at a
 * low rate; word-timed lyrics use the frame clock, so the rest of the player is
 * not recomposed by the lyric fill.
 */
@Composable
fun PlayerLyricLine(
    modifier: Modifier = Modifier,
    positionProvider: (() -> Long?)? = null,
    onOpenLyrics: () -> Unit,
    textStyle: TextStyle,
    textColor: Color,
    isVisible: Boolean = true,
    enabledOverride: Boolean? = null,
    isPreview: Boolean = false,
    previewSampleText: String? = null,
) {
    val connection = LocalPlayerConnection.current
    if (connection == null && !isPreview) return
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val (savedEnabled, setSavedEnabled) = rememberPreference(PlayerLyricLineEnabledKey, true)
    val (globalLyricsEnabled) = rememberPreference(ShowLyricsKey, true)
    val enabled = (enabledOverride ?: savedEnabled) && globalLyricsEnabled && isVisible

    val lyricsEntity by connection?.currentLyrics?.collectAsStateWithLifecycle(initialValue = null)
        ?: remember { mutableStateOf(null) }
    val currentSong by connection?.currentSong?.collectAsStateWithLifecycle(initialValue = null)
        ?: remember { mutableStateOf(null) }
    val mediaMetadata by connection?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }
    val isPlaying by connection?.isPlaying?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val playbackState by connection?.playbackState?.collectAsStateWithLifecycle()
        ?: remember { mutableIntStateOf(androidx.media3.common.Player.STATE_IDLE) }
    val (lyricsTextPosition) = rememberEnumPreference(LyricsTextPositionKey, LyricsPosition.LEFT)
    val lyricsTextAlign = when (lyricsTextPosition) {
        LyricsPosition.LEFT -> TextAlign.Start
        LyricsPosition.CENTER -> TextAlign.Center
        LyricsPosition.RIGHT -> TextAlign.End
    }

    val lyrics = remember(lyricsEntity) { lyricsEntity?.lyrics?.trim() }
    val timedLyrics = remember(lyrics, mediaMetadata?.id) {
        if (lyrics.isNullOrBlank() || lyrics == LYRICS_NOT_FOUND || !lyrics.startsWith("[")) {
            emptyList()
        } else {
            parseLyrics(lyrics)
        }
    }
    val hasTimedLyrics = timedLyrics.isNotEmpty()
    val lyricsOffset = currentSong?.song?.lyricsOffset ?: 0
    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }

    var playbackPosition by remember(mediaMetadata?.id) {
        mutableLongStateOf((connection?.player?.currentPosition ?: 0L).coerceAtLeast(0L))
    }
    var currentLineIndex by remember(mediaMetadata?.id) { mutableIntStateOf(-1) }
    val currentLine = timedLyrics.getOrNull(currentLineIndex)

    // Keep the row mounted during the normal lyrics-fetch handoff, then collapse
    // when the resolved lyrics are absent or untimed.
    var resolvedNoTimedLyrics by remember(mediaMetadata?.id) { mutableStateOf(false) }
    LaunchedEffect(mediaMetadata?.id, lyrics) {
        resolvedNoTimedLyrics = false
        if (lyrics.isNullOrBlank()) {
            delay(550L)
            if (lyrics.isNullOrBlank()) resolvedNoTimedLyrics = true
        } else {
            resolvedNoTimedLyrics = !hasTimedLyrics
        }
    }

    LaunchedEffect(
        timedLyrics,
        lyricsOffset,
        isPlaying,
        playbackState,
        mediaMetadata?.id,
        isVisible,
        reduceMotion,
    ) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                if (!isVisible || playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
                    delay(100L)
                    continue
                }

                val position = (positionProvider?.invoke() ?: connection?.player?.currentPosition ?: 0L)
                    .coerceAtLeast(0L)
                playbackPosition = position
                val nextIndex = timedLyrics.indexOfLast { it.time <= position + lyricsOffset }
                if (nextIndex != currentLineIndex) currentLineIndex = nextIndex

                val hasWordTiming = timedLyrics.getOrNull(currentLineIndex)?.words?.isNotEmpty() == true
                if (isPlaying && hasWordTiming && !reduceMotion) {
                    // One frame-clock update, scoped to this small composable only.
                    withFrameNanos { }
                } else {
                    delay(PlayerLyricLinePositionPollMs)
                }
            }
        }
    }

    val sampleFallback = isPreview && mediaMetadata == null && !previewSampleText.isNullOrBlank()
    val rowVisible = enabled && (hasTimedLyrics || sampleFallback || !resolvedNoTimedLyrics)
    val displayedLine = currentLine?.takeIf { it.text.isNotBlank() }
    val displayText = displayedLine?.text ?: previewSampleText.orEmpty()
    val lineKey = when {
        displayedLine != null -> "${displayedLine.time}:${displayedLine.text}"
        hasTimedLyrics -> "instrumental-gap"
        sampleFallback -> "preview:$displayText"
        else -> "empty"
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var showUndo by remember { mutableStateOf(false) }
    LaunchedEffect(showUndo) {
        if (showUndo) {
            delay(4_000L)
            showUndo = false
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = rowVisible,
            enter = expandVertically(animationSpec = tween(PlayerLyricLineCollapseMs)) +
                fadeIn(tween(PlayerLyricLineCollapseMs)),
            exit = shrinkVertically(animationSpec = tween(PlayerLyricLineCollapseMs)) +
                fadeOut(tween(PlayerLyricLineCollapseMs)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PlayerLyricLineHeight)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent),
                        ),
                    )
                    .semantics {
                        contentDescription = if (displayText.isNotBlank()) {
                            "Current lyric: $displayText"
                        } else {
                            "Current lyric: instrumental"
                        }
                        onClick(label = "Open lyrics") {
                            onOpenLyrics()
                            true
                        }
                        customActions = listOf(
                            CustomAccessibilityAction("Hide lyric line") {
                                setSavedEnabled(false)
                                showUndo = true
                                true
                            },
                            CustomAccessibilityAction("Open lyrics") {
                                onOpenLyrics()
                                true
                            },
                        )
                    }
                    .combinedClickable(
                        onClick = onOpenLyrics,
                        onLongClick = { menuExpanded = true },
                    )
                    .padding(horizontal = 1.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                AnimatedContent(
                    targetState = lineKey,
                    transitionSpec = {
                        if (reduceMotion) {
                            fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                        } else {
                        val enter = fadeIn(tween(PlayerLyricLineEnterMs)) +
                            slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = 500f,
                                ),
                            ) { with(density) { 10.dp.roundToPx() } }
                        val exit = fadeOut(tween(PlayerLyricLineExitMs)) +
                            slideOutVertically(tween(PlayerLyricLineExitMs)) {
                                -with(density) { 10.dp.roundToPx() }
                            }
                        enter togetherWith exit
                        }
                    },
                    label = "player-current-lyric",
                ) { target ->
                    if (target == "instrumental-gap") {
                        PlayerLyricLineDots()
                    } else {
                        val targetEntry = remember(target, timedLyrics) {
                            timedLyrics.firstOrNull { "${it.time}:${it.text}" == target }
                        }
                        val targetText = if (target.startsWith("preview:")) {
                            target.removePrefix("preview:")
                        } else {
                            targetEntry?.text.orEmpty()
                        }
                        if (targetText.isNotBlank()) {
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val baseFont = if (textStyle.fontSize == TextUnit.Unspecified) 24f else textStyle.fontSize.value
                                val scaledBase = min(baseFont * density.fontScale, PlayerLyricLineMaxFontSp) / density.fontScale
                                val measurer = rememberTextMeasurer()
                                val styleForMeasure = textStyle.copy(
                                    fontSize = scaledBase.sp,
                                    lineHeight = (scaledBase * 1.12f).sp,
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                )
                                val fittedFont = remember(targetText, maxWidth, scaledBase, textStyle, measurer) {
                                    val maxWidthPx = with(density) { maxWidth.roundToPx() }
                                    val maxHeightPx = with(density) { PlayerLyricLineHeight.roundToPx() }
                                    var chosen = max(PlayerLyricLineMinFontSp, scaledBase)
                                    var candidate = scaledBase
                                    while (candidate >= PlayerLyricLineMinFontSp) {
                                        val result = measurer.measure(
                                            text = AnnotatedString(targetText),
                                            style = styleForMeasure.copy(
                                                fontSize = candidate.sp,
                                                lineHeight = (candidate * 1.12f).sp,
                                            ),
                                            constraints = Constraints(
                                                maxWidth = maxWidthPx,
                                                maxHeight = maxHeightPx,
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (!result.didOverflowHeight && !result.didOverflowWidth) {
                                            chosen = candidate
                                            break
                                        }
                                        candidate -= 1f
                                    }
                                    chosen
                                }
                                val finalStyle = textStyle.copy(
                                    fontSize = fittedFont.sp,
                                    lineHeight = (fittedFont * 1.12f).sp,
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (targetEntry?.words?.isNotEmpty() == true && !reduceMotion && !target.startsWith("preview:")) {
                                    PlayerLyricLineWordFill(
                                        line = targetEntry,
                                        positionMs = playbackPosition + lyricsOffset,
                                        style = finalStyle,
                                        textAlign = lyricsTextAlign,
                                        layoutDirection = layoutDirection,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    Text(
                                        text = targetText,
                                        style = finalStyle,
                                        color = textColor,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = lyricsTextAlign,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        } else {
                            PlayerLyricLineDots()
                        }
                    }
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Hide lyric line") },
                onClick = {
                    menuExpanded = false
                    setSavedEnabled(false)
                    showUndo = true
                },
            )
            DropdownMenuItem(
                text = { Text("Open lyrics") },
                onClick = {
                    menuExpanded = false
                    onOpenLyrics()
                },
            )
        }

        if (showUndo) {
            Popup(
                alignment = Alignment.BottomCenter,
                offset = IntOffset(0, with(density) { (-20).dp.roundToPx() }),
                onDismissRequest = { showUndo = false },
            ) {
                Row(
                    modifier = Modifier
                        .background(AppTheme.colorScheme.inverseSurface, RoundedCornerShape(16.dp))
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Lyric line hidden. Turn it back on in Settings > Player & Lyrics > Lyrics style",
                        color = AppTheme.colorScheme.inverseOnSurface,
                        maxLines = 2,
                    )
                    TextButton(onClick = {
                        setSavedEnabled(true)
                        showUndo = false
                    }) {
                        Text("Undo", color = AppTheme.colorScheme.inversePrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerLyricLineDots() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "player-lyric-dots")
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = tween(520, delayMillis = index * 160),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "player-lyric-dot-$index",
            )
            Box(
                Modifier
                    .size(5.dp)
                    .background(Color.White.copy(alpha = alpha), RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
private fun PlayerLyricLineWordFill(
    line: LyricsEntry,
    positionMs: Long,
    style: TextStyle,
    textAlign: TextAlign,
    layoutDirection: LayoutDirection,
    modifier: Modifier = Modifier,
) {
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }
    val words = line.words.orEmpty()
    val wordRanges = remember(line.text, words) {
        var cursor = 0
        words.map { word ->
            val found = line.text.indexOf(word.text, cursor).takeIf { it >= 0 } ?: cursor
            val end = (found + word.text.length).coerceAtMost(line.text.length)
            cursor = end
            found until end
        }
    }
    Text(
        text = line.text,
        style = style.copy(color = Color.White.copy(alpha = PlayerLyricLineUnsungAlpha)),
        textAlign = textAlign,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
    Text(
        text = line.text,
        style = style.copy(color = Color.White),
        textAlign = textAlign,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            val measured = layout
            if (measured == null || words.isEmpty()) return@drawWithContent
            val seconds = positionMs / 1000.0
            val activeIndex = words.indexOfLast { it.startTime <= seconds }
            val edge = when {
                activeIndex < 0 -> if (layoutDirection == LayoutDirection.Rtl) size.width else 0f
                activeIndex >= words.lastIndex && seconds >= words.last().endTime ->
                    if (layoutDirection == LayoutDirection.Rtl) 0f else size.width
                else -> {
                    val range = wordRanges.getOrNull(activeIndex)
                    if (range == null || range.isEmpty()) 0f
                    else {
                        val word = words[activeIndex]
                        val duration = (word.endTime - word.startTime).coerceAtLeast(0.02)
                        val progress = ((seconds - word.startTime) / duration).toFloat().coerceIn(0f, 1f)
                        val start = measured.getHorizontalPosition(range.first.coerceIn(0, line.text.length), true)
                        val end = measured.getHorizontalPosition((range.last + 1).coerceIn(0, line.text.length), true)
                        start + (end - start) * progress
                    }
                }
            }
            if (layoutDirection == LayoutDirection.Rtl) {
                clipRect(left = edge.coerceIn(0f, size.width)) { this@drawWithContent.drawContent() }
            } else {
                clipRect(right = edge.coerceIn(0f, size.width)) { this@drawWithContent.drawContent() }
            }
        },
    )
}

/** Live Settings preview: real metadata and timed lyrics when a track is playing,
 * otherwise a quiet, no-audio sample that advances every 2.8 seconds. */
@Composable
fun PlayerLyricLinePreviewCard(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val connection = LocalPlayerConnection.current
    val metadata by connection?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }
    val samples = remember {
        listOf(
            "A quiet light is finding its way home",
            "We keep the morning in our hands",
            "Every small moment starts again",
        )
    }
    var sampleIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(metadata?.id) {
        if (metadata == null) {
            while (isActive) {
                delay(2_800L)
                sampleIndex = (sampleIndex + 1) % samples.size
            }
        } else {
            sampleIndex = 0
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(IosTheme.colors.secondarySystemGroupedBackground, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            metadata?.title ?: "Sample song",
            style = IosTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = IosTheme.colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            metadata?.artists?.joinToString { it.name } ?: "Sample artist",
            style = IosTheme.typography.footnote,
            color = IosTheme.colors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PlayerLyricLine(
            modifier = Modifier.padding(top = 4.dp),
            onOpenLyrics = {},
            textStyle = AppTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 21.sp),
            textColor = IosTheme.colors.label,
            enabledOverride = enabled,
            isPreview = true,
            previewSampleText = samples[sampleIndex],
        )
    }
}
