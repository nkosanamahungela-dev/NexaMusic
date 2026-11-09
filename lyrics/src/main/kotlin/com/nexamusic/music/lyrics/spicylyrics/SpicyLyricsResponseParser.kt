/**
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Parses the documented Spicy Lyrics word/syllable response shape.
 * This file is an independent adapter; it does not contain code from
 * Spicy Lyrics Mobile or Square.
 */
package com.nexamusic.music.lyrics.spicylyrics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One timed syllable as returned by the Spicy Lyrics API. Times are normalized to milliseconds. */
data class SpicyTimedSyllable(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val isPartOfWord: Boolean,
)

/** A lead-vocal segment. Keeping segment boundaries avoids joining separate lyric lines. */
data class SpicyTimedLine(
    val syllables: List<SpicyTimedSyllable>,
) {
    val text: String get() = syllables.joinToString("") { it.text }
}

/**
 * Small, tolerant parser for Body.Content[].Lead.Syllables[].
 *
 * The parser deliberately preserves IsPartOfWord instead of guessing word
 * boundaries. The renderer adapter can use that flag without losing the
 * provider's original syllable timing.
 */
object SpicyLyricsResponseParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(response: String): List<SpicyTimedLine> {
        val root = runCatching { json.parseToJsonElement(response).jsonObject }.getOrNull()
            ?: return emptyList()
        val body = root.objectAt("Body") ?: return emptyList()
        val content = body.arrayAt("Content") ?: return emptyList()

        return content.mapNotNull { item ->
            val lead = item.asObject()?.objectAt("Lead") ?: return@mapNotNull null
            val syllables = lead.arrayAt("Syllables").orEmpty().mapNotNull(::parseSyllable)
            syllables.takeIf { it.isNotEmpty() }?.let(::SpicyTimedLine)
        }
    }

    private fun parseSyllable(element: JsonElement): SpicyTimedSyllable? {
        val value = element.asObject() ?: return null
        val text = value.stringAt("Text")?.takeIf(String::isNotEmpty) ?: return null
        val start = value.numberAt("StartTime") ?: return null
        val end = value.numberAt("EndTime") ?: return null
        val startMs = secondsToMillis(start)
        val endMs = secondsToMillis(end)
        if (startMs < 0L || endMs <= startMs) return null

        return SpicyTimedSyllable(
            text = text,
            startMs = startMs,
            endMs = endMs,
            isPartOfWord = value.booleanAt("IsPartOfWord") ?: false,
        )
    }

    private fun secondsToMillis(seconds: Double): Long =
        (seconds * 1_000.0).toLong()

    private fun JsonObject.objectAt(key: String): JsonObject? =
        this[key]?.asObject()

    private fun JsonObject.arrayAt(key: String): JsonArray? =
        this[key] as? JsonArray

    private fun JsonObject.stringAt(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.numberAt(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull
            ?: this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

    private fun JsonObject.booleanAt(key: String): Boolean? =
        this[key]?.jsonPrimitive?.booleanOrNull

    private fun JsonElement.asObject(): JsonObject? =
        runCatching { jsonObject }.getOrNull()
}

/**
 * Converts parsed timing to NexaMusic's existing rich-sync LRC wire format.
 * LyricsUtils.parseLyrics() already understands this format and creates
 * LyricsEntry/WordTimestamp values consumed by AppleMusicV3Lyrics.
 *
 * IsPartOfWord marks syllables that continue a word. A false value closes
 * the current word. The final syllable is also flushed at the end of a line.
 */
fun List<SpicyTimedLine>.toNexaMusicRichSyncLyrics(): String =
    mapNotNull { line ->
        val words = line.toTimedWords()
        if (words.isEmpty()) return@mapNotNull null

        val lineStart = words.minOf { it.startMs }
        buildString {
            append('[').append(formatTimestamp(lineStart)).append(']')
            words.forEachIndexed { index, word ->
                if (index > 0) append(' ')
                append('<')
                    .append(formatTimestamp(word.startMs))
                    .append('|')
                    .append(formatTimestamp(word.endMs))
                    .append('>')
                    .append(word.text)
            }
        }
    }.joinToString("\n")

private data class SpicyTimedWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

private fun SpicyTimedLine.toTimedWords(): List<SpicyTimedWord> {
    val result = mutableListOf<SpicyTimedWord>()
    val current = mutableListOf<SpicyTimedSyllable>()

    fun flush() {
        if (current.isEmpty()) return
        val text = current.joinToString("") { it.text }.trim()
        val start = current.minOf { it.startMs }
        val end = current.maxOf { it.endMs }
        if (text.isNotEmpty() && end > start) {
            result += SpicyTimedWord(text, start, end)
        }
        current.clear()
    }

    syllables.forEach { syllable ->
        current += syllable
        if (!syllable.isPartOfWord) flush()
    }
    flush()
    return result
}

private fun formatTimestamp(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0L)
    val minutes = safe / 60_000L
    val seconds = safe / 1_000L % 60L
    val millis = safe % 1_000L
    return "%02d:%02d.%03d".format(java.util.Locale.ROOT, minutes, seconds, millis)
}
