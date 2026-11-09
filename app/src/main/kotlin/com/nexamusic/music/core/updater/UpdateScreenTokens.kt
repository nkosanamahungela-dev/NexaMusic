package com.nexamusic.music.core.updater

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexamusic.music.R

/** Update-screen-only palette, typography fallbacks, sizes and motion timings. */
internal object UpdateScreenTokens {
    data class Palette(
        val page: Color,
        val noteCard: Color,
        val noteBorder: Color,
        val divider: Color,
        val text: Color,
        val secondaryText: Color,
        val sectionLabel: Color,
        val glass: Color,
        val glassBorder: Color
    )

    fun palette(isDark: Boolean) = if (isDark) {
        Palette(
            page = Color(0xFF000000),
            noteCard = Color(0xFF1C1C1E),
            noteBorder = Color(0x14FFFFFF),
            divider = Color(0x1AFFFFFF),
            text = Color(0xFFFFFFFF),
            secondaryText = Color(0xFF98989D),
            sectionLabel = Color(0xFF8E8E93),
            glass = Color(0xB82C2C2E),
            glassBorder = Color(0x24FFFFFF)
        )
    } else {
        Palette(
            page = Color(0xFFFFFFFF),
            noteCard = Color(0xFFF2F2F7),
            noteBorder = Color(0x0F000000),
            divider = Color(0x1A000000),
            text = Color(0xFF000000),
            secondaryText = Color(0xFF6E6E73),
            sectionLabel = Color(0xFF6E6E73),
            glass = Color(0xC7FFFFFF),
            glassBorder = Color(0x1A000000)
        )
    }

    val hero = Color(0xFFC9E4C8)
    val heroInk = Color(0xFF17120E)
    val actionRed = Color(0xFFE5173F)

    val noteTileColors = listOf(
        Color(0xFFFA243C), // lyrics
        Color(0xFF0A9FD8), // duets
        Color(0xFFE96F00), // interludes; darkened slightly so white icon contrast exceeds 3:1
        Color(0xFFAF52DE), // new look
        Color(0xFF0A84FF), // playlist sync
        Color(0xFF1F9D4A)  // fixes
    )

    // The repository currently has no Bricolage Grotesque or DM Sans font binaries.
    // These bundled fonts are preloaded fallbacks. Replace the resource IDs below
    // when those two font files are added to res/font.
    val displayFont = FontFamily(Font(R.font.outfit, weight = FontWeight.ExtraBold))
    val bodyFont = FontFamily(Font(R.font.google_sans_flex))

    val pageSideMargin = 20.dp
    val heroMinHeight = 252.dp
    val heroCorner = 32.dp
    val heroHorizontalPadding = 22.dp
    val logoSize = 92.dp
    val versionMaxFontSize = 66.sp
    val noteCardCorner = 20.dp
    val noteTileSize = 36.dp
    val noteTileCorner = 10.dp
    val noteIconSize = 20.dp
    val actionHeight = 56.dp
    val actionGap = 12.dp
    val actionIconSize = 22.dp
    val bottomEdgeSpacing = 30.dp
    val bottomFadeHeight = 38.dp
    val heroProgressHeight = 10.dp
    val bodyFontSize = 14.sp
    val summaryFontSize = 15.sp
    val buttonFontSize = 17.sp
    const val entryDurationMs = 250
    const val progressDurationMs = 200
    const val readyPopDurationMs = 220
}