package com.convx.music.ui.screens.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Search-only design tokens. All neutral surfaces follow the app-resolved IosTheme.
 * Category colours are intentionally flat, bright Apple Music-style swatches.
 */
internal object SearchDesignTokens {
    data class Theme(
        val page: Color,
        val surface: Color,
        val border: Color,
        val divider: Color,
        val text: Color,
        val secondary: Color,
        val sectionLabel: Color,
        val chevron: Color,
        val accent: Color,
        val segmentedTrack: Color,
        val selectedScope: Color,
        val selectedScopeText: Color,
    )

    val dark = Theme(
        page = Color(0xFF000000),
        surface = Color(0xFF1C1C1E),
        border = Color(0x14FFFFFF),
        divider = Color(0x1AFFFFFF),
        text = Color(0xFFFFFFFF),
        secondary = Color(0xFF98989D),
        sectionLabel = Color(0xFF8E8E93),
        chevron = Color(0xFF636366),
        accent = Color(0xFFFF375F),
        segmentedTrack = Color(0x47767680),
        selectedScope = Color(0xFF636366),
        selectedScopeText = Color(0xFFFFFFFF),
    )

    val light = Theme(
        page = Color(0xFFFFFFFF),
        surface = Color(0xFFF2F2F7),
        border = Color(0x0F000000),
        divider = Color(0x1A000000),
        text = Color(0xFF000000),
        secondary = Color(0xFF6E6E73),
        sectionLabel = Color(0xFF6E6E73),
        chevron = Color(0xFFAEAEB2),
        accent = Color(0xFFFA243C),
        segmentedTrack = Color(0x24767680),
        selectedScope = Color(0xFFFFFFFF),
        selectedScopeText = Color(0xFF000000),
    )

    data class TileColor(val name: String, val fill: Color, val content: Color)

    // Fixed order is shared by Search and both appearance modes.
    val tilePalette = listOf(
        TileColor("Blue", Color(0xFF0A84FF), Color.White),
        TileColor("Red", Color(0xFFFA243C), Color.White),
        TileColor("Orange", Color(0xFFFF9F0A), Color(0xFF1A1100)),
        TileColor("Graphite", Color(0xFF636366), Color.White),
        TileColor("Green", Color(0xFF30D158), Color(0xFF06240F)),
        TileColor("Pink", Color(0xFFFF2D55), Color.White),
        TileColor("Mint", Color(0xFF00C7BE), Color(0xFF04302E)),
        TileColor("Yellow", Color(0xFFFFD60A), Color(0xFF2A2200)),
        TileColor("Coral", Color(0xFFFF6482), Color(0xFF2A0610)),
        TileColor("Sky", Color(0xFF5AC8FA), Color(0xFF052A3A)),
    )

    private val namedColors = mapOf(
        "sleep" to 0,
        "arabic hip-hop" to 1,
        "arabic hip hop" to 1,
        "commute" to 2,
        "metal" to 3,
        "christmas" to 4,
        "hip-hop en español" to 5,
        "hip hop en español" to 5,
        "chill" to 6,
        "energize" to 7,
        "energise" to 7,
        "feel-good" to 8,
        "feel good" to 8,
        "focus" to 9,
    )

    /**
     * Named categories keep their requested colour. Other categories use their stable
     * position in YouTube Music's ordered category feed; adjacent positions never collide.
     */
    fun colorFor(name: String, position: Int, previous: TileColor? = null): TileColor {
        val key = name.trim().lowercase()
        val fixed = namedColors[key]
        val start = fixed ?: Math.floorMod(position, tilePalette.size)
        var candidate = tilePalette[start]
        if (candidate == previous) candidate = tilePalette[(start + 1) % tilePalette.size]
        return candidate
    }

    val pageSide = 20.dp
    val sectionGap = 12.dp
    val tileCorner = 12.dp
    val featureCorner = 12.dp
    val titleSize = 40.sp
    val sectionTitleSize = 25.sp
    val tileLabelSize = 20.sp
    val bodyTextSize = 17.sp
    val playBadgeFill = Color.White.copy(alpha = 0.25f)
    val playBadgeContent = Color.White
    val pressScale = 0.97f
    const val pressDurationMs = 120
    const val browseFadeMs = 150
    const val coverFadeMs = 200
    const val firstTileStaggerMs = 40
    const val firstTileStaggerCount = 8
}
