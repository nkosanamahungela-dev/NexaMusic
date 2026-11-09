package com.nexamusic.music.core.updater

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Update-screen-only design tokens. The page palette is selected from the
 * active app theme at runtime; do not use system theme as a substitute.
 */
internal object UpdateScreenTokens {
    data class Palette(
        val page: Color,
        val card: Color,
        val cardBorder: Color,
        val divider: Color,
        val primaryText: Color,
        val secondaryText: Color,
        val sectionLabel: Color,
        val glass: Color,
        val glassBorder: Color,
    )

    val Dark = Palette(
        page = Color(0xFF000000),
        card = Color(0xFF1C1C1E),
        cardBorder = Color.White.copy(alpha = 0.08f),
        divider = Color.White.copy(alpha = 0.10f),
        primaryText = Color.White,
        secondaryText = Color(0xFF98989D),
        sectionLabel = Color(0xFF8E8E93),
        glass = Color(0xFF2C2C2E).copy(alpha = 0.72f),
        glassBorder = Color.White.copy(alpha = 0.14f),
    )

    val Light = Palette(
        page = Color.White,
        card = Color(0xFFF2F2F7),
        cardBorder = Color.Black.copy(alpha = 0.06f),
        divider = Color.Black.copy(alpha = 0.10f),
        primaryText = Color.Black,
        secondaryText = Color(0xFF6E6E73),
        sectionLabel = Color(0xFF6E6E73),
        glass = Color.White.copy(alpha = 0.78f),
        glassBorder = Color.Black.copy(alpha = 0.10f),
    )

    val Hero = Color(0xFFC9E4C8)
    val HeroInk = Color(0xFF17120E)
    val Action = Color(0xFFE5173F)
    val TileLyrics = Color(0xFFFA243C)
    val TileDuets = Color(0xFF0A9FD8)
    val TileInterludes = Color(0xFFE66F00) // darkened slightly to keep white glyph contrast >= 3:1
    val TileLook = Color(0xFFAF52DE)
    val TileSync = Color(0xFF0A84FF)
    val TileFix = Color(0xFF1F9D4A)

    val SideMargin = 20.dp
    val HeroMinHeight = 252.dp
    val HeroCorner = 32.dp
    val NoteCardCorner = 20.dp
    val NoteTileSize = 36.dp
    val NoteTileCorner = 10.dp
    val ButtonHeight = 56.dp
    val ButtonGap = 12.dp
    val NoteIconSize = 20.dp
    val ButtonIconSize = 22.dp

    val DisplayFont: FontFamily = FontFamily.SansSerif // Replace with bundled Bricolage Grotesque ExtraBold.
    val BodyFont: FontFamily = FontFamily.SansSerif // Replace with bundled DM Sans; system fallback remains available.
    val VersionSize = 66.sp
    val BodySize = 14.sp
    val SummarySize = 15.sp
    val ButtonSize = 17.sp
    val SectionSize = 12.sp

    const val ProgressDurationMs = 200
    const val PressDurationMs = 120
    const val OpenDurationMs = 250
    const val ReadyPopDurationMs = 220
}