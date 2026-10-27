/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.convx.music.ui.utils.youtubeBrowseRoute
import coil3.compose.AsyncImage
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.ios.foundation.iosRowPress
import com.convx.music.ui.screens.library.apple.SfStyle
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.MoodAndGenres
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/**
 * Premium Search browse content. Covers stay unmodified and edge-to-edge.
 * Category artwork is taken from a playlist/album on that category page, never a song
 * or artist thumbnail. If no suitable cover exists, the tile stays on a dark gradient.
 */
@HiltViewModel
class AppleSearchViewModel @Inject constructor() : ViewModel() {
    private val _categories = MutableStateFlow<List<MoodAndGenres.Item>?>(null)
    val categories = _categories.asStateFlow()

    private val _art = MutableStateFlow<Map<String, String?>>(artCache.toMap())
    val art = _art.asStateFlow()

    // Bound artwork requests as categories scroll into view.
    private val gate = Semaphore(4)
    private val pending = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    // IO, not Main: Ktor decodes the response on the calling dispatcher.
    init {
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.moodAndGenres().onSuccess { sections ->
                _categories.value = sections.flatMap { it.items }.distinctBy { it.endpoint.browseId + it.endpoint.params }
            }
        }
    }

    fun loadArt(item: MoodAndGenres.Item) {
        val key = item.key
        if (_art.value.containsKey(key) || !pending.add(key)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                gate.withPermit {
                    val url = YouTube.browse(item.endpoint.browseId, item.endpoint.params).getOrNull()
                        ?.items?.asSequence()?.flatMap { it.items.asSequence() }
                        // Only category playlist/album covers; never borrow a song or artist image.
                        ?.firstOrNull { it is PlaylistItem || it is AlbumItem }?.thumbnail
                    // ConcurrentHashMap rejects null; a category may have no artwork.
                    if (url != null) artCache[key] = url
                    _art.update { it + (key to url) }
                }
            } finally {
                pending.remove(key)
            }
        }
    }

    private companion object {
        val artCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    }
}

private val MoodAndGenres.Item.key get() = endpoint.browseId + "|" + endpoint.params

@Composable
fun AppleSearchBrowse(
    navController: NavController,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
    viewModel: AppleSearchViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val art by viewModel.art.collectAsStateWithLifecycle()
    val items = categories.orEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val startInset = contentPadding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
        val columns = maxOf(2, ((maxWidth - startInset - 24.dp) / 189.dp).toInt())
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = startInset + 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search-header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                header()
            }
            if (items.isNotEmpty()) {
                item(key = "featured-heading", span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
                    Text(
                        "Moods & moments",
                        style = SfStyle(22.sp, 28.sp, FontWeight.Bold, (-0.45).sp),
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                item(key = "featured-row", span = { GridItemSpan(maxLineSpan) }, contentType = "featured-row") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 4.dp),
                    ) {
                        rowItems(items.take(8), key = { "featured:${it.key}" }) { item ->
                            LaunchedEffect(item.key) { viewModel.loadArt(item) }
                            FeaturedMoodCard(
                                title = item.title,
                                art = art[item.key],
                                modifier = Modifier.width(268.dp).height(158.dp),
                                onClick = {
                                    navController.navigate(youtubeBrowseRoute(item.endpoint.browseId, item.endpoint.params))
                                },
                            )
                        }
                    }
                }
                item(key = "browse-heading", span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
                    Text(
                        "Browse all",
                        style = SfStyle(22.sp, 28.sp, FontWeight.Bold, (-0.45).sp),
                        color = Color.White,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                }
                items(items, key = { it.key }, contentType = { "category-tile" }) { item ->
                    LaunchedEffect(item.key) { viewModel.loadArt(item) }
                    val index = items.indexOf(item)
                    CategoryTile(
                        title = item.title,
                        art = art[item.key],
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (index % 4 == 0 || index % 4 == 3) 182.dp else 150.dp),
                        onPlay = {
                            navController.navigate(youtubeBrowseRoute(item.endpoint.browseId, item.endpoint.params))
                        },
                        onClick = {
                            navController.navigate(youtubeBrowseRoute(item.endpoint.browseId, item.endpoint.params))
                        },
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Text("Loading moods & genres…", color = SearchPalette.Secondary)
                    }
                }
            }
        }
    }
}

/** Horizontal featured card: official cover, dark text-safe fade, compact source label. */
@Composable
private fun FeaturedMoodCard(
    title: String,
    art: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.clip(shape)
            .background(SearchPalette.Placeholder)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$title, mood playlist" },
    ) {
        CategoryCover(art = art, title = title, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .03f),
                    .38f to Color.Black.copy(alpha = .08f),
                    1f to Color.Black.copy(alpha = .88f),
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(title, color = Color.White, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("YouTube Music", color = Color.White.copy(alpha = .78f), fontSize = 13.sp)
        }
        Text(
            "MOOD",
            color = Color.White.copy(alpha = .92f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .42f))
                .padding(horizontal = 9.dp, vertical = 6.dp),
        )
    }
}

/** Two-column browse tile; play affordance is separate from the category-page tap. */
@Composable
internal fun CategoryTile(
    title: String,
    art: String?,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier.clip(shape).background(SearchPalette.Placeholder)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$title, mood playlist" },
    ) {
        CategoryCover(art = art, title = title, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    .36f to Color.Black.copy(alpha = .08f),
                    1f to Color.Black.copy(alpha = .88f),
                ),
            ),
        )
        Text(
            title,
            color = Color.White,
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 48.dp, bottom = 14.dp),
        )
        Box(
            Modifier.align(Alignment.TopEnd).padding(10.dp).size(34.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = .48f))
                .clickable(onClick = onPlay)
                .semantics { contentDescription = "Play $title playlist" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(12.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * .12f, 0f)
                    lineTo(size.width, size.height * .5f)
                    lineTo(size.width * .12f, size.height)
                    close()
                }
                drawPath(path, Color.White)
            }
        }
    }
}

/** Cover images are never tinted or duotoned; dark neutral placeholder while loading/missing. */
@Composable
private fun CategoryCover(art: String?, title: String, modifier: Modifier = Modifier) {
    val imageAlpha by animateFloatAsState(
        targetValue = if (art.isNullOrBlank()) 0f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "search-cover-fade",
    )
    Box(modifier.background(
        Brush.linearGradient(
            colors = listOf(SearchPalette.Placeholder, SearchPalette.PlaceholderHighlight, SearchPalette.Placeholder),
        ),
    )) {
        if (!art.isNullOrBlank()) {
            AsyncImage(
                model = art,
                contentDescription = "$title cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = imageAlpha },
            )
        }
        if (art.isNullOrBlank()) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to SearchPalette.PlaceholderHighlight,
                        1f to SearchPalette.Placeholder,
                    ),
                ),
            )
        }
    }
}

private object SearchPalette {
    val Background = Color(0xFF000000)
    val Secondary = Color(0xFFA1A1A6)
    val Accent = Color(0xFFFF4B6B)
    val Placeholder = Color(0xFF171719)
    val PlaceholderHighlight = Color(0xFF29292D)
}

