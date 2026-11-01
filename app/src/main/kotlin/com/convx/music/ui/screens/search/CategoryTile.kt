package com.convx.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import coil3.compose.AsyncImage

/**
 * Shared mood tile used by Home, Explore and the mood browser.
 * Keeps the original public API so those screens retain their existing behaviour.
 */
@Composable
internal fun CategoryTile(
    title: String,
    color: Color = Color(0xFF34343A),
    art: String?,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit = {},
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier.clip(shape)
            .background(if (art.isNullOrBlank()) color else Color(0xFF171719))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$title, mood playlist" },
    ) {
        if (!art.isNullOrBlank()) {
            AsyncImage(
                model = art,
                contentDescription = "$title cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(color, color.copy(alpha = 0.72f))),
            ))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.38f to Color.Black.copy(alpha = 0.08f),
                    1f to Color.Black.copy(alpha = 0.84f),
                ),
            ),
        )
        androidx.compose.material3.Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 48.dp, bottom = 14.dp),
        )
        Box(
            Modifier.align(Alignment.TopEnd).padding(10.dp).size(34.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = 0.48f))
                .clickable(onClick = onPlay)
                .semantics { contentDescription = "Play $title playlist" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(12.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.12f, 0f)
                    lineTo(size.width, size.height * 0.5f)
                    lineTo(size.width * 0.12f, size.height)
                    close()
                }
                drawPath(path, Color.White)
            }
        }
    }
}
