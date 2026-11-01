/**
 * NexaMusic Settings visual tokens and Android icon map.
 *
 * Material Symbols Rounded are represented by the app's existing bundled vector resources;
 * this keeps the APK font-free and avoids shipping the complete Material Symbols font.
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
    )

    /** Kept in one place so a row's icon can be changed with a single mapping edit. */
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

    private val mint = Color(0xFFC8E3C9)
    private val mintInk = Color(0xFF12382A)
    private val terracotta = Color(0xFFC23A1D)
    private val terracottaInk = Color(0xFFFFF3E2)
    private val saffron = Color(0xFFF2A33A)
    private val saffronInk = Color(0xFF1A120C)
    private val forest = Color(0xFF1F6B4E)
    private val forestInk = Color(0xFFE8F3E6)
    private val cream = Color(0xFFE8DFCB)
    private val creamInk = Color(0xFF1A120C)
    val appearanceTile = terracotta
    val appearanceIcon = terracottaInk

    private fun group(title: String): Int = when (title) {
        "Listen Together" -> 0
        "Player & Lyrics", "Playback & Audio", "Appearance" -> 1
        "Library & Content", "Accounts & Services", "Local-only mode" -> 2
        "Privacy & Storage", "History", "Stats" -> 3
        "Software Update", "Help & About" -> 4
        else -> 1
    }

    fun tileColor(title: String): Color = when (group(title)) {
        0 -> mint
        1 -> terracotta
        2 -> saffron
        3 -> forest
        else -> cream
    }

    fun iconTint(title: String): Color = when (group(title)) {
        0 -> mintInk
        1 -> terracottaInk
        2 -> saffronInk
        3 -> forestInk
        else -> creamInk
    }

    val previewFallback = Color(0xFF1B1713)

    @Composable
    fun cardBorder(): Color =
        if (IosTheme.colors.isDark) Color(0x14F4EEDD) else Color(0x1A1A120C)
}
