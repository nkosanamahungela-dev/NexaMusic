/**
 * NexaMusic Project (C) 2026
 * Pod player skin inspired by Podium's rotary-player interaction model.
 * Podium source: https://github.com/GHOSTxASR/podium-music-player
 * Podium is distributed under GNU GPL v3.0; see LICENSE and NOTICE.
 */

package com.nexamusic.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.nexamusic.music.LocalPlayerConnection
import com.nexamusic.music.ui.component.BottomSheetState
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.roundToInt

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
    var wheelAngle by remember { mutableFloatStateOf(0f) }
    var scrubbing by remember { mutableStateOf(false) }
    val animatedAngle by animateFloatAsState(wheelAngle, spring(dampingRatio = 0.72f, stiffness = 420f), label = "podWheel")
    val context = LocalContext.current

    LaunchedEffect(connection, metadata?.id) {
        while (true) {
            if (!scrubbing) position = connection.player.currentPosition.coerceAtLeast(0L)
            duration = connection.player.duration.coerceAtLeast(0L)
            volume = connection.player.volume.coerceIn(0f, 1f)
            delay(250L)
        }
    }

    val title = metadata?.title ?: "Nothing playing"
    val artist = metadata?.artists?.joinToString(", ") { it.name }.orEmpty()
    val artwork = metadata?.thumbnailUrl

    Column(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF20242B), Color(0xFF101216), Color(0xFF090A0D)))
        ).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { sheetState.collapseSoft() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Close Pod player", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text("NOW PLAYING", color = Color(0xFFB8C0CC), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { sheetState.collapseSoft() }) {
                Icon(Icons.Default.Menu, contentDescription = "Menu — close player", tint = Color.White)
            }
        }

        Spacer(Modifier.weight(0.45f))
        AsyncImage(
            model = artwork,
            contentDescription = "Album artwork",
            modifier = Modifier.fillMaxWidth(0.68f).aspectRatio(1f).clip(CircleShape),
        )
        Spacer(Modifier.height(22.dp))
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(artist.ifBlank { " " }, color = Color(0xFFADB5C1), fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)

        Spacer(Modifier.height(22.dp))
        Slider(
            value = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { scrubbing = true; position = (it * duration).toLong() },
            onValueChangeFinished = { connection.seekTo(position); scrubbing = false },
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0xFF4C535E)
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatPodTime(position), color = Color(0xFFADB5C1), fontSize = 11.sp)
            Text(formatPodTime(duration), color = Color(0xFFADB5C1), fontSize = 11.sp)
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { connection.player.seekToPreviousMediaItem() }) {
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { connection.seekTo((connection.player.currentPosition - 10_000L).coerceAtLeast(0L)) }) {
                Icon(Icons.Default.FastRewind, "Back 10 seconds", tint = Color(0xFFCFD5DE))
            }
            val playScale by animateFloatAsState(if (playing) 1f else 0.94f, label = "podPlay")
            IconButton(
                onClick = { connection.togglePlayPause() },
                modifier = Modifier.size(68.dp).scale(playScale).clip(CircleShape).background(Color(0xFFE7EAF0)),
            ) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause",
                    tint = Color(0xFF11151B), modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = { connection.seekTo((connection.player.currentPosition + 10_000L).coerceAtMost(duration)) }) {
                Icon(Icons.Default.FastForward, "Forward 10 seconds", tint = Color(0xFFCFD5DE))
            }
            IconButton(onClick = { connection.player.seekToNextMediaItem() }) {
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }

        Spacer(Modifier.height(22.dp))
        Box(
            Modifier.size(218.dp).pointerInput(volume) {
                var previousAngle: Float? = null
                detectDragGestures(
                    onDragStart = { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        previousAngle = Math.toDegrees(atan2((offset.y - center.y).toDouble(), (offset.x - center.x).toDouble())).toFloat()
                    },
                    onDragEnd = { previousAngle = null },
                    onDragCancel = { previousAngle = null },
                    onDrag = { change, _ ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val current = Math.toDegrees(atan2((change.position.y - center.y).toDouble(), (change.position.x - center.x).toDouble())).toFloat()
                        val old = previousAngle
                        if (old != null) {
                            var delta = current - old
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            volume = (volume + delta / 300f).coerceIn(0f, 1f)
                            connection.player.volume = volume
                            wheelAngle += delta
                        }
                        previousAngle = current
                        change.consume()
                    }
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 13.dp.toPx()
                drawArc(Color(0xFF343B46), -225f, 270f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(Color(0xFFE8EDF5), -225f, 270f * volume, false, style = Stroke(stroke, cap = StrokeCap.Round))
                drawCircle(Color(0xFF252B34), radius = size.minDimension * 0.36f)
                drawCircle(Color(0xFF596372).copy(alpha = 0.55f), radius = size.minDimension * 0.36f, style = Stroke(1.dp.toPx()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VOLUME", color = Color(0xFF9EA8B6), fontSize = 10.sp, letterSpacing = 1.8.sp)
                Text("${(volume * 100).roundToInt()}%", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text("ROTATE", color = Color(0xFF9EA8B6), fontSize = 9.sp, letterSpacing = 1.4.sp)
            }
        }
        Spacer(Modifier.weight(0.55f))
        Text("POD", color = Color(0xFF707A89), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp,
            modifier = Modifier.padding(bottom = 24.dp))
    }
}

private fun formatPodTime(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
