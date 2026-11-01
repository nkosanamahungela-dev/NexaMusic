package com.convx.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.convx.music.constants.SearchSource
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.ios.foundation.IosTheme
import com.convx.music.utils.openSearchResults
import com.convx.music.ui.utils.youtubeBrowseRoute
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.PlaylistItem
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

@HiltViewModel
class AppleSearchViewModel @Inject constructor() : ViewModel() {
    private val _categories = MutableStateFlow<List<MoodAndGenres.Item>?>(null)
    val categories = _categories.asStateFlow()
    private val _art = MutableStateFlow<Map<String, String?>>(artCache.toMap())
    val art = _art.asStateFlow()
    private val gate = Semaphore(4)
    private val pending = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

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
                        ?.firstOrNull { it is PlaylistItem || it is AlbumItem }?.thumbnail
                    if (url != null) artCache[key] = url
                    _art.update { it + (key to url) }
                }
            } finally { pending.remove(key) }
        }
    }
    private companion object { val artCache = java.util.concurrent.ConcurrentHashMap<String, String>() }
}

private val MoodAndGenres.Item.key get() = endpoint.browseId + "|" + endpoint.params

private data class SearchTileSpec(val title: String, val terms: List<String>, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val activities = listOf(
    SearchTileSpec("Workout", listOf("workout", "fitness"), SearchIcons.workout),
    SearchTileSpec("Focus", listOf("focus", "study"), SearchIcons.focus),
    SearchTileSpec("Sleep", listOf("sleep", "bedtime"), SearchIcons.sleep),
    SearchTileSpec("Commute", listOf("commute", "driving"), SearchIcons.commute),
    SearchTileSpec("Party", listOf("party", "celebration"), SearchIcons.party),
    SearchTileSpec("Chill", listOf("chill", "relax"), SearchIcons.chill),
    SearchTileSpec("Energise", listOf("energise", "energy", "energize"), SearchIcons.energise),
    SearchTileSpec("Feel-good", listOf("feel-good", "feel good", "happy"), SearchIcons.feelGood),
)
private val moreMoods = listOf(
    SearchTileSpec("Romance", listOf("romance", "love"), SearchIcons.romance),
    SearchTileSpec("Sad", listOf("sad", "heartbreak"), SearchIcons.sad),
    SearchTileSpec("Christmas", listOf("christmas", "holiday"), SearchIcons.christmas),
    SearchTileSpec("Metal", listOf("metal"), SearchIcons.metal),
    SearchTileSpec("Hip-hop", listOf("hip-hop", "hip hop", "rap"), SearchIcons.hipHop),
)

@Composable
fun AppleSearchBrowse(
    navController: NavController,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
    recentSearches: List<String> = emptyList(),
    onRecentSearchClick: (String) -> Unit = {},
    onClearRecent: () -> Unit = {},
    scope: SearchSource = SearchSource.ONLINE,
    onScopeChange: (SearchSource) -> Unit = {},
    viewModel: AppleSearchViewModel = hiltViewModel(),
) {
    val all by viewModel.categories.collectAsStateWithLifecycle()
    val art by viewModel.art.collectAsStateWithLifecycle()
    val c = IosTheme.colors
    val t = if (c.isDark) SearchDesignTokens.dark else SearchDesignTokens.light
    val categories = all.orEmpty()
    val activityItems = activities.map { spec -> spec to categories.firstOrNull { item -> spec.terms.any { item.title.contains(it, true) } } }
    val moodItems = moreMoods.map { spec -> spec to categories.firstOrNull { item -> spec.terms.any { item.title.contains(it, true) } } }
    val excluded = (activityItems + moodItems).mapNotNull { it.second?.key }.toSet()
    val genres = categories.filter { it.key !in excluded }
    val featured = categories.take(8)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(SearchDesignTokens.pageSide, contentPadding.calculateTopPadding(),
            SearchDesignTokens.pageSide, contentPadding.calculateBottomPadding() + 36.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column { header(); ScopeSwitch(scope, onScopeChange, t) }
        }
        if (recentSearches.isNotEmpty()) {
            item(key = "recent-title", span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Recent searches", "Clear", t, onClearRecent) }
            item(key = "recent-row", span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems(recentSearches.distinct().take(12), key = { it }) { query ->
                        Row(Modifier.clip(CircleShape).background(t.surface).border(1.dp, t.border, CircleShape)
                            .clickable { onRecentSearchClick(query) }.padding(horizontal = 13.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(SearchIcons.recent, null, tint = t.secondary, modifier = Modifier.size(16.dp))
                            Text(query, color = t.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (featured.isNotEmpty()) {
            item(key = "moods-title", span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Moods & moments", "See all", t) {
                featured.firstOrNull()?.let { navController.navigate(youtubeBrowseRoute(it.endpoint.browseId, it.endpoint.params)) }
            } }
            item(key = "moods-row", span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems(featured, key = { it.key }) { item ->
                        LaunchedEffect(item.key) { viewModel.loadArt(item) }
                        FeaturedTile(item.title, art[item.key], SearchDesignTokens.colorFor(item.title, featured.indexOf(item)),
                            Modifier.width(268.dp).height(170.dp)) {
                            navController.navigate(youtubeBrowseRoute(item.endpoint.browseId, item.endpoint.params))
                        }
                    }
                }
            }
        }
        if (activityItems.isNotEmpty()) {
            item(key = "activities-title", span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Activities", null, t, null) }
            item(key = "activities-row", span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems(activityItems, key = { it.first.title }) { (spec, item) ->
                        ActivityTile(spec.title, spec.icon, SearchDesignTokens.colorFor(spec.title, activities.indexOf(spec)),
                            Modifier.width(150.dp).height(96.dp)) {
                            if (item != null) navController.navigate(youtubeBrowseRoute(item.endpoint.browseId, item.endpoint.params))
                            else navController.openSearchResults(spec.title + " music")
                        }
                    }
                }
            }
        }
        if (genres.isNotEmpty()) {
            item(key = "genres-title", span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Genres", null, t, null) }
            items(genres, key = { it.key }) { item ->
                LaunchedEffect(item.key) { viewModel.loadArt(item) }
                GenreTile(item.title, art[item.key], SearchDesignTokens.colorFor(item.title, genres.indexOf(item)),
                    Modifier.fillMaxWidth().height(104.dp)) {
                    navController.navigate(youtubeBrowseRoute(item.endpoint.browseId, item.endpoint.params))
                }
            }
        }
        item(key = "decades-title", span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Decades", null, t, null) }
        item(key = "decades", span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("80s" to "1980s", "90s" to "1990s", "00s" to "2000s").forEachIndexed { index, pair ->
                    DecadeTile(pair.first, pair.second, SearchDesignTokens.colorFor(pair.second, index),
                        Modifier.weight(1f).height(108.dp)) { navController.openSearchResults(pair.second + " music") }
                }
            }
        }
        if (moodItems.isNotEmpty()) {
            item(key = "more-moods-title", span = { GridItemSpan(maxLineSpan) }) { SectionTitle("More moods", null, t, null) }
            item(key = "more-moods", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(t.surface)
                    .border(1.dp, t.border, RoundedCornerShape(22.dp))) {
                    moodItems.forEachIndexed { index, pair ->
                        MoreMoodRow(pair.first.title, pair.first.icon, SearchDesignTokens.colorFor(pair.first.title, index), t) {
                            if (pair.second != null) navController.navigate(youtubeBrowseRoute(pair.second.endpoint.browseId, pair.second.endpoint.params))
                            else navController.openSearchResults(pair.first.title + " music")
                        }
                        if (index < moodItems.lastIndex) Spacer(Modifier.fillMaxWidth().padding(start = 56.dp).height(1.dp).background(t.border))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeSwitch(scope: SearchSource, onSelect: (SearchSource) -> Unit, t: SearchDesignTokens.Theme) {
    Row(Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(t.surface)
        .border(1.dp, t.border, CircleShape).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(SearchSource.ONLINE to "YouTube Music", SearchSource.LOCAL to "Your Library").forEach { (value, label) ->
            val selected = scope == value
            Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                .background(if (selected) t.selectedScope else Color.Transparent).clickable { onSelect(value) },
                contentAlignment = Alignment.Center) {
                Text(label, color = if (selected) t.selectedScopeText else t.secondary, fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String?, t: SearchDesignTokens.Theme, onAction: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 1.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = t.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (action != null && onAction != null) {
            Row(Modifier.clickable(onClick = onAction).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(action, color = t.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (action == "See all") Icon(SearchIcons.chevron, null, tint = t.accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun FeaturedTile(title: String, art: String?, palette: SearchDesignTokens.TileColor, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(palette.fill).clickable(onClick = onClick)
        .semantics { contentDescription = "$title mood playlist" }) {
        Column(Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Text(title, color = palette.content, fontSize = 26.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("YouTube Music", color = palette.content.copy(alpha = .88f), fontSize = 13.sp)
        }
        if (!art.isNullOrBlank()) AsyncImage(model = art, contentDescription = "$title official cover", contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.BottomEnd).fillMaxWidth(.58f).height(142.dp).graphicsLayer { rotationZ = 12f })
        Box(Modifier.align(Alignment.BottomStart).padding(12.dp).size(30.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center) {
            Icon(SearchIcons.play, null, tint = Color(0xFF1A120C), modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun ActivityTile(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, palette: SearchDesignTokens.TileColor,
    modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(palette.fill).clickable(onClick = onClick)
        .padding(14.dp).semantics { contentDescription = name }) {
        Icon(icon, null, tint = palette.content, modifier = Modifier.align(Alignment.TopStart).size(28.dp))
        Text(name, color = palette.content, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun GenreTile(title: String, art: String?, palette: SearchDesignTokens.TileColor, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(palette.fill).clickable(onClick = onClick)
        .semantics { contentDescription = "$title genre" }) {
        Text(title, color = palette.content, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).width(110.dp))
        if (!art.isNullOrBlank()) AsyncImage(model = art, contentDescription = "$title official cover", contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.BottomEnd).fillMaxWidth(.58f).height(92.dp).graphicsLayer { rotationZ = 12f })
        Box(Modifier.align(Alignment.BottomStart).padding(9.dp).size(24.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center) {
            Icon(SearchIcons.play, null, tint = Color(0xFF1A120C), modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun DecadeTile(number: String, label: String, palette: SearchDesignTokens.TileColor, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(palette.fill).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween) {
        Text(number, color = palette.content, fontSize = 29.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = palette.content, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MoreMoodRow(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, palette: SearchDesignTokens.TileColor,
    t: SearchDesignTokens.Theme, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(palette.fill), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = palette.content, modifier = Modifier.size(19.dp))
        }
        Text(name, color = t.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(SearchIcons.chevron, null, tint = t.secondary, modifier = Modifier.size(18.dp))
    }
}
