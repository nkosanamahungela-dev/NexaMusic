/**
 * NexaMusic AMLL TTML Database provider.
 *
 * This provider fetches lyrics on demand; it does not bundle or redistribute
 * the upstream database. Upstream data licensing varies by source; see NOTICE.
 * The provider implementation is part of NexaMusic and is licensed under GPL-3.0.
 */
package com.convx.music.lyrics

import com.convx.music.betterlyrics.TTMLParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Uses Apple's public iTunes Search endpoint only to resolve a track's catalog ID,
 * then requests the corresponding TTML file from the AMLL community database.
 *
 * Exact title/artist matching and a duration check are intentional: a wrong edit
 * or recording can make otherwise accurate word timestamps appear out of sync.
 */
object AmllTtmlDatabaseProvider : LyricsProvider {
    override val id = "AmllTtmlDatabase"
    override val displayName = "AMLL TTML Database"

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 8_000
                socketTimeoutMillis = 15_000
            }
            expectSuccess = false
        }
    }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = try {
        if (title.isBlank() || artist.isBlank()) {
            throw NoSuchElementException("Track title or artist is unavailable")
        }

        val appleMusicId = resolveAppleMusicId(title, artist, duration, album)
            ?: throw NoSuchElementException("No matching Apple Music catalog entry")

        val response = client.get(
            "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/main/am-lyrics/$appleMusicId.ttml"
        )
        if (response.status != HttpStatusCode.OK) {
            throw NoSuchElementException("No AMLL TTML lyrics for this track")
        }

        val ttml = response.bodyAsText()
        if (ttml.isBlank() || !ttml.contains("<tt")) {
            throw IllegalStateException("The AMLL database returned an invalid TTML file")
        }

        // Keep every timed TTML span separate. AMLL files often time CJK
        // characters/syllables individually, even when no spaces separate them.
        val lines = TTMLParser.parseTTML(ttml, mergeAdjacentSpans = false)
        if (lines.isEmpty()) {
            throw IllegalStateException("Could not parse the AMLL TTML lyrics")
        }

        Result.success(TTMLParser.toLRC(lines))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun resolveAppleMusicId(
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Long? {
        val response = client.get("https://itunes.apple.com/search") {
            parameter("term", "$title ${artist.substringBefore(",").trim()}")
            parameter("entity", "song")
            parameter("limit", 25)
        }
        if (response.status != HttpStatusCode.OK) return null

        val root = runCatching { Json.parseToJsonElement(response.bodyAsText()).jsonObject }
            .getOrNull() ?: return null
        val results = root["results"]?.jsonArray ?: return null
        val expectedTitle = normalize(title)
        val requestedArtists = artist.split(",").map(::normalize).filter { it.isNotBlank() }
        val expectedAlbum = normalize(album.orEmpty())
        val expectedDuration = duration.takeIf { it > 0 }?.times(1_000L)

        data class Candidate(
            val id: Long,
            val albumMatches: Boolean,
            val durationDifference: Long,
        )

        return results.mapNotNull { item ->
            val track = item as? JsonObject ?: return@mapNotNull null
            val trackId = track.longValue("trackId") ?: return@mapNotNull null
            val trackTitle = normalize(track.stringValue("trackName"))
            val trackArtist = normalize(track.stringValue("artistName"))
            val artistMatches = requestedArtists.any { requested ->
                trackArtist == requested || trackArtist.contains(requested) || requested.contains(trackArtist)
            }
            if (trackTitle != expectedTitle || trackArtist.isBlank() || !artistMatches) {
                return@mapNotNull null
            }

            val trackDuration = track.longValue("trackTimeMillis") ?: 0L
            val durationDifference = if (expectedDuration != null && trackDuration > 0) {
                abs(trackDuration - expectedDuration)
            } else {
                0L
            }
            val durationTolerance = if (expectedDuration != null) {
                max(5_000L, expectedDuration * 3 / 100)
            } else {
                Long.MAX_VALUE
            }
            if (durationDifference > durationTolerance) return@mapNotNull null

            Candidate(
                id = trackId,
                albumMatches = expectedAlbum.isNotBlank() &&
                    normalize(track.stringValue("collectionName")) == expectedAlbum,
                durationDifference = durationDifference,
            )
        }.sortedWith(
            compareByDescending<Candidate> { it.albumMatches }
                .thenBy { it.durationDifference }
        ).firstOrNull()?.id
    }

    private fun JsonObject.stringValue(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.longValue(key: String): Long? =
        this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
}
