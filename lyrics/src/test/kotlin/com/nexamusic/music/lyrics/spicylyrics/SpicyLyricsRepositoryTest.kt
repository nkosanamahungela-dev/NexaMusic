package com.nexamusic.music.lyrics.spicylyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpicyLyricsRepositoryTest {
    @Test
    fun fetchesAndConvertsAnAuthorizedGatewayResponse() = runBlocking {
        val response = """
            {
              "Body": {
                "Type": "Syllable",
                "source": "apple_music",
                "Content": [{
                  "Lead": {
                    "Syllables": [
                      {"Text":"Hello","StartTime":0.5,"EndTime":1.0,"IsPartOfWord":false}
                    ]
                  }
                }]
              }
            }
        """.trimIndent()
        var receivedQuery: SpicyTrackQuery? = null
        val repository = SpicyLyricsRepository(
            SpicyLyricsGateway { query ->
                receivedQuery = query
                response
            },
        )

        val result = repository.fetch(
            SpicyTrackQuery("Example", "Artist", durationMs = 180_000L),
        )

        assertTrue(result.isSuccess)
        assertEquals(180.0, receivedQuery?.durationSeconds ?: 0.0, 0.001)
        assertEquals(
            "[00:00.500]<00:00.500|00:01.000>Hello",
            result.getOrThrow().richSyncLyrics,
        )
        assertEquals("apple_music", result.getOrThrow().document.attribution.source)
    }

    @Test
    fun returnsFailureWhenNoWordTimedLyricsAreAvailable() = runBlocking {
        val repository = SpicyLyricsRepository(SpicyLyricsGateway { null })
        val result = repository.fetch(
            SpicyTrackQuery("Example", "Artist", durationMs = 120_000L),
        )

        assertTrue(result.isFailure)
    }
}
