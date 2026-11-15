/**
 * NexaMusic Project (C) 2026
 * Podium-style player design adapted for NexaMusic playback.
 * Upstream reference: https://github.com/GHOSTxASR/podium-music-player
 * Preserve GPL-3.0 notices for reused upstream material.
 */
package com.nexamusic.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nexamusic.music.LocalPlayerConnection
import com.nexamusic.music.ui.component.BottomSheetState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private val PodInk = Color(0xFF0B0E13)
private val PodGlass = Color(0xFF202832)
private val PodText = Color(0xFFF3F5F8)
private val PodMuted = Color(0xFFAEB7C3)
private val PodLine = Color(0xFF75808E)

@Composable
fun PodPlayer(
    sheetState: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val connection = LocalPlayerConnection.current ?: return
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    val playing by connection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    var position by remember(metadata?.id) { mutableLongStateOf(connection.player.currentPosition.coerceAtLeast(0L)) }
    var duration by remember(metadata?.id) { mutableLongStateOf(connection.player.duration.coerceAtLeast(0L)) }
    var volume by remember { mutableFloatStateOf(connection.player.volume.coerceIn(0f, 1f)) }
    var scrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(connection, metadata?.id) {
        while (true) {
            if (!scrubbing) position = connection.player.currentPosition.coerceAtLeast(0L)
            duration = connection.player.duration.coerceAtLeast(0L)
            volume = connection.player.volume.coerceIn(0f, 1f)
            delay(200L)
        }
    }

    PodPlayerDesign(
        title = metadata?.title?.toString() ?: "Nothing playing",
        artist = metadata?.artists?.joinToString(", ") { it.name }.orEmpty(),
        artwork = metadata?.thumbnailUrl,
        positionMs = position,
        durationMs = duration,
        isPlaying = playing,
        volume = volume,
        onSeek = { target -> scrubbing = false; connection.seekTo(target) },
        onScrub = { target -> scrubbing = true; position = target },
        onPrevious = { connection.player.seekToPreviousMediaItem() },
        onNext = { connection.player.seekToNextMediaItem() },
        onPlayPause = { connection.togglePlayPause() },
        onVolume = { value -> volume = value; connection.player.volume = value },
        onMenu = { sheetState.collapseSoft() },
        modifier = modifier,
    )
}

/** The exact same Podium-inspired composition is used by the live player and picker preview. */
@Composable
fun PodPlayerPreview(
    track: PreviewTrack?,
    positionMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    PodPlayerDesign(
        title = track?.title ?: "Maye Maye (feat. Azana & Stixx)",
        artist = track?.artist ?: "Kelvin Momo & Babalwa M",
        artwork = track?.artworkUrl,
        positionMs = if (track == null) 4_000L else positionMs,
        durationMs = track?.durationMs ?: 450_000L,
        isPlaying = isPlaying,
        volume = 0.74f,
        onSeek = {},
        onScrub = {},
        onPrevious = {},
        onNext = {},
        onPlayPause = {},
        onVolume = {},
        onMenu = {},
        modifier = modifier,
        preview = true,
    )
}

@Composable
private fun PodPlayerDesign(
    title: String,
    artist: String,
    artwork: String?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    volume: Float,
    onSeek: (Long) -> Unit,
    onScrub: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onVolume: (Float) -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
) {
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val animatedVolume by animateFloatAsState(volume.coerceIn(0f, 1f), spring(dampingRatio = 0.78f, stiffness = 360f), label = "podVolume")
    var wheelDrag by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF11151B), Color(0xFF090B0F), Color(0xFF050609)))
        ),
    ) {
        val compact = maxHeight < 760.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (preview) {
                Row(
                    Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("9:41", color = PodText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("▮▮▮  Wi-Fi  74%", color = PodText, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(5.dp))
                        Box(Modifier.width(18.dp).height(9.dp).clip(RoundedCornerShape(2.dp)).border(1.dp, PodText, RoundedCornerShape(2.dp)))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowBack, "Close player", tint = PodText)
                }
                Text("NOW PLAYING", color = PodMuted, fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Medium)
                IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Menu, "Menu", tint = PodText, modifier = Modifier.size(28.dp))
                }
            }

            // Main floating glass deck: artwork and source metadata sit side by side as in Podium.
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(22.dp, RoundedCornerShape(30.dp))
                    .clip(RoundedCornerShape(30.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xB52B343D), Color(0xA51B2932), Color(0xB514171D)),
                            start = Offset.Zero,
                            end = Offset(900f, 1200f),
                        )
                    )
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(.28f), Color.White.copy(.04f))), RoundedCornerShape(30.dp))
                    .padding(14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(42.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFF56636B), Color(0xFF10161C)))).border(1.dp, Color.White.copy(.22f), CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text("Now Playing", color = PodText, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        Modifier.clip(CircleShape).background(Color.White.copy(.09f)).border(1.dp, Color.White.copy(.22f), CircleShape).padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Cast, null, tint = PodText, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(14.dp))
                        Box(Modifier.width(1.dp).height(18.dp).background(Color.White.copy(.25f)))
                        Spacer(Modifier.width(10.dp))
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = PodText, modifier = Modifier.size(18.dp))
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = artwork,
                        contentDescription = "Album artwork",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.weight(1.45f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(PodGlass),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(.8f), verticalArrangement = Arrangement.Center) {
                        Text("1 of 11", color = PodMuted, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Opus", color = PodText, fontSize = 13.sp,
                            modifier = Modifier.clip(CircleShape).background(Color.White.copy(.12f)).border(1.dp, Color.White.copy(.24f), CircleShape).padding(horizontal = 12.dp, vertical = 5.dp))
                        Spacer(Modifier.height(7.dp))
                        Text("Online", color = PodMuted, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(title, color = PodText, fontSize = 21.sp, lineHeight = 25.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(artist, color = PodMuted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

                Spacer(Modifier.height(12.dp))
                Slider(
                    value = progress,
                    onValueChange = { onScrub((it * durationMs).toLong()) },
                    onValueChangeFinished = { onSeek(positionMs) },
                    enabled = !preview,
                    colors = SliderDefaults.colors(
                        thumbColor = PodText,
                        activeTrackColor = Color(0xFFB7C1CB),
                        inactiveTrackColor = Color.White.copy(.22f),
                    ),
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(podTime(positionMs), color = PodMuted, fontSize = 12.sp)
                    Text("-${podTime((durationMs - positionMs).coerceAtLeast(0L))}", color = PodMuted, fontSize = 12.sp)
                }

                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    PodGlassButton(onPrevious, Icons.Default.SkipPrevious, "Previous")
                    PodGlassButton(onPlayPause, if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", primary = true)
                    PodGlassButton(onNext, Icons.Default.SkipNext, "Next")
                }

                // Podium's lower segmented control tray.
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.verticalGradient(listOf(Color.White.copy(.12f), Color.White.copy(.035f))))
                        .border(1.dp, Color.White.copy(.24f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(6) { index ->
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (index > 0) Box(Modifier.align(Alignment.CenterStart).width(1.dp).height(20.dp).background(Color.White.copy(.18f)))
                            if (index == 2) Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(.55f)))
                        }
                    }
                }
            }

            // The physical-wheel-inspired transport controller beneath the glass deck.
            Box(
                Modifier.fillMaxWidth().height(if (compact) 226.dp else 252.dp).padding(top = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(if (compact) 206.dp else 228.dp)
                        .shadow(18.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF252D36), Color(0xFF080A0E))))
                        .border(1.dp, Color.White.copy(.22f), CircleShape)
                        .pointerInput(volume) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                wheelDrag += drag.x - drag.y
                                if (abs(wheelDrag) >= 8f) {
                                    val step = wheelDrag / 8f
                                    onVolume((volume + step * 0.006f).coerceIn(0f, 1f))
                                    wheelDrag %= 8f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                        drawCircle(Color.White.copy(.035f), style = Stroke(1.dp.toPx()))
                        drawCircle(Color.Black.copy(.28f), style = Stroke(2.dp.toPx()))
                    }
                    Text("MENU", color = PodText.copy(.8f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.TopCenter).clip(CircleShape).clickable(onClick = onMenu).padding(horizontal = 12.dp, vertical = 8.dp).padding(top = 16.dp))
                    IconButton(onClick = onPrevious, modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp).size(38.dp)) {
                        Icon(Icons.Default.SkipPrevious, "Previous", tint = PodMuted, modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = onNext, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp).size(38.dp)) {
                        Icon(Icons.Default.SkipNext, "Next", tint = PodMuted, modifier = Modifier.size(24.dp))
                    }
                    Box(
                        Modifier.size(88.dp).clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color(0xFF28313B), Color(0xFF07090D))))
                            .border(1.dp, Color.White.copy(.24f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The center is the wheel's tactile/select button; MENU is the exit action.
                    }
                    IconButton(onClick = onPlayPause, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).size(38.dp)) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = PodMuted, modifier = Modifier.size(23.dp))
                    }
                }
                Box(
                    Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, top = 4.dp).size(38.dp)
                        .clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFF303B46), Color(0xFF090C10))))
                        .border(1.dp, Color.White.copy(.2f), CircleShape),
                )
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                PodStatusDot("Battery", active = true)
                Spacer(Modifier.width(18.dp))
                PodStatusDot("Disk", active = false)
                Spacer(Modifier.weight(1f))
                Text("POD", color = PodMuted.copy(.6f), fontSize = 10.sp, letterSpacing = 4.sp)
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun PodGlassButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    primary: Boolean = false,
) {
    Box(
        Modifier.size(width = if (primary) 82.dp else 76.dp, height = 58.dp)
            .shadow(8.dp, RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.verticalGradient(
                    if (primary) listOf(Color(0xFF25323D), Color(0xFF0B1118))
                    else listOf(Color(0xFF34414B).copy(.85f), Color(0xFF10151C).copy(.9f)),
                )
            )
            .border(1.dp, Color.White.copy(.22f), RoundedCornerShape(30.dp)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(icon, description, tint = PodText, modifier = Modifier.size(if (primary) 28.dp else 24.dp))
        }
    }
}

@Composable
private fun PodStatusDot(label: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(9.dp).clip(CircleShape)
                .background(if (active) Color(0xFFFFB52E) else Color(0xFF20252C))
                .border(1.dp, Color.White.copy(.16f), CircleShape),
        )
        Spacer(Modifier.width(5.dp))
        Text(label, color = PodMuted.copy(.55f), fontSize = 9.sp)
    }
}

private fun podTime(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
