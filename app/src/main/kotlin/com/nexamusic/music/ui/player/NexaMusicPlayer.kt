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
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.rounded.Favorite
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
import kotlinx.coroutines.CancellationException
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
    val isLiked = currentSong?.song?.takeIf { it.id == metadata?.id }?.liked == true
    val queueWindows by connection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by connection.currentWindowIndex.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val database = LocalDatabase.current.get()
    var lyricsRequestTrackId by remember { mutableStateOf<String?>(null) }

    // Fetch through NexaMusic's existing lyrics provider when the current track
    // has no usable cached lyrics. Retry a cached NOT_FOUND once per track.
    LaunchedEffect(metadata?.id, currentLyrics) {
        val track = metadata ?: return@LaunchedEffect
        val cachedForTrack = currentLyrics?.takeIf { it.id == track.id }
        val needsLookup = cachedForTrack == null ||
            cachedForTrack.lyrics == LyricsEntity.LYRICS_NOT_FOUND
        if (!needsLookup || lyricsRequestTrackId == track.id) return@LaunchedEffect
        lyricsRequestTrackId = track.id
        delay(500)
        withContext(Dispatchers.IO) {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.nexamusic.music.di.LyricsHelperEntryPoint::class.java,
                )
                val fetched = entryPoint.lyricsHelper().getLyrics(track)
                database.withTransaction {
                    val cached = lyrics(track.id).first()
                    if (cached == null || cached.lyrics == LyricsEntity.LYRICS_NOT_FOUND) {
                        upsert(LyricsEntity(track.id, fetched.lyrics, fetched.provider))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A lyrics-provider failure must never interrupt playback or the player UI.
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

    LaunchedEffect(metadata?.id, sheetState.isCollapsed) {
        if (sheetState.isCollapsed) return@LaunchedEffect
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
            delay(if (isPlaying) 120L else 350L)
        }
    }

    val lyricsText = remember(currentLyrics) { currentLyrics?.lyrics?.trim().orEmpty() }
    val isSyncedLyrics = remember(lyricsText) {
        lyricsText.startsWith("[") && lyricsText != LyricsEntity.LYRICS_NOT_FOUND
    }
    val lyricLines = remember(lyricsText) {
        if (lyricsText.isBlank() || lyricsText == LyricsEntity.LYRICS_NOT_FOUND) {
            emptyList()
        } else {
            runCatching { LyricsUtils.parseLyrics(lyricsText) }.getOrDefault(emptyList())
        }
    }
    val lyricsOffset = currentSong?.song?.lyricsOffset?.toLong() ?: 0L
    val lyricIndex = remember(lyricLines, position, lyricsOffset, isSyncedLyrics) {
        if (lyricLines.isEmpty() || !isSyncedLyrics) -1
        else LyricsUtils.findCurrentLineIndex(lyricLines, (position + lyricsOffset).coerceAtLeast(0L))
    }
    val lyricText = when {
        !isSyncedLyrics && lyricLines.isNotEmpty() -> "Open lyrics"
        lyricLines.getOrNull(lyricIndex)?.text?.isNotBlank() == true -> lyricLines[lyricIndex].text
        lyricsText == LyricsEntity.LYRICS_NOT_FOUND -> "Lyrics not available"
        lyricsText.isBlank() -> "Finding synced lyrics…"
        else -> "♪"
    }

    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.965f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "nexamusicArtworkScale",
    )
    val artwork = metadata?.thumbnailUrl

    var volume by remember { mutableFloatStateOf(connection.player.volume.coerceIn(0f, 1f)) }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // BitChord's full-bleed artwork treatment: the cover fills the player,
        // while a separate blurred layer gives the controls readable contrast.
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.16f * artworkScale
                        scaleY = 1.16f * artworkScale
                        alpha = 0.92f
                    }
                    .blur(34.dp),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x44000000),
                    0.36f to Color(0x33000000),
                    0.62f to Color(0xA6000000),
                    1f to Color(0xF5000000),
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
                        Text("Your queue is empty", color = Color.White.copy(alpha = 0.72f))
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
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
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
                                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(9.dp)),
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
                                        color = Color.White.copy(alpha = 0.62f),
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
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.systemBars.asPaddingValues())
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                // Compact grabber and source label, matching BitChord's immersive player header.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { sheetState.collapseSoft() }
                        .padding(top = 8.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .width(42.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.82f)),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "NOW PLAYING",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.weight(1f))

                // Track identity and like action stay anchored above the lyric strip.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Crossfade(
                            targetState = metadata?.id to metadata?.title,
                            animationSpec = tween(260),
                            label = "nexamusicTrackTitle",
                        ) { target ->
                            Text(
                                text = target.second ?: "Unknown title",
                                color = Color.White,
                                fontSize = 23.sp,
                                lineHeight = 28.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(iterations = Int.MAX_VALUE)
                                    .clickable {
                                        metadata?.album?.id?.let { id -> navController.navigate("album/$id") }
                                    },
                            )
                        }
                        Text(
                            text = metadata?.artists?.joinToString(", ") { it.name }.orEmpty(),
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 19.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = Int.MAX_VALUE)
                                .clickable {
                                    metadata?.artists?.firstOrNull()?.id?.let { id -> navController.navigate("artist/$id") }
                                },
                        )
                    }
                    IconButton(onClick = connection::toggleLike) {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (isLiked) "Unlike song" else "Like song",
                            tint = if (isLiked) Color(0xFFFF6B81) else Color.White,
                            modifier = Modifier.size(29.dp),
                        )
                    }
                }

                // One-line synced lyric preview. Track-keyed AnimatedContent prevents
                // stale lines from flashing when the queue advances.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { showLyrics = true }
                        .padding(top = 12.dp, bottom = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    AnimatedContent(
                        targetState = metadata?.id to lyricText,
                        transitionSpec = {
                            (fadeIn(tween(220, delayMillis = 35)) +
                                slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 3 }) togetherWith
                                (fadeOut(tween(180)) +
                                    slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { -it / 3 })
                        },
                        label = "bitChordSyncedLyricStrip",
                    ) { target ->
                        Text(
                            text = target.second,
                            style = MaterialTheme.typography.titleMedium.copy(lineHeight = 23.sp),
                            color = Color.White.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Slider(
                    value = scrubValue.coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                    onValueChange = {
                        dragging = true
                        scrubValue = it
                    },
                    onValueChangeFinished = {
                        val target = scrubValue.roundToLong().coerceAtLeast(0L)
                        connection.seekTo(target)
                        position = target
                        dragging = false
                    },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatNexaPlayerTime(if (dragging) scrubValue.roundToLong() else position),
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 12.sp,
                    )
                    Text(
                        "-${formatNexaPlayerTime((duration - if (dragging) scrubValue.roundToLong() else position).coerceAtLeast(0L))}",
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 12.sp,
                    )
                }

                Spacer(Modifier.height(34.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showLyrics = true }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = "Lyrics", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(25.dp))
                    }
                    IconButton(onClick = connection::seekToPrevious, modifier = Modifier.size(58.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(43.dp))
                    }
                    IconButton(
                        onClick = connection::togglePlayPause,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(43.dp),
                        )
                    }
                    IconButton(onClick = connection::seekToNext, modifier = Modifier.size(58.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(43.dp))
                    }
                    IconButton(onClick = { showQueue = true }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(25.dp))
                    }
                }

                Spacer(Modifier.height(30.dp))
                // Local output volume control. This touches only the existing player
                // volume; it does not alter track gain or the playback pipeline.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("−", color = Color.White.copy(alpha = 0.72f), fontSize = 20.sp)
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            connection.player.volume = it
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text("+", color = Color.White.copy(alpha = 0.72f), fontSize = 20.sp)
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showLyrics = true }) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = "Open lyrics", tint = Color.White.copy(alpha = 0.88f))
                    }
                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = "Open queue", tint = Color.White.copy(alpha = 0.88f))
                    }
                }
            }
        }
    }
}

private fun formatNexaPlayerTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
