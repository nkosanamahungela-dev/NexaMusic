/**
 * NexaMusic Settings visual tokens and Android icon map.
 * Material Symbols Rounded are represented by the app's bundled vector resources.
 */
package com.convx.music.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.convx.music.R
import com.convx.music.ui.ios.foundation.IosTheme

object SettingsDesign {
    // Material Symbols Rounded names: weight 500, optical size 24, fill 1.
    private val symbolNames = mapOf(
        "Sign in" to "account_circle",
        "Local-only mode" to "music_note",
        "Listen Together" to "group",
        "Player & Lyrics" to "play_circle",
        "Playback & Audio" to "graphic_eq",
        "Appearance" to "palette",
        "Library & Content" to "library_music",
        "Accounts & Services" to "manage_accounts",
        "Privacy & Storage" to "shield",
        "History" to "history",
        "Stats" to "bar_chart",
        "Software Update" to "system_update",
        "Help & About" to "info",
        "Workout" to "fitness_center",
        "Focus" to "center_focus_strong",
        "Sleep" to "bedtime",
        "Commute" to "directions_car",
        "Party" to "celebration",
        "Chill" to "local_cafe",
        "Energize" to "bolt",
        "Feel-good" to "sunny",
        "Romance" to "favorite",
        "Sad" to "rainy",
        "Christmas" to "ac_unit",
        "Metal" to "local_fire_department",
        "Hip-hop" to "mic",
        "Controls" to "chevron_right",
        "Back" to "chevron_left",
        "Close" to "close",
        "Search" to "search",
        "Recent search" to "schedule",
        "Clear" to "cancel",
        "Check" to "check",
        "Play" to "play_arrow",
    )

    fun materialSymbolName(title: String): String? = symbolNames[title]

    @DrawableRes
    fun iconFor(title: String, fallback: Int): Int = when (materialSymbolName(title)) {
        "account_circle", "manage_accounts" -> R.drawable.account
        "music_note", "library_music" -> R.drawable.music_note
        "group" -> R.drawable.group
        "play_circle" -> R.drawable.play
        "graphic_eq" -> R.drawable.equalizer
        "palette" -> R.drawable.palette
        "shield" -> R.drawable.security
        "history" -> R.drawable.music_history
        "bar_chart" -> R.drawable.stats
        "system_update" -> R.drawable.network_update
        "info" -> R.drawable.info
        else -> fallback
    }

    private val listenTogether = Color(0xFF0A9FD8)
    private val playerLyrics = Color(0xFFFA243C)
    private val playbackAudio = Color(0xFFF2542D)
    private val appearance = Color(0xFFAF52DE)
    private val libraryContent = Color(0xFFF57C00)
    private val accountsServices = Color(0xFF1F9D4A)
    private val privacyStorage = Color(0xFF0A84FF)
    private val history = Color(0xFF0E9AA7)
    private val stats = Color(0xFFFFD60A)
    private val neutral = Color(0xFF8E8E93)

    val appearanceTile = appearance
    val appearanceIcon = Color.White
    val localOnlyTile = libraryContent
    val localOnlyIcon = Color.White

    fun tileColor(title: String): Color = when (title) {
        "Listen Together" -> listenTogether
        "Player & Lyrics" -> playerLyrics
        "Playback & Audio" -> playbackAudio
        "Appearance" -> appearance
        "Library & Content" -> libraryContent
        "Accounts & Services" -> accountsServices
        "Privacy & Storage" -> privacyStorage
        "History" -> history
        "Stats" -> stats
        "Software Update", "Help & About" -> neutral
        "Local-only mode" -> libraryContent
        else -> neutral
    }

    fun iconTint(title: String): Color =
        if (title == "Stats") Color(0xFF1A1A1A) else Color.White

    val previewFallback = Color(0xFF1C1C1E)

    @Composable
    fun cardBorder(): Color =
        if (IosTheme.colors.isDark) Color(0x14FFFFFF) else Color(0x0F000000)
}
