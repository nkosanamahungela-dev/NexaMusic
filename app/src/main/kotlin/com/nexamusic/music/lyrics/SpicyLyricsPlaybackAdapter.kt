/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors.
 *
 * Bridges the independent Spicy Lyrics parser to NexaMusic's existing
 * LyricsEntry model. The Apple Music V3 renderer remains the sole renderer.
 */
package com.nexamusic.music.lyrics

import com.nexamusic.music.lyrics.spicylyrics.SpicyLyricsAttribution
import com.nexamusic.music.lyrics.spicylyrics.SpicyLyricsResult

/**
 * Keep credits alongside the parsed entries so the lyrics UI can display
 * the correct provider/community attribution instead of dropping it.
 */
data class SpicyLyricsPlaybackPayload(
    val entries: List<LyricsEntry>,
    val attribution: SpicyLyricsAttribution,
)

fun SpicyLyricsResult.toPlaybackPayload(): SpicyLyricsPlaybackPayload {
    val entries = LyricsUtils.parseLyrics(richSyncLyrics)
    require(entries.isNotEmpty()) { "Spicy Lyrics did not produce usable playback entries" }
    require(entries.any { !it.words.isNullOrEmpty() }) {
        "Spicy Lyrics response did not contain word-level timing"
    }
    return SpicyLyricsPlaybackPayload(
        entries = entries,
        attribution = document.attribution,
    )
}
