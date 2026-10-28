package com.convx.music.utils

/**
 * Builds shareable YouTube URLs for media IDs used by NexaMusic.
 * Keep links provider-neutral rather than pointing to Convx's branded domain.
 */
object ShareUtils {
    fun songUrl(songId: String): String = "https://www.youtube.com/watch?v=$songId"

    fun playlistUrl(playlistId: String): String = "https://www.youtube.com/playlist?list=$playlistId"

    fun albumUrl(playlistId: String): String = playlistUrl(playlistId)

    fun artistUrl(artistId: String): String = "https://www.youtube.com/channel/$artistId"
}
