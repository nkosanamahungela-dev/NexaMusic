package com.nexamusic.music.lyrics.spicylyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpicyLyricsResponseParserTest {
    @Test
    fun parsesLeadSyllablesAndConvertsSecondsToMilliseconds() {
        val response = """
            {
              "Body": {
                "Content": [
                  {
                    "Lead": {
                      "Syllables": [
                        {"Text":"Hel","StartTime":1.25,"EndTime":1.55,"IsPartOfWord":true},
                        {"Text":"lo","StartTime":1.55,"EndTime":1.90,"IsPartOfWord":false}
                      ]
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val lines = SpicyLyricsResponseParser.parse(response)

        assertEquals(1, lines.size)
        assertEquals("Hello", lines.single().text)
        assertEquals(1_250L, lines.single().syllables[0].startMs)
        assertEquals(1_900L, lines.single().syllables[1].endMs)
        assertTrue(lines.single().syllables[0].isPartOfWord)
    }

    @Test
    fun ignoresMalformedSyllablesAndEmptySegments() {
        val response = """
            {
              "Body": {
                "Content": [
                  {"Lead":{"Syllables":[{"Text":"bad","StartTime":2.0,"EndTime":1.0}]}},
                  {"Lead":{"Syllables":[{"Text":"ok","StartTime":0.0,"EndTime":0.4}]}}
                ]
              }
            }
        """.trimIndent()

        val lines = SpicyLyricsResponseParser.parse(response)

        assertEquals(1, lines.size)
        assertEquals("ok", lines.single().text)
    }

    @Test
    fun returnsEmptyForUnexpectedPayload() {
        assertTrue(SpicyLyricsResponseParser.parse("""{"error":"not found"}""").isEmpty())
    }
}
