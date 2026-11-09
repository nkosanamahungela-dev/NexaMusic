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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One timed syllable as returned by the API. API times are seconds, stored here as milliseconds. */
data class SpicyTimedSyllable(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    /** True means this syllable has no trailing space and continues the current word group. */
    val isPartOfWord: Boolean,
)

data class SpicyTimedBackground(val syllables: List<SpicyTimedSyllable>)

data class SpicyTimedLine(
    val syllables: List<SpicyTimedSyllable>,
    val backgrounds: List<SpicyTimedBackground> = emptyList(),
    val oppositeAligned: Boolean = false,
) {
    val text: String get() = syllables.joinToString("") { it.text }
}

data class SpicyLyricsAttribution(
    val source: String?,
    val uploader: String?,
    val maker: String?,
)

data class SpicyLyricsDocument(
    val lines: List<SpicyTimedLine>,
    val attribution: SpicyLyricsAttribution,
)

/**
 * Independent parser for the documented Spicy Lyrics response contract.
 * No renderer implementation or source from Spicy Lyrics Mobile/Square is copied.
 */
object SpicyLyricsResponseParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(response: String): List<SpicyTimedLine> = parseDocument(response)?.lines.orEmpty()

    fun parseDocument(response: String): SpicyLyricsDocument? {
        val root = runCatching { json.parseToJsonElement(response).jsonObject }.getOrNull()
            ?: return null
        val body = root.objectAt("Body") ?: return null
        val content = body.arrayAt("Content") ?: return null
        // This adapter is specifically for word-timed syllable responses.
        // Line/static results remain eligible for the existing providers.
        if (body.stringAt("Type")?.let { it != "Syllable" } == true) {
            return SpicyLyricsDocument(emptyList(), SpicyLyricsAttribution(
                source = body.stringAt("source"),
                uploader = null,
                maker = null,
            ))
        }
        val lines = content.mapNotNull { item ->
            val row = item.asObject() ?: return@mapNotNull null
            val lead = row.objectAt("Lead") ?: return@mapNotNull null
            val syllables = lead.arrayAt("Syllables").orEmpty().mapNotNull(::parseSyllable)
            if (syllables.isEmpty()) return@mapNotNull null
            val backgrounds = row.arrayAt("Background").orEmpty().mapNotNull { background ->
                val parts = background.asObject()?.arrayAt("Syllables").orEmpty().mapNotNull(::parseSyllable)
                parts.takeIf { it.isNotEmpty() }?.let(::SpicyTimedBackground)
            }
            SpicyTimedLine(
                syllables = syllables,
                backgrounds = backgrounds,
                oppositeAligned = row.booleanAt("OppositeAligned") ?: false,
            )
        }
        val attribution = body.objectAt("UploadAttribution")
        return SpicyLyricsDocument(
            lines = lines,
            attribution = SpicyLyricsAttribution(
                source = body.stringAt("source"),
                uploader = attribution?.objectAt("Uploader")?.stringAt("username"),
                maker = attribution?.objectAt("Maker")?.stringAt("username"),
            ),
        )
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
 * Converts lead and background timing to NexaMusic's rich-sync LRC format.
 * LyricsUtils.parseLyrics() consumes this format and creates LyricsEntry /
 * WordTimestamp values for the existing Apple Music V3 renderer.
 */
fun SpicyLyricsDocument.toNexaMusicRichSyncLyrics(): String =
    lines.flatMap { line ->
        buildList {
            line.toRichSyncRow(background = false)?.let(::add)
            line.backgrounds.forEach { background ->
                background.toRichSyncRow(
                    background = true,
                    oppositeAligned = line.oppositeAligned,
                )?.let(::add)
            }
        }
    }.joinToString("\n")

/** Convenience overload for callers that only have lead lines. */
fun List<SpicyTimedLine>.toNexaMusicRichSyncLyrics(): String =
    SpicyLyricsDocument(this, SpicyLyricsAttribution(null, null, null))
        .toNexaMusicRichSyncLyrics()

private fun SpicyTimedLine.toRichSyncRow(background: Boolean): String? =
    toRichSyncRow(syllables, background, oppositeAligned)

private fun SpicyTimedBackground.toRichSyncRow(
    background: Boolean,
    oppositeAligned: Boolean,
): String? = toRichSyncRow(syllables, background, oppositeAligned)

private fun toRichSyncRow(
    syllables: List<SpicyTimedSyllable>,
    background: Boolean,
    oppositeAligned: Boolean,
): String? {
    val words = syllables.toTimedWords()
    if (words.isEmpty()) return null
    val lineStart = words.minOf { it.startMs }
    return buildString {
        append('[').append(formatTimestamp(lineStart)).append(']')
        if (background) append("{bg}")
        if (oppositeAligned) append(if (background) "{agent:v2}" else "{agent:v1}")
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
}

private data class SpicyTimedWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

private fun List<SpicyTimedSyllable>.toTimedWords(): List<SpicyTimedWord> {
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

    forEach { syllable ->
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
