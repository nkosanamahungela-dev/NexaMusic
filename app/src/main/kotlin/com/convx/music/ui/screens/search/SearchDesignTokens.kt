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

    val dark = Theme(
        page = Color(0xFF0F0D0B), surface = Color(0xFF1B1713),
        border = Color(0x14F4EEDD), text = Color(0xFFF4EEDD),
        secondary = Color(0xFFA8A29A), accent = Color(0xFFFF7A5C),
        selectedScope = Color(0xFFF4EEDD), selectedScopeText = Color(0xFF1A120C),
    )
    val light = Theme(
        page = Color(0xFFFFFFFF), surface = Color(0xFFF2F0EB),
        border = Color(0x1A1A120C), text = Color(0xFF1A120C),
        secondary = Color(0xFF5C5247), accent = Color(0xFFB3331A),
        selectedScope = Color(0xFF1A120C), selectedScopeText = Color(0xFFF4EEDD),
    )

    data class TileColor(val name: String, val fill: Color, val content: Color)
    val tilePalette = listOf(
        TileColor("Vermilion", Color(0xFFC93A28), Color.White),
        TileColor("Brick", Color(0xFFA5432A), Color.White),
        TileColor("Cocoa", Color(0xFF4A3528), Color.White),
        TileColor("Orange", Color(0xFFE0752B), Color(0xFF1A120C)),
        TileColor("Saffron", Color(0xFFF2A33A), Color(0xFF1A120C)),
        TileColor("Mustard", Color(0xFFE8C547), Color(0xFF1A120C)),
        TileColor("Leaf", Color(0xFF8DB84A), Color(0xFF1A120C)),
        TileColor("Emerald", Color(0xFF1F7A55), Color.White),
        TileColor("Teal", Color(0xFF1F7F86), Color.White),
        TileColor("Cobalt", Color(0xFF2F6FD0), Color.White),
        TileColor("Plum", Color(0xFF8A3F7A), Color.White),
        TileColor("Rose", Color(0xFFD45A86), Color(0xFF1A120C)),
        TileColor("Slate", Color(0xFF4F5D66), Color.White),
        TileColor("Mint", Color(0xFFC8E3C9), Color(0xFF12382A)),
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
    val playBadgeContent = Color(0xFF1A120C)
    val pressScale = 0.97f
    const val pressDurationMs = 120
    const val browseFadeMs = 150
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
