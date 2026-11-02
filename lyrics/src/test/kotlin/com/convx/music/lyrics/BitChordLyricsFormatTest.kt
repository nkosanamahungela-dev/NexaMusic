package com.convx.music.lyrics

import com.convx.music.lyrics.bitchord.*
import org.junit.Assert.*
import org.junit.Test

class BitChordLyricsFormatTest {
    @Test fun preservesWordEndsDuetsAndBackgrounds() {
        val line = LyricLine(timeMs = 1250, text = "Stay here", words = listOf(
            LyricWord(1250, 1800, "Stay"), LyricWord(2000, 2500, "here")),
            alignment = LyricAlignment.End,
            background = LyricLine(2000, "Oh", words = listOf(LyricWord(2000, 2400, "Oh"))))
        val encoded = listOf(line).toConvxLyrics()
        assertTrue(encoded.contains("{agent:v2}"))
        assertTrue(encoded.contains("<00:01.250|00:01.800>Stay"))
        assertTrue(encoded.contains("<00:02.000|00:02.500>here"))
        assertTrue(encoded.contains("[00:02.000]{bg}"))
    }

    @Test fun parsesAppleTtmlAndRetainsSyllableTimingInProviderFormat() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
                <body><div><p begin="0.0" end="2.0">
                    <span begin="0.0" end="0.5">Hel</span><span begin="0.5" end="0.9">lo</span> <span begin="1.0" end="2.0">world</span>
                </p></div></body>
            </tt>
        """.trimIndent()

        val parsed = TtmlLyrics.parse(ttml)
        assertEquals(1, parsed.size)
        assertEquals("Hello world", parsed.single().text)
        assertEquals(2, parsed.single().words.size)
        assertEquals(2, parsed.single().words.first().syllables.size)

        val encoded = parsed.toConvxLyrics()
        assertTrue(encoded.contains("<00:00.000|00:00.900>Hello"))
        assertTrue(encoded.contains("<00:01.000|00:02.000>world"))
    }

}
