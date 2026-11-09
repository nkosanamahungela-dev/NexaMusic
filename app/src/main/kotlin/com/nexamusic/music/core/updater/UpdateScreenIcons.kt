package com.nexamusic.music.core.updater

import com.nexamusic.music.R

/**
 * Official Google Material Symbols Rounded Android VectorDrawable assets.
 * Note tiles use weight 500 / fill 1; the download action uses weight 500 / fill 0.
 * Each icon can be replaced in this one mapping table.
 */
internal object UpdateScreenIcons {
    private val releaseNotes = listOf(
        "lyrics" to R.drawable.update_ms_format_quote,
        "duets" to R.drawable.update_ms_group,
        "interludes" to R.drawable.update_ms_graphic_eq,
        "new-look" to R.drawable.update_ms_palette,
        "playlist-sync" to R.drawable.update_ms_sync,
        "fixes" to R.drawable.update_ms_verified
    )

    val update: Int = R.drawable.update_ms_download
    val install: Int = R.drawable.update_ms_check_circle

    fun noteIcon(index: Int): Int =
        releaseNotes[index.coerceIn(0, releaseNotes.lastIndex)].second
}