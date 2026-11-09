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
 * Single icon registry for the update screen.
 * Change any icon in this file without touching layout code.
 */
internal object UpdateScreenIcons {
    val lyrics: ImageVector = Icons.Rounded.FormatQuote
    val duets: ImageVector = Icons.Rounded.Group
    val interludes: ImageVector = Icons.Rounded.GraphicEq
    val newLook: ImageVector = Icons.Rounded.Palette
    val playlistSync: ImageVector = Icons.Rounded.Sync
    val fix: ImageVector = Icons.Rounded.Verified
    val update: ImageVector = Icons.Rounded.Download
    val install: ImageVector = Icons.Rounded.CheckCircle
    val ready: ImageVector = Icons.Rounded.CheckCircle

    val noteIcons: List<ImageVector> = listOf(
        lyrics, duets, interludes, newLook, playlistSync, fix
    )
}