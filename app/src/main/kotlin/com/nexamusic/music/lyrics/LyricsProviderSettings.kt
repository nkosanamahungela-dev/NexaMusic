package com.nexamusic.music.lyrics

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.nexamusic.music.constants.EnableBetterLyricsKey
import com.nexamusic.music.constants.EnableKugouKey
import com.nexamusic.music.constants.EnableLrcLibKey
import com.nexamusic.music.constants.EnableMusixmatchKey
import com.nexamusic.music.constants.EnablePaxsenixKey
import com.nexamusic.music.constants.EnableSimpMusicKey
import com.nexamusic.music.constants.EnableUnisonKey
import com.nexamusic.music.constants.EnableYouLyPlusKey
import com.nexamusic.music.constants.PreferredLyricsProvider
import com.nexamusic.music.utils.dataStore
import com.nexamusic.music.utils.get

object LyricsProviderSettings {
    fun idForLegacyPreference(provider: PreferredLyricsProvider): String = when (provider) {
        PreferredLyricsProvider.LRCLIB -> "LrcLib"
        PreferredLyricsProvider.KUGOU -> "Kugou"
        PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
        PreferredLyricsProvider.SIMPMUSIC -> "SimpMusic"
        PreferredLyricsProvider.YOULYPLUS -> "YouLyPlus"
        PreferredLyricsProvider.PAXSENIX -> "Paxsenix"
    }

    fun enabledKey(id: String): Preferences.Key<Boolean> = when (id) {
        "BetterLyrics" -> EnableBetterLyricsKey
        "Kugou" -> EnableKugouKey
        "LrcLib" -> EnableLrcLibKey
        "Musixmatch" -> EnableMusixmatchKey
        "Paxsenix" -> EnablePaxsenixKey
        "SimpMusic" -> EnableSimpMusicKey
        "Unison" -> EnableUnisonKey
        "YouLyPlus" -> EnableYouLyPlusKey
        else -> booleanPreferencesKey("enableLyrics$id")
    }

    fun defaultEnabled(id: String): Boolean = true

    fun isEnabled(context: Context, provider: LyricsProvider): Boolean =
        context.dataStore[enabledKey(provider.id)] ?: defaultEnabled(provider.id)

    fun initialize(context: Context, provider: LyricsProvider) {
        provider.initialize(context.applicationContext)
    }
}
