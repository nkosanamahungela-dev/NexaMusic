/*
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Player presentation adapted from the visual structure and motion principles
 * of BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Playback, queue, metadata and lyric sources remain NexaMusic-owned.
 */
package com.nexamusic.music.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.room.withTransaction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nexamusic.music.LocalDatabase
import com.nexamusic.music.LocalPlayerConnection
import com.nexamusic.music.ui.component.BottomSheetState
import com.nexamusic.music.ui.component.Lyrics
import com.nexamusic.music.lyrics.LyricsUtils
import com.nexamusic.music.db.entities.LyricsEntity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

@Composable
fun NexaMusicPlayer(
    navController: NavController,
    sheetState: BottomSheetState,
    modifier: Modifier = Modifier,
) {
    val connection = LocalPlayerConnection.current ?: return
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    val currentLyrics by connection.currentLyrics.collectAsState(initial = null)
    val currentSong by connection.currentSong.collectAsState(initial = null)
    val queueWindows by connection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by connection.currentWindowIndex.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val database = LocalDatabase.current.get()

    // Fetch through NexaMusic's existing lyrics provider when the current track
    // has no cached lyrics. The player UI only observes the result; playback is untouched.
    LaunchedEffect(metadata?.id, currentLyrics) {
        val track = metadata ?: return@LaunchedEffect
        if (currentLyrics != null) return@LaunchedEffect
        delay(500)
        withContext(Dispatchers.IO) {
            runCatching {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.nexamusic.music.di.LyricsHelperEntryPoint::class.java,
                )
                val fetched = entryPoint.lyricsHelper().getLyrics(track)
                database.withTransaction {
                    if (lyrics(track.id).first() == null) {
                        upsert(LyricsEntity(track.id, fetched.lyrics, fetched.provider))
                    }
                }
            }
        }
    }

    var position by remember { mutableLongStateOf(connection.player.currentPosition.coerceAtLeast(0L)) }
    var isPlaying by remember { mutableStateOf(connection.player.isPlaying) }
    var duration by remember { mutableLongStateOf(0L) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    BackHandler(enabled = showLyrics || showQueue) {
        showLyrics = false
        showQueue = false
    }
    var dragging by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(metadata?.id) {
        while (true) {
            val playerDuration = connection.player.duration
            duration = if (playerDuration > 0L && playerDuration != androidx.media3.common.C.TIME_UNSET) {
                playerDuration
            } else {
                (metadata?.duration?.coerceAtLeast(0)?.toLong() ?: 0L) * 1000L
            }
            isPlaying = connection.player.isPlaying
            if (!dragging) {
                position = connection.player.currentPosition.coerceAtLeast(0L)
                scrubValue = position.toFloat()
            }
            delay(if (isPlaying) 80L else 350L)
        }
    }

    val lyricsText = remember(currentLyrics) { currentLyrics?.lyrics?.trim().orEmpty() }
    val lyricLines = remember(lyricsText) {
        if (lyricsText.isBlank() || lyricsText == com.nexamusic.music.db.entities.LyricsEntity.LYRICS_NOT_FOUND) {
            emptyList()
        } else {
            runCatching { LyricsUtils.parseLyrics(lyricsText) }.getOrDefault(emptyList())
        }
    }
    val lyricsOffset = currentSong?.song?.lyricsOffset?.toLong() ?: 0L
    val lyricIndex = remember(lyricLines, position, lyricsOffset) {
        if (lyricLines.isEmpty()) -1
        else LyricsUtils.findCurrentLineIndex(lyricLines, (position + lyricsOffset).coerceAtLeast(0L))
    }
    val lyricText = lyricLines.getOrNull(lyricIndex)?.text
        ?.takeIf { it.isNotBlank() }
        ?: if (lyricsText.isBlank()) "Lyrics will appear here" else "♪"

    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.965f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "nexamusicArtworkScale",
    )
    val artwork = metadata?.thumbnailUrl

    Box(modifier.fillMaxSize().background(Color(0xFF101014))) {
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.18f; scaleY = 1.18f; alpha = 0.34f }
                    .blur(64.dp),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x66202028), Color(0xE6101014), Color(0xFF101014)),
                ),
            ),
        )
        if (showLyrics) {
            Box(Modifier.fillMaxSize()) {
                Lyrics(
                    sliderPositionProvider = { position },
                    modifier = Modifier.fillMaxSize(),
                    showLyrics = true,
                )
                IconButton(
                    onClick = { showLyrics = false },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 28.dp, start = 18.dp),
                ) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Back to player", tint = Color.White)
                }
            }
        } else if (showQueue) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.systemBars.asPaddingValues())
                    .padding(horizontal = 22.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showQueue = false }) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Back to player", tint = Color.White)
                    }
                    Text(
                        "UP NEXT",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.6.sp,
                    )
                }
                if (queueWindows.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Your queue is empty", color = Color.White.copy(alpha = 0.62f))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = queueWindows,
                            key = { index, window -> "$index-${window.mediaItem.mediaId}" },
                        ) { index, window ->
                            val itemMetadata = window.mediaItem.mediaMetadata
                            val selected = index == currentWindowIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable {
                                        connection.player.seekTo(index, 0L)
                                        connection.play()
                                        showQueue = false
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = itemMetadata.artworkUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        itemMetadata.title?.toString().orEmpty().ifBlank { "Unknown title" },
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        itemMetadata.artist?.toString().orEmpty(),
                                        color = Color.White.copy(alpha = 0.58f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { sheetState.collapseSoft() }) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Collapse player", tint = Color.White)
                }
                Text(
                    "NOW PLAYING",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = Color.White)
                }
            }

            Spacer(Modifier.weight(0.55f))
            AsyncImage(
                model = artwork,
                contentDescription = metadata?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(4.7f, fill = false)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = artworkScale
                        scaleY = artworkScale
                    }
                    .shadow(28.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp)),
            )
            Spacer(Modifier.height(30.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Crossfade(
                        targetState = metadata?.id to metadata?.title,
                        animationSpec = tween(300),
                        label = "nexamusicTrackTitle",
                    ) {
                        Text(
                            text = it.second ?: "Unknown title",
                            color = Color.White,
                            fontSize = 20.sp,
                            lineHeight = 25.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    metadata?.album?.id?.let { id -> navController.navigate("album/$id") }
                                },
                        )
                    }
                    Text(
                        text = metadata?.artists?.joinToString { it.name }.orEmpty(),
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 20.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                metadata?.artists?.firstOrNull()?.id?.let { id -> navController.navigate("artist/$id") }
                            },
                    )
                }
                IconButton(onClick = connection::toggleLike) {
                    Icon(
                        Icons.Rounded.FavoriteBorder,
                        contentDescription = "Like song",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            // BitChord-style single-line synced lyric slot directly under the artist.
            // Fixed height prevents the artwork and transport from jumping between tracks.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clickable { showLyrics = true }
                    .padding(vertical = 4.dp)
                    .graphicsLayer { translationY = 6.dp.toPx() },
                contentAlignment = Alignment.CenterStart,
            ) {
                AnimatedContent(
                    targetState = lyricText,
                    transitionSpec = {
                        slideInVertically(
                            animationSpec = tween(340, easing = FastOutSlowInEasing),
                        ) { height -> (height * 0.35f).toInt() } togetherWith
                            (fadeOut(animationSpec = tween(340, easing = FastOutSlowInEasing)) +
                                slideOutVertically(
                                    animationSpec = tween(340, easing = FastOutSlowInEasing),
                                ) { height -> -(height * 0.35f).toInt() })
                    },
                    label = "nexamusicCurrentLyric",
                ) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp),
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Slider(
                value = scrubValue.coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                onValueChange = {
                    dragging = true
                    scrubValue = it
                },
                onValueChangeFinished = {
                    connection.seekTo(scrubValue.roundToLong().coerceAtLeast(0L))
                    position = scrubValue.roundToLong().coerceAtLeast(0L)
                    dragging = false
                },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatNexaPlayerTime(if (dragging) scrubValue.roundToLong() else position), color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
                Text(formatNexaPlayerTime((duration - if (dragging) scrubValue.roundToLong() else position).coerceAtLeast(0L)), color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showLyrics = true }) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = "Lyrics", tint = Color.White.copy(alpha = 0.82f))
                }
                IconButton(onClick = connection::seekToPrevious, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                IconButton(
                    onClick = connection::togglePlayPause,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = connection::seekToNext, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = Color.White.copy(alpha = 0.82f))
                }
            }
            Spacer(Modifier.weight(0.7f))
        }
    }
}

private fun formatNexaPlayerTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
