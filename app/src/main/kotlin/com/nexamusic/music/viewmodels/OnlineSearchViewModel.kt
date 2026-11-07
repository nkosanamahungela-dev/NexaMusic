/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nexamusic.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.SearchFilter
import com.music.spotify.SpotApi
import com.music.spotify.SpotApiTrack
import com.nexamusic.music.BuildConfig
import com.music.innertube.YouTube
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.SearchSummaryPage
import com.nexamusic.music.constants.HideExplicitKey
import com.nexamusic.music.constants.HideVideoSongsKey
import com.nexamusic.music.constants.DataSaverEnabledKey
import com.nexamusic.music.constants.HideYoutubeShortsKey
import com.nexamusic.music.models.ItemsPage
import com.nexamusic.music.utils.dataStore
import com.nexamusic.music.utils.get
import com.nexamusic.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import com.nexamusic.music.utils.PageCaches
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val query = try {
        URLDecoder.decode(requireNotNull(savedStateHandle.get<String>("query")) { "Missing query" }, "UTF-8")
    } catch (e: IllegalArgumentException) {
        requireNotNull(savedStateHandle.get<String>("query")) { "Missing query" }
    }
    val filter = MutableStateFlow<SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

    var spotifyTracks by mutableStateOf<List<SpotApiTrack>>(emptyList())
        private set
    var isSpotifyLoading by mutableStateOf(false)
        private set
    var spotifyError by mutableStateOf<String?>(null)
        private set
    private var spotifySearchJob: Job? = null

    init {
        SpotApi.configure(BuildConfig.SPOTAPI_BASE_URL)
        viewModelScope.launch {
            // Only fetches what isn't cached yet — switching filters back and forth
            // must not re-hit the network. refresh() drops the cache entry first.
            filter.collect { load(it) }
        }
    }


    /** Search the public Spotify catalogue through NexaMusic's hosted gateway. */
    fun searchSpotifyTracks(searchQuery: String) {
        spotifySearchJob?.cancel()
        val normalizedQuery = searchQuery.trim()
        if (normalizedQuery.length < 2 || !SpotApi.isConfigured()) {
            spotifyTracks = emptyList()
            spotifyError = null
            isSpotifyLoading = false
            return
        }

        spotifySearchJob = viewModelScope.launch(Dispatchers.IO) {
            isSpotifyLoading = true
            spotifyError = null
            val result = SpotApi.searchTracks(normalizedQuery, limit = 8)
            result.onSuccess { tracks ->
                spotifyTracks = tracks
                isSpotifyLoading = false
            }.onFailure { error ->
                if (error is CancellationException) throw error
                spotifyTracks = emptyList()
                spotifyError = "Spotify catalogue is temporarily unavailable."
                isSpotifyLoading = false
                reportException(error)
            }
        }
    }

    private suspend fun load(filter: SearchFilter?) {
        if (filter == null) {
            if (summaryPage != null) return
            PageCaches.searchSummaries
                .get(query) { YouTube.searchSummary(query).onFailure { reportException(it) }.getOrNull() }
                ?.let {
                    com.nexamusic.music.utils.AppleArt.prefetchArtistIds(it.summaries.flatMap { summary -> summary.items })
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false) || context.dataStore.get(DataSaverEnabledKey, false)
                    val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                    summaryPage =
                        it.filterExplicit(
                            hideExplicit,
                        ).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts)
                }
        } else {
            if (viewStateMap[filter.value] != null) return
            PageCaches.searchResults
                .get("$query|${filter.value}") { YouTube.search(query, filter).onFailure { reportException(it) }.getOrNull() }
                ?.let { result ->
                    com.nexamusic.music.utils.AppleArt.prefetchArtistIds(result.items)
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false) || context.dataStore.get(DataSaverEnabledKey, false)
                    val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                    viewStateMap[filter.value] =
                        ItemsPage(
                            result.items
                                .distinctBy { it.id }
                                .filterExplicit(
                                    hideExplicit,
                                )
                                .filterVideoSongs(hideVideoSongs)
                                .filterYoutubeShorts(hideYoutubeShorts),
                            result.continuation,
                        )
                }
        }
    }

    /**
     * Re-runs the current query. Bound to the pull-to-refresh gesture.
     *
     * Clears this filter's cached page first, otherwise [load] short-circuits on
     * it — and calls [load] directly rather than re-setting [filter], since a
     * StateFlow drops a write of the value it already holds.
     */
    fun refresh() {
        val current = filter.value
        if (current == null) {
            summaryPage = null
            PageCaches.searchSummaries.remove(query)
        } else {
            viewStateMap.remove(current.value)
            PageCaches.searchResults.remove("$query|${current.value}")
        }
        viewModelScope.launch { load(current) }
    }

    fun loadMore() {
        val filter = filter.value?.value
        viewModelScope.launch {
            if (filter == null) return@launch
            val viewState = viewStateMap[filter] ?: return@launch
            val continuation = viewState.continuation
            if (continuation != null) {
                val searchResult =
                    YouTube.searchContinuation(continuation).getOrNull() ?: return@launch
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false) || context.dataStore.get(DataSaverEnabledKey, false)
                val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                val newItems = searchResult.items
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs)
                    .filterYoutubeShorts(hideYoutubeShorts)
                viewStateMap[filter] = ItemsPage(
                    (viewState.items + newItems).distinctBy { it.id },
                    searchResult.continuation
                )
            }
        }
    }
}
