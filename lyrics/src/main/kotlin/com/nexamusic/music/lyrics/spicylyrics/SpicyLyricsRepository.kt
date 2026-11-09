/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors.
 *
 * Credential-free integration boundary for Spicy Lyrics. The gateway must be
 * backed by an authorized service that keeps secret credentials server-side.
 */
package com.nexamusic.music.lyrics.spicylyrics

import kotlinx.coroutines.CancellationException

data class SpicyTrackQuery(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long,
) {
    val durationSeconds: Double get() = durationMs.coerceAtLeast(0L) / 1_000.0
}

/**
 * Implement this at the trusted backend boundary.
 *
 * Implementations should search using title + artist + duration, accept only
 * a sufficiently reliable exact track match, then request that track's lyrics.
 * Do not put a secret API key in this Android module or ship it in the APK.
 */
fun interface SpicyLyricsGateway {
    suspend fun fetchLyrics(query: SpicyTrackQuery): String?
}

/** Parsed word-timed lyrics plus the source credits required by the API. */
data class SpicyLyricsResult(
    val document: SpicyLyricsDocument,
    val richSyncLyrics: String,
)

/**
 * Coordinates lookup and parsing without owning credentials or networking.
 * A missing gateway response or a non-syllable response is a normal fallback
 * condition; callers should continue to their existing lyrics providers.
 */
class SpicyLyricsRepository(
    private val gateway: SpicyLyricsGateway,
) {
    suspend fun fetch(query: SpicyTrackQuery): Result<SpicyLyricsResult> = try {
        require(query.title.isNotBlank()) { "Track title is required" }
        require(query.artist.isNotBlank()) { "Track artist is required" }
        require(query.durationMs > 0L) { "Track duration must be positive" }

        val response = gateway.fetchLyrics(query)
            ?: return Result.failure(NoSuchElementException("No Spicy Lyrics response"))
        val document = SpicyLyricsResponseParser.parseDocument(response)
            ?: return Result.failure(IllegalStateException("Invalid Spicy Lyrics response"))
        if (document.lines.isEmpty()) {
            return Result.failure(NoSuchElementException("No word-timed lyrics available"))
        }

        val richSync = document.toNexaMusicRichSyncLyrics()
        if (richSync.isBlank()) {
            Result.failure(NoSuchElementException("No usable word-timed lyrics"))
        } else {
            Result.success(SpicyLyricsResult(document, richSync))
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
