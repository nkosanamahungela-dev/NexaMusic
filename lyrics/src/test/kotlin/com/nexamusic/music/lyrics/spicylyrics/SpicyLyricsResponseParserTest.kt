package com.nexamusic.music.lyrics.spicylyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun groupsSyllablesIntoWordsAndWritesNexaMusicRichSync() {
        val response = """
            {
              "Body": {
                "source": "apple_music",
                "Content": [{
                  "Lead": {
                    "Syllables": [
                      {"Text":"Hel","StartTime":1.0,"EndTime":1.2,"IsPartOfWord":true},
                      {"Text":"lo","StartTime":1.2,"EndTime":1.5,"IsPartOfWord":false},
                      {"Text":" ","StartTime":1.5,"EndTime":1.6,"IsPartOfWord":false},
                      {"Text":"world","StartTime":1.6,"EndTime":2.0,"IsPartOfWord":false}
                    ]
                  }
                }]
              }
            }
        """.trimIndent()

        val document = SpicyLyricsResponseParser.parseDocument(response)!!

        assertEquals("apple_music", document.attribution.source)
        assertEquals(
            "[00:01.000]<00:01.000|00:01.500>Hello <00:01.600|00:02.000>world",
            document.toNexaMusicRichSyncLyrics(),
        )
    }

    @Test
    fun parsesBackgroundVocalsAndUploaderAttribution() {
        val response = """
            {
              "Body": {
                "source": "spicy_lyrics",
                "UploadAttribution": {
                  "Uploader": {"username":"community-uploader"},
                  "Maker": {"username":"sync-maker"}
                },
                "Content": [{
                  "OppositeAligned": true,
                  "Lead": {"Syllables":[
                    {"Text":"Lead","StartTime":0.0,"EndTime":0.5,"IsPartOfWord":false}
                  ]},
                  "Background": [{
                    "Syllables":[
                      {"Text":"Echo","StartTime":0.2,"EndTime":0.6,"IsPartOfWord":false}
                    ]
                  }]
                }]
              }
            }
        """.trimIndent()

        val document = SpicyLyricsResponseParser.parseDocument(response)!!

        assertEquals("community-uploader", document.attribution.uploader)
        assertEquals("sync-maker", document.attribution.maker)
        assertEquals(
            "[00:00.000]{agent:v1}<00:00.000|00:00.500>Lead\n" +
                "[00:00.200]{bg}{agent:v2}<00:00.200|00:00.600>Echo",
            document.toNexaMusicRichSyncLyrics(),
        )
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
        assertNull(SpicyLyricsResponseParser.parseDocument("""{"error":"not found"}"""))
    }
}
