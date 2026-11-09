package com.nexamusic.music.core.updater

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Android uses one Material Symbols Rounded-style family at medium weight.
 * Edit an icon in this file only; note order follows the release data order.
 */
internal object UpdateScreenIcons {
    private val releaseNotes: List<Pair<String, ImageVector>> = listOf(
        "lyrics" to Icons.Rounded.FormatQuote,  // format_quote
        "duets" to Icons.Rounded.Group,         // group
        "interludes" to Icons.Rounded.GraphicEq,// graphic_eq
        "new-look" to Icons.Rounded.Palette,    // palette
        "playlist-sync" to Icons.Rounded.Sync,  // sync
        "fixes" to Icons.Rounded.Verified       // verified
    )

    val update: ImageVector = Icons.Rounded.Download // download
    val install: ImageVector = Icons.Rounded.CheckCircle // check_circle

    fun noteIcon(index: Int): ImageVector =
        releaseNotes[index.coerceIn(0, releaseNotes.lastIndex)].second
}