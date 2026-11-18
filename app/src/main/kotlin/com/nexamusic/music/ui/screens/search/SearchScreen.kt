/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nexamusic.music.ui.screens.search

import com.nexamusic.music.utils.openSearchResults

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalDensity
import com.nexamusic.music.ui.component.GlassSegmentedControl
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import com.nexamusic.music.ui.ios.compat.Icon
import com.nexamusic.music.ui.ios.compat.IconButton
import com.nexamusic.music.ui.ios.compat.LocalContentColor
import com.nexamusic.music.ui.ios.compat.AppTheme
import com.nexamusic.music.ui.ios.compat.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import com.nexamusic.music.ui.ios.compat.Surface
import com.nexamusic.music.ui.ios.compat.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import com.nexamusic.music.ui.ios.foundation.iosRowPress
import com.nexamusic.music.ui.ios.foundation.iosGlass
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.utils.YouTubeUrlParser
import com.nexamusic.music.LocalDatabase
import com.nexamusic.music.LocalIsPlayerExpanded
import com.nexamusic.music.LocalPlayerAwareWindowInsets
import com.nexamusic.music.LocalPlayerConnection
import com.nexamusic.music.R
import com.nexamusic.music.ui.utils.rememberGridColumns
import com.nexamusic.music.constants.PauseSearchHistoryKey
import com.nexamusic.music.constants.SearchSource
import com.nexamusic.music.db.entities.SearchHistory
import com.nexamusic.music.playback.queues.YouTubeQueue
import com.nexamusic.music.ui.component.NavigationTitle
import com.nexamusic.music.ui.utils.albumRoute
import com.nexamusic.music.ui.utils.bounceClick
import com.nexamusic.music.ui.utils.combinedBounceClick
import com.nexamusic.music.ui.utils.youtubeBrowseRoute
import com.nexamusic.music.utils.rememberEnumPreference
import com.nexamusic.music.utils.rememberPreference
import com.nexamusic.music.viewmodels.MoodAndGenresViewModel
import com.nexamusic.music.viewmodels.ExploreViewModel
import com.nexamusic.music.ui.screens.search.suggestions.SuggestionsTabContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import com.nexamusic.music.ui.ios.compat.CircularWavyProgressIndicator
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.nexamusic.music.ui.component.LargeScreenTitle
import com.nexamusic.music.ui.component.LocalMenuState
import com.nexamusic.music.ui.component.YouTubeGridItem
import com.nexamusic.music.ui.menu.YouTubeAlbumMenu
import com.nexamusic.music.constants.GridThumbnailHeight
import com.nexamusic.music.constants.GridItemsSizeKey
import com.nexamusic.music.constants.GridItemSize
import com.nexamusic.music.ui.component.HeroBackground
import com.nexamusic.music.ui.component.HomeImageBackground
import com.nexamusic.music.ui.component.rememberAppBackgroundColor
import com.nexamusic.music.ui.component.rememberHeroSource
import com.nexamusic.music.ui.component.rememberHeroTint
import com.nexamusic.music.ui.theme.AppleTokens
import com.nexamusic.music.ui.theme.HeroTintedContent

import com.nexamusic.music.ui.component.LocalNavSearchState
import com.nexamusic.music.ui.component.shapes.ContinuousRoundedRectangle
import com.nexamusic.music.ui.component.backdrop.backdrops.layerBackdrop
import com.nexamusic.music.ui.component.backdrop.backdrops.rememberBackdropFreeze
import com.nexamusic.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.nexamusic.music.ui.component.backdrop.catalog.components.LiquidBottomTab
import com.nexamusic.music.ui.component.backdrop.catalog.components.LiquidBottomTabs
import androidx.compose.ui.text.font.FontWeight
import com.nexamusic.music.ui.component.GlassCircleButton
import com.nexamusic.music.ui.component.TopFadeBlur
import com.nexamusic.music.ui.component.SmoothHeaderTokens
import com.nexamusic.music.ui.component.rememberSmoothHeaderTitleMotion
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    pureBlack: Boolean,
    accountImageUrl: String? = null,
    accountName: String = "",
    onAccountClick: () -> Unit = { navController.navigate(com.nexamusic.music.ui.screens.Screens.Settings.route) },
) {
    val database = LocalDatabase.current.get()
    val recentSearches by remember(database) { database.searchHistory() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isPlayerExpanded = LocalIsPlayerExpanded.current
    val playerConnection = LocalPlayerConnection.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Read the shared nav-bar query so results update as the user types.
    val navSearch = LocalNavSearchState.current
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)

    val localOnly = com.nexamusic.music.LocalLocalOnlyMode.current

    val onSearchFromSuggestion: (String) -> Unit = remember {
        { searchQuery ->
            if (searchQuery.isNotEmpty()) {
                focusManager.clearFocus()
                when (val parsedUrl = YouTubeUrlParser.parse(searchQuery)) {
                    is YouTubeUrlParser.ParsedUrl.Video -> {
                        playerConnection?.playQueue(
                            YouTubeQueue(WatchEndpoint(videoId = parsedUrl.id)),
                        )
                    }
                    is YouTubeUrlParser.ParsedUrl.Artist -> {
                        navController.navigate("artist/${parsedUrl.id}")
                    }
                    null -> {
                        navController.openSearchResults(searchQuery)
                    }
                }
                if (!pauseSearchHistory) {
                    coroutineScope.launch(Dispatchers.IO) {
                        database.query {
                            insert(SearchHistory(query = searchQuery))
                        }
                    }
                }
            }
        }
    }

    // Keep the browse page and results page on the same system background.
    val colors = com.nexamusic.music.ui.ios.foundation.IosTheme.colors
    val pageColor = if (colors.isDark) SearchDesignTokens.dark.page else SearchDesignTokens.light.page
    val context = LocalContext.current
    val view = LocalView.current
    val window = (context as? android.app.Activity)?.window
    val statusTop = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
        .asPaddingValues().calculateTopPadding()

    // Search uses the app's resolved appearance, even when it differs from Android's setting.
    DisposableEffect(window, view) {
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val previousLightStatus = controller?.isAppearanceLightStatusBars
        val previousLightNavigation = controller?.isAppearanceLightNavigationBars
        val previousStatusColor = window?.statusBarColor
        val previousNavigationColor = window?.navigationBarColor
        val previousStatusContrast = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            window?.isStatusBarContrastEnforced else null
        val previousNavigationContrast = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            window?.isNavigationBarContrastEnforced else null
        onDispose {
            if (controller != null) {
                previousLightStatus?.let { controller.isAppearanceLightStatusBars = it }
                previousLightNavigation?.let { controller.isAppearanceLightNavigationBars = it }
            }
            previousStatusColor?.let { window?.statusBarColor = it }
            previousNavigationColor?.let { window?.navigationBarColor = it }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                previousStatusContrast?.let { window?.isStatusBarContrastEnforced = it }
                previousNavigationContrast?.let { window?.isNavigationBarContrastEnforced = it }
            }
        }
    }
    SideEffect {
        window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = !colors.isDark
                isAppearanceLightNavigationBars = !colors.isDark
            }
            it.statusBarColor = android.graphics.Color.TRANSPARENT
            it.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                it.isStatusBarContrastEnforced = false
                it.isNavigationBarContrastEnforced = false
            }
        }
    }
    // Respect the real status-bar/cutout inset on every device, with 8dp breathing room.
    val titleTop = statusTop + 8.dp
    val browseGridState = rememberLazyGridState()
    val headerDensity = LocalDensity.current
    val bottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val sideInset = LocalPlayerAwareWindowInsets.current
        .asPaddingValues()
        .calculateStartPadding(LocalLayoutDirection.current)
    val searching = navSearch.keyboardActive || navSearch.query.text.isNotEmpty() || localOnly

    Box(
        Modifier
            .fillMaxSize()
            .background(pageColor),
    ) {
        if (!searching) {
            // Frosted strip under the status bar once tiles scroll up into it.
            val browseHaze = remember { HazeState() }
            val scopePinned = browseGridState.firstVisibleItemIndex >= 2
            val headerProgressProvider = {
                val scrollDp = if (browseGridState.firstVisibleItemIndex > 0) {
                    SmoothHeaderTokens.ScrollDistance.value
                } else {
                    with(headerDensity) { browseGridState.firstVisibleItemScrollOffset.toDp().value }
                }
                SmoothHeaderTokens.progress(scrollDp)
            }
            val searchTitleMotion = rememberSmoothHeaderTitleMotion(headerProgressProvider)
            Box(Modifier.fillMaxSize().hazeSource(browseHaze)) {
                AppleSearchBrowse(
                    navController = navController,
                    contentPadding = PaddingValues(start = sideInset, top = titleTop, bottom = bottomPadding + 16.dp),
                    gridState = browseGridState,
                    header = { AppleSearchHeader(accountImageUrl, accountName, onAccountClick,
                        progressProvider = headerProgressProvider, horizontalInset = 0.dp, bottomInset = 2.dp) },
                    recentSearches = recentSearches.map { it.query }.distinct().take(12),
                    onRecentSearchClick = onSearchFromSuggestion,
                    onClearRecent = {
                        coroutineScope.launch(Dispatchers.IO) {
                            database.query { clearSearchHistory() }
                        }
                    },
                    scope = navSearch.searchSource,
                    onScopeChange = { selected ->
                        if (selected != navSearch.searchSource) navSearch.onToggleSource()
                    },
                )
            }
            TopFadeBlur(
                hazeState = browseHaze,
                pageColor = pageColor,
                scrimColor = pageColor,
                scrimOpacity = SmoothHeaderTokens.TintPeakOpacity,
                modifier = Modifier.align(Alignment.TopCenter),
                heightOverride = statusTop + SmoothHeaderTokens.HeaderContentHeight +
                    if (scopePinned) SmoothHeaderTokens.PinnedRowHeight else 0.dp,
                progressProvider = headerProgressProvider,
            )
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = statusTop).height(SmoothHeaderTokens.TopBarContentHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.search),
                    style = AppTheme.typography.titleMedium.copy(fontSize = SmoothHeaderTokens.SmallTitleSize),
                    color = colors.label,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = searchTitleMotion.compactAlpha() }.clearAndSetSemantics { },
                )
            }
            if (scopePinned) {
                Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = statusTop + SmoothHeaderTokens.TopBarContentHeight).height(SmoothHeaderTokens.PinnedRowHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    SearchScopeSwitch(
                        scope = navSearch.searchSource,
                        onSelect = { if (it != navSearch.searchSource) navSearch.onToggleSource() },
                    )
                }
            }
        } else {
            val searchBackdrop = com.nexamusic.music.ui.component.backdrop.backdrops.rememberLayerBackdrop(
                onDraw = remember(pageColor) { { drawRect(pageColor); drawContent() } },
            )
            val focusedTop = statusTop + 8.dp
            Column(Modifier.fillMaxSize().padding(top = focusedTop)) {
                if (navSearch.canToggleSource) {
                    SourceSwitch(
                        source = navSearch.searchSource,
                        onSelect = { if (it != navSearch.searchSource) navSearch.onToggleSource() },
                        modifier = Modifier.padding(start = sideInset + 20.dp, end = 20.dp),
                    )
                }
                val searchStateHolder = rememberSaveableStateHolder()
                val reduceMotion = !android.animation.ValueAnimator.areAnimatorsEnabled()
                val searchLayoutDirection = LocalLayoutDirection.current
                val contentSlidePx = with(LocalDensity.current) { 12.dp.roundToPx() }
                Box(Modifier.weight(1f).background(pageColor)
                    .layerBackdrop(searchBackdrop)) {
                    AnimatedContent(
                        targetState = navSearch.searchSource,
                        contentKey = { it },
                        label = "searchSourceContent",
                        transitionSpec = {
                            if (reduceMotion) {
                                fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                            } else {
                                val move = if (targetState == SearchSource.LOCAL) 1 else -1
                                val rtlMove = if (searchLayoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) -move else move
                                (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { -rtlMove * contentSlidePx }) togetherWith
                                    (fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { rtlMove * contentSlidePx })
                            }
                        },
                    ) { source ->
                        searchStateHolder.SaveableStateProvider(source) {
                            when (source) {
                                SearchSource.LOCAL -> LocalSearchScreen(
                                    query = navSearch.query.text,
                                    navController = navController,
                                    onDismiss = { },
                                    pureBlack = pureBlack
                                )
                                SearchSource.ONLINE -> OnlineSearchScreen(
                                    query = navSearch.query.text,
                                    onQueryChange = navSearch.onQueryChange,
                                    navController = navController,
                                    onSearch = { onSearchFromSuggestion(it) },
                                    onDismiss = { },
                                    pureBlack = pureBlack,
                                    contentColor = colors.label,
                                    referenceHistory = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Keep the keyboard closed on resume; the navigation bar handles search focus.
    DisposableEffect(lifecycleOwner, isPlayerExpanded) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlayerExpanded) {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        navSearch.onCloseKeyboard()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    navSearch.onCloseKeyboard()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (isPlayerExpanded) {
            keyboardController?.hide()
            focusManager.clearFocus()
            navSearch.onCloseKeyboard()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
fun ExploreTabContent(
    navController: NavController,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val moodAndGenresList by viewModel.moodAndGenres.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        moodAndGenresList?.forEach { section ->
            item {
                NavigationTitle(title = section.title)
            }
            val rows = section.items.chunked(2)
            items(rows, key = { row -> row.joinToString("|") { it.endpoint.browseId } }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                ) {
                    row.forEach { item ->
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .weight(1f)
                                .padding(6.dp)
                                .height(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(LocalContentColor.current.copy(alpha = 0.1f))
                                .bounceClick {
                                    navController.navigate(youtubeBrowseRoute(item.endpoint.browseId, item.endpoint.params))
                                }
                                .padding(horizontal = 14.dp)
                        ) {
                            Text(
                                text = item.title,
                                style = AppTheme.typography.labelLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    repeat(2 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (moodAndGenresList == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator()
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun AlbumsTabContent(
    navController: NavController,
    viewModel: ExploreViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata by (playerConnection?.mediaMetadata?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })
    val isPlaying by (playerConnection?.isEffectivelyPlaying?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) })
    val coroutineScope = rememberCoroutineScope()
    
    val explorePage by viewModel.explorePage.collectAsStateWithLifecycle()
    val newReleaseAlbums = explorePage?.newReleaseAlbums

    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    if (newReleaseAlbums == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularWavyProgressIndicator()
        }
    } else {
        LazyVerticalGrid(
            columns = rememberGridColumns(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 12.dp,
                end = 12.dp,
                bottom = 12.dp + contentPadding.calculateBottomPadding()
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = newReleaseAlbums.distinctBy { it.id },
                key = { it.id }
            ) { album ->
                YouTubeGridItem(
                    item = album,
                    isActive = mediaMetadata?.album?.id == album.id,
                    isPlaying = isPlaying,
                    coroutineScope = coroutineScope,
                    fillMaxWidth = true,
                    modifier = Modifier
                        .combinedBounceClick(
                            onClick = {
                                navController.navigate(albumRoute(album.id, album.thumbnail))
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    YouTubeAlbumMenu(
                                        albumItem = album,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        )
                )
            }
        }
    }
}

@Composable
fun DynamicSearchPlaceholder(searchSource: SearchSource, style: TextStyle) {
    Text(
        text = stringResource(
            when (searchSource) {
                SearchSource.ONLINE -> R.string.search_yt_music
                SearchSource.LOCAL -> R.string.search_library
            }
        ),
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}


/** Search toolbar with a title and a 44dp profile button. */
@Composable
private fun AppleSearchHeader(
    accountImageUrl: String?,
    accountName: String,
    onAccountClick: () -> Unit,
    progressProvider: () -> Float,
    horizontalInset: androidx.compose.ui.unit.Dp = 16.dp,
    bottomInset: androidx.compose.ui.unit.Dp = 10.dp,
 ) {
    val profileDescription = stringResource(R.string.account)
    val titleMotion = rememberSmoothHeaderTitleMotion(progressProvider)
    Row(
        modifier = Modifier.padding(start = horizontalInset, end = horizontalInset, bottom = bottomInset)
            .fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = stringResource(R.string.search),
            style = com.nexamusic.music.ui.screens.library.apple.SfStyle(SmoothHeaderTokens.BigTitleSize, SmoothHeaderTokens.BigTitleLineHeight, FontWeight.ExtraBold, SmoothHeaderTokens.BigTitleLetterSpacing),
            color = com.nexamusic.music.ui.ios.foundation.IosTheme.colors.label,
            maxLines = 1,
            modifier = Modifier.weight(1f).height(46.dp).semantics { heading() }.graphicsLayer {
                val p = progressProvider()
                alpha = titleMotion.largeAlpha()
                translationY = if (titleMotion.transformsEnabled) -SmoothHeaderTokens.BigTitleTravel.toPx() * p else 0f
                val scale = if (titleMotion.transformsEnabled) SmoothHeaderTokens.bigTitleScale(p) else 1f
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            },
        )
        Box(
            Modifier.size(44.dp).graphicsLayer {
                val p = progressProvider()
                alpha = SmoothHeaderTokens.profileOpacity(p)
                val scale = if (titleMotion.transformsEnabled) SmoothHeaderTokens.profileScale(p) else 1f
                scaleX = scale
                scaleY = scale
            }.clip(CircleShape)
                .iosRowPress(onClick = onAccountClick)
                .semantics { contentDescription = profileDescription },
        ) {
            com.nexamusic.music.ui.component.AccountAvatar(accountImageUrl, accountName)
        }
    }
}

/**
 * Search source selector. Selection and persistence remain owned by NavSearchState.
 */
@Composable
private fun SourceSwitch(
    source: SearchSource,
    onSelect: (SearchSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSegmentedControl(
        titles = listOf(
            stringResource(R.string.apple_search_catalog),
            stringResource(R.string.apple_search_library),
        ),
        selectedIndex = if (source == SearchSource.ONLINE) 0 else 1,
        onSelectionChange = { index ->
            onSelect(if (index == 0) SearchSource.ONLINE else SearchSource.LOCAL)
        },
        modifier = modifier,
        reduceMotion = !android.animation.ValueAnimator.areAnimatorsEnabled(),
    )
}
