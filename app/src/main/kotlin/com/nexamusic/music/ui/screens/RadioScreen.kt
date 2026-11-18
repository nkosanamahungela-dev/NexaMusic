package com.nexamusic.music.ui.screens

import com.nexamusic.music.ui.component.sf
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFDotRadiowavesLeftAndRight
import com.composables.sfsymbols.monochrome.SFPlayFill
import com.nexamusic.music.LocalPlayerAwareWindowInsets
import com.nexamusic.music.LocalPlayerConnection
import com.nexamusic.music.R
import com.nexamusic.music.db.entities.Song
import com.nexamusic.music.models.MediaMetadata
import com.nexamusic.music.models.toMediaMetadata
import com.nexamusic.music.playback.queues.YouTubeQueue
import com.nexamusic.music.ui.component.GlassCircleButton
import com.nexamusic.music.ui.component.smoothHeaderScrollProgress
import com.nexamusic.music.ui.component.NavigationTitle
import com.nexamusic.music.ui.component.YouTubeListItem
import com.nexamusic.music.ui.ios.compat.AppTheme
import com.nexamusic.music.ui.ios.compat.Text
import com.nexamusic.music.ui.ios.component.IosGlyph
import com.nexamusic.music.ui.ios.foundation.IosTheme
import com.nexamusic.music.ui.screens.library.apple.AppleSubpage
import com.nexamusic.music.ui.component.shapes.ContinuousRoundedRectangle
import com.nexamusic.music.ui.screens.discovery.DiscoveryIcons
import com.nexamusic.music.ui.screens.discovery.DiscoveryTokens
import com.nexamusic.music.ui.utils.combinedBounceClick
import com.nexamusic.music.viewmodels.RadioStationViewModel
import com.nexamusic.music.viewmodels.RadioViewModel
import com.music.innertube.models.SongItem

/** Cards stay near this width, so a wide tab-view screen gets more columns, not bigger tiles. */
private val RadioGutter = 16.dp

@Composable
private fun RadioStationCard(
    song: Song,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    val shape = ContinuousRoundedRectangle(14.dp)
    val artist = song.artists.joinToString { it.name }.ifBlank { song.song.title }
    val stationName = stringResource(R.string.radio_station_for, artist)

    Column(
        Modifier
            .fillMaxWidth()
            .combinedBounceClick(onClick = onClick),
    ) {
        // Artwork is kept clean: station text lives below the cover, never over it.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(IosTheme.colors.secondarySystemBackground),
        ) {
            AsyncImage(
                model = song.song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(9.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(DiscoveryIcons.RadioBadge, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(DiscoveryIcons.Play, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            text = stationName,
            style = AppTheme.typography.titleSmall,
            color = IosTheme.colors.label,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp),
        )
        Text(
            text = song.song.title,
            style = AppTheme.typography.bodySmall,
            color = IosTheme.colors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun RadioOnAirCard(
    song: Song,
    isPlaying: Boolean,
    onPlay: () -> Unit,
) {
    val artist = song.artists.joinToString { it.name }.ifBlank { song.song.title }
    val stationName = stringResource(R.string.radio_station_for, artist)
    val dotAlpha = remember { Animatable(1f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                dotAlpha.animateTo(0.4f, tween(700))
                dotAlpha.animateTo(1f, tween(700))
            }
        } else {
            dotAlpha.snapTo(1f)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(DiscoveryTokens.Size.HeroHeight)
            .clip(ContinuousRoundedRectangle(28.dp))
            .background(DiscoveryTokens.AccentRed)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(7.dp).alpha(dotAlpha.value).clip(CircleShape).background(Color.White))
                Text(
                    "ON AIR",
                    color = Color.White,
                    style = AppTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stationName,
                color = Color.White,
                style = AppTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Starts with ${song.song.title}",
                color = Color.White.copy(alpha = 0.88f),
                style = AppTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box(Modifier.size(134.dp), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = song.song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(DiscoveryTokens.Size.HeroCover)
                    .graphicsLayer { rotationZ = 6f }
                    .clip(ContinuousRoundedRectangle(16.dp)),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(DiscoveryIcons.Play, contentDescription = null, tint = DiscoveryTokens.AccentRed, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun RadioScreen(
    navController: NavController,
    viewModel: RadioViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val songs by viewModel.recentStations.collectAsStateWithLifecycle()
    val currentMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val playbackActive by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val sideInset = insets.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
    val recentTitle = stringResource(R.string.radio_recent_tracks)
    val gridState = rememberLazyGridState()

    Box(
        Modifier
            .fillMaxSize()
            .background(IosTheme.colors.systemBackground),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize().smoothHeaderScrollProgress(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(
                start = sideInset + RadioGutter,
                top = maxOf(insets.calculateTopPadding(), if (sideInset > 0.dp) 96.dp else 116.dp),
                end = insets.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current) + RadioGutter,
                bottom = insets.calculateBottomPadding() + 20.dp,
            ),
        ) {
            when {
                songs == null -> item(key = "radio_loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                songs.isNullOrEmpty() -> item(key = "radio_empty", span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
                    ) {
                        IosGlyph(sf("SFDotRadiowavesLeftAndRight"),
                            IosTheme.colors.secondaryLabel,
                            34.dp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.radio_empty_history),
                            style = AppTheme.typography.bodyLarge,
                            color = IosTheme.colors.secondaryLabel,
                        )
                        TextButton(onClick = { navController.navigate(Screens.Search.route) }) {
                            Text(stringResource(R.string.search))
                        }
                    }
                }
                else -> {
                    val recentStations = songs.orEmpty()
                    // ON AIR is shown only for a station that is the current, actively playing item.
                    // Never fall back to a recently played station when playback is idle or unrelated.
                    val onAirStation = if (playbackActive) {
                        currentMetadata?.id?.let { activeId -> recentStations.firstOrNull { it.id == activeId } }
                    } else {
                        null
                    }
                    if (onAirStation != null) {
                        item(key = "radio_on_air", span = { GridItemSpan(maxLineSpan) }) {
                            RadioOnAirCard(
                                song = onAirStation,
                                isPlaying = playbackActive && currentMetadata?.id == onAirStation.id,
                                onPlay = {
                                    if (playbackActive && currentMetadata?.id == onAirStation.id) {
                                        playerConnection.player.pause()
                                    } else {
                                        playerConnection.playQueue(YouTubeQueue.radio(onAirStation.toMediaMetadata()))
                                    }
                                },
                            )
                        }
                    }
                    item(key = "radio_recent_title", span = { GridItemSpan(maxLineSpan) }) {
                        NavigationTitle(title = recentTitle)
                    }
                    gridItems(recentStations, key = { "radio_seed_${it.id}" }) { song ->
                        RadioStationCard(
                            song = song,
                            onClick = { navController.navigate(song.radioStationRoute()) },
                            onPlay = { playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata())) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RadioStationScreen(
    navController: NavController,
    seedId: String,
    seedTitle: String,
    seedArtist: String,
    seedThumbnail: String,
    viewModel: RadioStationViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasError by viewModel.hasError.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val title = seedTitle.ifBlank { stringResource(R.string.radio) }
    val stationTitle = stringResource(R.string.radio_station_for, title)
    val seedMetadata = remember(seedId, seedTitle, seedArtist, seedThumbnail) {
        MediaMetadata(
            id = seedId,
            title = seedTitle,
            artists = seedArtist.split(", ").filter(String::isNotBlank).map {
                MediaMetadata.Artist(id = null, name = it)
            },
            duration = -1,
            thumbnailUrl = seedThumbnail,
        )
    }

    LaunchedEffect(seedId) { viewModel.load(seedId) }

    AppleSubpage(
        title = stationTitle,
        onBack = { navController.navigateUp() },
        backLabel = stringResource(R.string.radio),
    ) {
        item(key = "radio_station_header") {
            RadioStationHeader(
                title = title,
                artist = seedArtist,
                thumbnail = seedThumbnail,
                onPlay = { playerConnection.playQueue(YouTubeQueue.radio(seedMetadata)) },
            )
        }

        item(key = "radio_station_tracks_title") {
            NavigationTitle(title = stringResource(R.string.radio_station_tracks))
        }

        when {
            isLoading -> item(key = "radio_station_loading") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            hasError -> item(key = "radio_station_error") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                ) {
                    Text(
                        text = stringResource(R.string.radio_station_load_error),
                        color = IosTheme.colors.secondaryLabel,
                    )
                    TextButton(onClick = { viewModel.load(seedId) }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            tracks.isNullOrEmpty() -> item(key = "radio_station_empty") {
                Text(
                    text = stringResource(R.string.radio_station_load_error),
                    color = IosTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
            else -> items(tracks.orEmpty(), key = { "radio_track_${it.id}" }) { song ->
                YouTubeListItem(
                    item = song,
                    isActive = mediaMetadata?.id == song.id,
                    isPlaying = isPlaying && mediaMetadata?.id == song.id,
                    isSwipeable = false,
                    badges = {},
                    drawHighlight = false,
                    flat = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata())) },
                    trailingContent = {
                        IosGlyph(sf("SFDotRadiowavesLeftAndRight"),
                            IosTheme.colors.secondaryLabel,
                            19.dp,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun RadioStationHeader(
    title: String,
    artist: String,
    thumbnail: String,
    onPlay: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(ContinuousRoundedRectangle(20.dp))
            .background(IosTheme.colors.secondarySystemBackground)
            .clickable(onClick = onPlay)
            .padding(14.dp),
    ) {
        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.radio_station_label),
                color = IosTheme.colors.secondaryLabel,
                fontSize = 13.sp,
            )
            Text(
                text = title,
                color = IosTheme.colors.label,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                color = IosTheme.colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GlassCircleButton(onClick = onPlay, size = 44.dp, forceFlat = true) {
            IosGlyph(sf("SFPlayFill"), IosTheme.colors.label, 19.dp)
        }
    }
}

private fun Song.radioStationRoute(): String = buildString {
    append("radio_station/").append(song.id)
    append("?title=").append(Uri.encode(song.title))
    append("&artist=").append(Uri.encode(artists.joinToString(", ") { it.name }))
    append("&thumbnail=").append(Uri.encode(song.thumbnailUrl.orEmpty()))
}
