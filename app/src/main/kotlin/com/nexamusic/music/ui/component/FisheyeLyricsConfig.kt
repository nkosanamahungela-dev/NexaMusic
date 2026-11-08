package com.nexamusic.music.ui.component

import androidx.compose.ui.graphics.Color

/**
 * Single tuning surface for the Fisheye lyrics renderer.
 * Values are deliberately stable so word styling never changes while scrolling.
 */
internal object FisheyeLyricsConfig {
    const val LENS_K = 0.55f
    const val SCALE_FALLOFF = 0.55f
    const val VERTICAL_COMPRESSION = 0.28f
    const val VERTICAL_CENTRE = 0.46f
    const val MAX_WORD_SCALE = 1.06f
    const val MIN_WORD_SCALE = 0.45f
    const val MAX_ROTATION_DEGREES = 16f
    const val SPRING_STIFFNESS = 170f
    const val SPRING_DAMPING_RATIO = 0.82f
    const val WORD_POP_SCALE = 1.06f
    const val WORD_POP_MS = 120
    const val ICON_ENTER_MS = 200
    const val UNDERLINE_DRAW_MS = 200
    const val STYLE_CROSSFADE_MS = 200
    const val PREVIEW_LINE_MS = 2_800L
    const val DEFAULT_LINE_MS = 4_000L
    const val MIN_LINE_MS = 800L
    const val MAX_LINE_MS = 8_000L
    const val VISIBLE_LINE_OVERSCAN = 3
    const val INTERLUDE_GAP_MS = 6_500L
    const val WORD_GAP_EM = 0.5f
    const val HERO_WORD_SCALE = 1f
    const val FUNCTION_WORD_SCALE = 0.55f
    const val MIN_WORD_SCALE_FACTOR = 0.55f
    const val MAX_WORD_SCALE_FACTOR = 0.80f
    const val UNDERLINE_WIDTH_DP = 3f
    const val UNDERLINE_EXTRA_WIDTH_DP = 5f
    const val LABEL_CORNER_DP = 6f
    const val LABEL_HORIZONTAL_PADDING_DP = 6f
    const val LABEL_VERTICAL_PADDING_DP = 2f
    const val LABEL_NOTCH_COUNT = 3
    const val LABEL_NOTCH_DEPTH_DP = 2.1f
    const val LABEL_NOTCH_SPACING_FRACTION = 0.18f
    const val EDGE_SAFE_FRACTION = 0.92f

    val Upcoming = Color(0xFF8F8A84)
    val Sung = Color(0xFFC9C4BE)
    val ActiveInk = Color(0xFF0F0D0A)
    val ActiveLabel = Color.White
    val MutedLabel = Color(0xFF5B5753)
    val FallbackBackground = Color(0xFF0F0D0A)

    val FunctionWords = setOf("in", "an", "but", "it", "all", "my", "a", "the", "to", "of", "and")

    enum class Icon {
        HOURGLASS, PERSON, HEART, FLAME, SUN, MOON, STAR, RAIN, DANCE, RUN,
        HOME, EYE, DROP, CLOCK, PHONE, MUSIC
    }

    data class KeywordIcon(
        val icon: Icon,
        val materialSymbol: String,
    )

    /** Lowercase keys are matched after Unicode punctuation is stripped. */
    val KeywordIcons: Map<String, KeywordIcon> = buildMap {
        listOf("wait", "waiting", "waited").forEach { put(it, KeywordIcon(Icon.HOURGLASS, "hourglass_top")) }
        listOf("you", "your", "youre", "yours").forEach { put(it, KeywordIcon(Icon.PERSON, "person")) }
        listOf("heart", "hearts", "love", "loved").forEach { put(it, KeywordIcon(Icon.HEART, "favorite")) }
        listOf("fire", "burn", "burning", "flame").forEach { put(it, KeywordIcon(Icon.FLAME, "local_fire_department")) }
        listOf("sun", "sunshine", "sunrise").forEach { put(it, KeywordIcon(Icon.SUN, "sunny")) }
        listOf("moon", "night", "midnight").forEach { put(it, KeywordIcon(Icon.MOON, "bedtime")) }
        listOf("star", "stars").forEach { put(it, KeywordIcon(Icon.STAR, "star")) }
        listOf("rain", "rainy").forEach { put(it, KeywordIcon(Icon.RAIN, "rainy")) }
        listOf("dance", "dancing").forEach { put(it, KeywordIcon(Icon.DANCE, "nightlife")) }
        listOf("run", "running").forEach { put(it, KeywordIcon(Icon.RUN, "directions_run")) }
        put("home", KeywordIcon(Icon.HOME, "home"))
        listOf("eyes", "eye").forEach { put(it, KeywordIcon(Icon.EYE, "visibility")) }
        listOf("cry", "tears", "crying").forEach { put(it, KeywordIcon(Icon.DROP, "water_drop")) }
        put("time", KeywordIcon(Icon.CLOCK, "schedule"))
        listOf("call", "phone").forEach { put(it, KeywordIcon(Icon.PHONE, "call")) }
        listOf("sing", "song", "music", "melody", "rhythm").forEach { put(it, KeywordIcon(Icon.MUSIC, "music_note")) }
        listOf("sky", "cloud", "clouds").forEach { put(it, KeywordIcon(Icon.RAIN, "cloud")) }
        listOf("ocean", "sea", "wave", "waves").forEach { put(it, KeywordIcon(Icon.RAIN, "water")) }
        listOf("wind", "breeze").forEach { put(it, KeywordIcon(Icon.SUN, "air")) }
        listOf("dream", "dreams", "sleep", "sleeping").forEach { put(it, KeywordIcon(Icon.MOON, "bedtime")) }
        listOf("smile", "smiling", "laugh", "laughter").forEach { put(it, KeywordIcon(Icon.HEART, "sentiment_satisfied")) }
        listOf("angel", "angels", "heaven").forEach { put(it, KeywordIcon(Icon.STAR, "brightness_5")) }
        listOf("light", "shine", "shining", "glow").forEach { put(it, KeywordIcon(Icon.SUN, "light_mode")) }
        listOf("dark", "darkness", "shadow").forEach { put(it, KeywordIcon(Icon.MOON, "dark_mode")) }
        listOf("road", "street", "drive", "driving").forEach { put(it, KeywordIcon(Icon.RUN, "route")) }
        listOf("rainbow", "color").forEach { put(it, KeywordIcon(Icon.SUN, "palette")) }
    }

    /** Stable FNV-style hash; no frame/time/random input is allowed. */
    fun seed(songId: String, lineIndex: Int, wordIndex: Int): Int {
        var hash = 0x811C9DC5.toInt()
        val source = "$songId:$lineIndex:$wordIndex"
        source.forEach { char ->
            hash = hash xor char.code
            hash *= 0x01000193
        }
        return hash and Int.MAX_VALUE
    }

    fun normalizedWord(word: String): String =
        word.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
}
