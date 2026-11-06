/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nexamusic.music.ui.screens

import com.nexamusic.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import com.nexamusic.music.ui.ios.compat.Icon
import com.nexamusic.music.ui.ios.compat.Text
import com.nexamusic.music.ui.ios.compat.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.nexamusic.music.LocalPlayerAwareWindowInsets
import com.nexamusic.music.R
import com.nexamusic.music.ui.utils.rememberGridColumns
import com.nexamusic.music.constants.GridItemSize
import com.nexamusic.music.constants.GridItemsSizeKey
import com.nexamusic.music.constants.GridThumbnailHeight
import com.nexamusic.music.ui.component.ChipsRow
import com.nexamusic.music.ui.component.IconButton
import com.nexamusic.music.ui.component.LocalMenuState
import com.nexamusic.music.ui.component.YouTubeGridItem
import com.nexamusic.music.ui.component.shimmer.GridItemPlaceHolder
import com.nexamusic.music.ui.component.shimmer.ShimmerHost
import com.nexamusic.music.ui.menu.YouTubeAlbumMenu
import com.nexamusic.music.ui.menu.YouTubeArtistMenu
import com.nexamusic.music.ui.menu.YouTubePlaylistMenu
import com.nexamusic.music.ui.utils.backToMain
import com.nexamusic.music.ui.utils.bounceClick
import com.nexamusic.music.ui.utils.combinedBounceClick
import com.nexamusic.music.ui.utils.albumRoute
import com.nexamusic.music.ui.utils.artistRoute
import com.nexamusic.music.ui.utils.onlinePlaylistRoute
import com.nexamusic.music.utils.rememberEnumPreference
import com.nexamusic.music.viewmodels.AccountContentType
import com.nexamusic.music.viewmodels.AccountViewModel
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.nexamusic.music.ui.component.ScreenLargeTitle
import com.nexamusic.music.ui.component.ScreenScaffold
import com.nexamusic.music.ui.component.rememberTitleCollapseProgressState
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    val coroutineScope = rememberCoroutineScope()

    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val selectedContentType by viewModel.selectedContentType.collectAsStateWithLifecycle()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val gridState = rememberLazyGridState()
    val accountTitleProgress = rememberTitleCollapseProgressState(gridState)

    ScreenScaffold(
        title = stringResource(R.string.account),
        titleAlpha = 0f,
        titleAlphaProvider = { accountTitleProgress.value },
        onBack = navController::navigateUp,
        onBackLongClick = navController::backToMain,
    ) { chrome ->
    LazyVerticalGrid(
        state = gridState,
        columns = rememberGridColumns(),
        modifier = chrome.contentModifier(),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            ScreenLargeTitle(stringResource(R.string.account))
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            ChipsRow(
                chips = listOf(
                    AccountContentType.PLAYLISTS to stringResource(R.string.filter_playlists),
                    AccountContentType.ALBUMS to stringResource(R.string.filter_albums),
                    AccountContentType.ARTISTS to stringResource(R.string.filter_artists),
                ),
                currentValue = selectedContentType,
                onValueUpdate = { viewModel.setSelectedContentType(it) },
            )
        }

        when (selectedContentType) {
            AccountContentType.PLAYLISTS -> {
                items(
                    items = playlists.orEmpty().distinctBy { it.id },
                    key = { it.id },
                ) { item ->
                    YouTubeGridItem(
                        item = item,
                        fillMaxWidth = true,
                        modifier = Modifier
                            .combinedBounceClick(
                                onClick = {
                                    navController.navigate(onlinePlaylistRoute(item.id, item.title, item.thumbnail))
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        YouTubePlaylistMenu(
                                            playlist = item,
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ),
                    )
                }

                if (playlists == null) {
                    items(8) {
                        ShimmerHost {
                            GridItemPlaceHolder(fillMaxWidth = true)
                        }
                    }
                }
            }

            AccountContentType.ALBUMS -> {
                items(
                    items = albums.orEmpty().distinctBy { it.id },
                    key = { it.id }
                ) { item ->
                    YouTubeGridItem(
                        item = item,
                        fillMaxWidth = true,
                        modifier = Modifier
                            .combinedBounceClick(
                                onClick = {
                                    navController.navigate(albumRoute(item.id, item.thumbnail))
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        YouTubeAlbumMenu(
                                            albumItem = item,
                                            navController = navController,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            )
                    )
                }

                if (albums == null) {
                    items(8) {
                        ShimmerHost {
                            GridItemPlaceHolder(fillMaxWidth = true)
                        }
                    }
                }
            }

            AccountContentType.ARTISTS -> {
                items(
                    items = artists.orEmpty().distinctBy { it.id },
                    key = { it.id }
                ) { item ->
                    YouTubeGridItem(
                        item = item,
                        fillMaxWidth = true,
                        modifier = Modifier
                            .combinedBounceClick(
                                onClick = {
                                    navController.navigate(artistRoute(item.id, item.thumbnail))
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        YouTubeArtistMenu(
                                            artist = item,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            )
                    )
                }

                if (artists == null) {
                    items(8) {
                        ShimmerHost {
                            GridItemPlaceHolder(fillMaxWidth = true)
                        }
                    }
                }
            }
        }
    }
}
}
