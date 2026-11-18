/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nexamusic.music.ui.screens.library

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.nexamusic.music.constants.ChipSortTypeKey
import com.nexamusic.music.constants.LibraryFilter
import com.nexamusic.music.ui.screens.library.apple.AppleAlbumsPage
import com.nexamusic.music.ui.screens.library.apple.AppleArtistsPage
import com.nexamusic.music.ui.screens.library.apple.AppleLibraryDestination
import com.nexamusic.music.ui.screens.library.apple.AppleLibraryScreen
import com.nexamusic.music.ui.screens.library.apple.ApplePlaylistsPage
import com.nexamusic.music.ui.screens.library.apple.AppleSongsPage
import com.nexamusic.music.ui.screens.library.apple.AppleLocalSongsPage
import com.nexamusic.music.utils.rememberEnumPreference
import com.nexamusic.music.ui.component.smoothHeaderScrollProgress
import com.nexamusic.music.ui.component.rememberSmoothHeaderProgress
import com.nexamusic.music.ui.component.SmoothHeaderProgressSync
import com.nexamusic.music.ui.component.ChipsRow
import com.nexamusic.music.ui.component.PinnedScrollHeaderRow
import com.nexamusic.music.R
import androidx.compose.ui.res.stringResource

@Composable
fun LibraryScreen(navController: NavController) {
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)

    val back = { filterType = LibraryFilter.LIBRARY }
    val libraryListState = rememberLazyListState()
    val headerScreenKey = navController.currentBackStackEntry?.id ?: "library"
    val libraryHeaderProgress = rememberSmoothHeaderProgress(libraryListState)
    if (filterType == LibraryFilter.LIBRARY) SmoothHeaderProgressSync(libraryHeaderProgress, headerScreenKey)
    val libraryPinnedScrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize().then(if (filterType == LibraryFilter.LIBRARY) Modifier else Modifier.smoothHeaderScrollProgress(headerScreenKey))) {
        PinnedScrollHeaderRow(
            screenKey = headerScreenKey,
            visible = filterType == LibraryFilter.LIBRARY && libraryHeaderProgress() >= 0.85f,
        ) {
            ChipsRow(
                chips = listOf(
                    LibraryFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
                    LibraryFilter.ARTISTS to stringResource(R.string.filter_artists),
                    LibraryFilter.ALBUMS to stringResource(R.string.filter_albums),
                    LibraryFilter.SONGS to stringResource(R.string.filter_songs),
                    LibraryFilter.LOCAL to stringResource(R.string.filter_local),
                ),
                currentValue = filterType,
                onValueUpdate = { filterType = it },
                scrollState = libraryPinnedScrollState,
            )
        }
        // Apple Music's Library and its lists: new screens on the plain system background.
        when (filterType) {
            LibraryFilter.LIBRARY -> AppleLibraryScreen(
                navController = navController,
                listState = libraryListState,
                onOpen = { destination ->
                    when (destination) {
                        AppleLibraryDestination.Playlists -> filterType = LibraryFilter.PLAYLISTS
                        AppleLibraryDestination.Artists -> filterType = LibraryFilter.ARTISTS
                        AppleLibraryDestination.Albums -> filterType = LibraryFilter.ALBUMS
                        AppleLibraryDestination.Songs -> filterType = LibraryFilter.SONGS
                        AppleLibraryDestination.Local -> filterType = LibraryFilter.LOCAL
                        AppleLibraryDestination.Downloaded -> navController.navigate("auto_playlist/downloaded")
                        AppleLibraryDestination.Liked -> navController.navigate("auto_playlist/liked")
                        AppleLibraryDestination.Podcasts -> navController.navigate("auto_playlist/podcasts")
                        AppleLibraryDestination.Cached -> navController.navigate("cache_playlist/cached")
                        AppleLibraryDestination.Top -> navController.navigate("top_playlist/50")
                        AppleLibraryDestination.Uploaded -> navController.navigate("auto_playlist/uploaded")
                        AppleLibraryDestination.History -> navController.navigate("history")
                    }
                },
            )
            LibraryFilter.PLAYLISTS -> ApplePlaylistsPage(navController, back)
            LibraryFilter.SONGS -> AppleSongsPage(navController, back)
            LibraryFilter.ALBUMS -> AppleAlbumsPage(navController, back)
            LibraryFilter.ARTISTS -> AppleArtistsPage(navController, back)
            LibraryFilter.LOCAL -> AppleLocalSongsPage(navController, back)
        }
    }
}
