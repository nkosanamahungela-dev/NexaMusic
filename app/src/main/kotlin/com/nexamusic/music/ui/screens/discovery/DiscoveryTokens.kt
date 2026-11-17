/**
 * Shared visual tokens for the Radio and Explore discovery screens.
 * Theme-aware surface/text values are read from IosTheme at render time so saved
 * Appearance changes are applied immediately; fixed accents and mood swatches live here.
 */
package com.nexamusic.music.ui.screens.discovery

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object DiscoveryTokens {
    val AccentRed = Color(0xFFFA243C)

    // Reference values for documentation and components that need a fixed neutral.
    object Dark {
        val Page = Color(0xFF000000)
        val Card = Color(0xFF1C1C1E)
        val CardBorder = Color(0x14FFFFFF)
        val Divider = Color(0x14FFFFFF)
        val Text = Color(0xFFFFFFFF)
        val SecondaryText = Color(0xFF98989D)
        val Glass = Color(0xB82C2C2E)
        val GlassBorder = Color(0x24FFFFFF)
    }

    object Light {
        val Page = Color(0xFFFFFFFF)
        val Card = Color(0xFFF2F2F7)
        val CardBorder = Color(0x0F000000)
        val Divider = Color(0x14000000)
        val Text = Color(0xFF000000)
        val SecondaryText = Color(0xFF6E6E73)
        val Glass = Color(0xC7FFFFFF)
        val GlassBorder = Color(0x1A000000)
    }

    data class MoodSwatch(val fill: Color, val foreground: Color)

    val MoodPalette = listOf(
        MoodSwatch(Color(0xFF30D158), Color(0xFF06240F)),
        MoodSwatch(Color(0xFF5AC8FA), Color(0xFF052A3A)),
        MoodSwatch(Color(0xFFFF9F0A), Color(0xFF1A1100)),
        MoodSwatch(Color(0xFFFF2D55), Color.White),
        MoodSwatch(Color(0xFF00C7BE), Color(0xFF04302E)),
        MoodSwatch(Color(0xFFFFD60A), Color(0xFF2A2200)),
        MoodSwatch(Color(0xFF0A84FF), Color.White),
        MoodSwatch(Color(0xFFFF6482), Color(0xFF2A0610)),
        MoodSwatch(Color(0xFF636366), Color.White),
    )

    // Stable by mood title; adjacent tiles are forced onto a different palette entry.
    fun moodSwatch(name: String, index: Int, previousName: String?): MoodSwatch {
        val size = MoodPalette.size
        var slot = (name.hashCode().toUInt().toLong() % size).toInt()
        if (previousName != null) {
            val previous = (previousName.hashCode().toUInt().toLong() % size).toInt()
            if (slot == previous) slot = (slot + 1) % size
        }
        // Keep the first index deterministic if a feed repeats identical names.
        if (index > 0 && previousName == name) slot = (slot + 1) % size
        return MoodPalette[slot]
    }

    object Size {
        val PageTitle = 40.sp
        val SectionTitle = 25.sp
        val HeroHeight = 178.dp
        val HeroCover = 134.dp
        val RadioCover = 165.dp
        val FeaturedCover = 300.dp
        val AlbumCover = 150.dp
        val MoodWidth = 222.dp
        val MoodHeight = 150.dp
    }

    object Motion {
        const val PressScale = 0.97f
        const val PressDurationMs = 120
        const val FirstItemsStaggerMs = 40
        const val FirstItemsStaggerCount = 8
        const val OnAirPulseHalfCycleMs = 700
    }
}
