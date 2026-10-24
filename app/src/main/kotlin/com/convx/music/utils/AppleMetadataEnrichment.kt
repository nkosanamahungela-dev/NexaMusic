/**
 * NexaMusic Project (C) 2026
 * Apple Music supplies descriptive catalog metadata; YouTube IDs remain playback IDs.
 */
package com.convx.music.utils

import com.convx.music.canvas.AppleMusicCatalog
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.CancellationException

/**
 * Enrich existing YouTube search rows with matching Apple Music catalog metadata.
 * If Apple is unavailable or there is no confident title/artist match, the original row is kept.
 */
internal suspend fun List<YTItem>.withAppleMusicMetadata(query: String): List<YTItem> {
    if (isEmpty() || query.isBlank() || none { it is SongItem }) return this
    val appleSongs = try {
        AppleMusicCatalog.searchSongs(query)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        return this
    }
    if (appleSongs.isEmpty()) return this

    return map { item ->
        if (item !is SongItem) return@map item
        val youtubeArtist = item.artists.firstOrNull()?.name
        val matches = appleSongs.filter { apple ->
            AppleMusicCatalog.normalize(apple.title) == AppleMusicCatalog.normalize(item.title) &&
                AppleMusicCatalog.artistMatches(youtubeArtist, apple.artistName)
        }
        val apple = matches.minByOrNull { candidate ->
            val duration = item.duration
            if (duration == null || candidate.durationMillis == null) Long.MAX_VALUE / 2
            else kotlin.math.abs(candidate.durationMillis - duration.toLong() * 1000L)
        } ?: return@map item

        item.copy(
            title = apple.title,
            artists = if (item.artists.size == 1) {
                listOf(item.artists.first().copy(name = apple.artistName))
            } else {
                item.artists
            },
            album = item.album?.let { album ->
                album.copy(name = apple.albumName.takeIf { it.isNotBlank() } ?: album.name)
            },
            duration = apple.durationMillis
                ?.div(1000L)
                ?.takeIf { it > 0L && it <= Int.MAX_VALUE }
                ?.toInt()
                ?: item.duration,
            thumbnail = apple.artworkUrl ?: item.thumbnail,
        )
    }
}
