package com.convx.music.ui.screens.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Search-only design tokens. Keep all Search screen colours and dimensions here. */
internal object SearchDesignTokens {
    data class Theme(
        val page: Color, val surface: Color, val border: Color,
        val text: Color, val secondary: Color, val accent: Color,
        val selectedScope: Color, val selectedScopeText: Color,
    )

    // Spotify-inspired true black canvas, graphite surfaces and a restrained green action accent.
    // Apple Music-inspired vivid artwork colours are reserved for discovery tiles.
    val dark = Theme(
        page = Color(0xFF000000), surface = Color(0xFF121212),
        border = Color(0x26FFFFFF), text = Color(0xFFFFFFFF),
        secondary = Color(0xFFB3B3B3), accent = Color(0xFF1ED760),
        selectedScope = Color(0xFF1ED760), selectedScopeText = Color(0xFF000000),
    )
    val light = Theme(
        page = Color(0xFFFFFFFF), surface = Color(0xFFF2F0EB),
        border = Color(0x1A1A120C), text = Color(0xFF1A120C),
        secondary = Color(0xFF5C5247), accent = Color(0xFFB3331A),
        selectedScope = Color(0xFF1A120C), selectedScopeText = Color(0xFFF4EEDD),
    )

    data class TileColor(val name: String, val fill: Color, val content: Color)
    val tilePalette = listOf(
        TileColor("Spotify Green", Color(0xFF167A43), Color.White),
        TileColor("Apple Red", Color(0xFFC62845), Color.White),
        TileColor("Electric Blue", Color(0xFF2457B8), Color.White),
        TileColor("Violet", Color(0xFF713A9B), Color.White),
        TileColor("Magenta", Color(0xFFAD245F), Color.White),
        TileColor("Deep Orange", Color(0xFFB94716), Color.White),
        TileColor("Teal", Color(0xFF087F8C), Color.White),
        TileColor("Indigo", Color(0xFF3949AB), Color.White),
        TileColor("Berry", Color(0xFF8E245B), Color.White),
        TileColor("Cyan", Color(0xFF087C9C), Color.White),
        TileColor("Lime", Color(0xFFB7D83D), Color(0xFF172000)),
        TileColor("Sunshine", Color(0xFFF2C94C), Color(0xFF211A00)),
        TileColor("Coral", Color(0xFFE85D4A), Color(0xFF240A06)),
        TileColor("Mint", Color(0xFFA8E6CF), Color(0xFF12382A)),
    )

    val pageSide = 20.dp
    val sectionGap = 12.dp
    val tileCorner = 22.dp
    val featureCorner = 26.dp
    val titleSize = 40.sp
    val sectionTitleSize = 22.sp
    val tileLabelSize = 17.sp
    val bodyTextSize = 16.sp
    val playBadgeFill = Color.White
    val playBadgeContent = Color(0xFF000000)
    val pressScale = 0.975f
    const val pressDurationMs = 160
    const val browseFadeMs = 180
    const val coverFadeMs = 220
    const val firstTileStaggerMs = 40
    const val firstTileStaggerCount = 8

    /** Stable across process launches; resolves adjacent collisions deterministically. */
    fun colorFor(name: String, position: Int, previous: TileColor? = null): TileColor {
        val start = (name.lowercase().hashCode().ushr(1) + position) % tilePalette.size
        for (step in tilePalette.indices) {
            val candidate = tilePalette[(start + step) % tilePalette.size]
            if (candidate != previous) return candidate
        }
        return tilePalette[start]
    }
}
