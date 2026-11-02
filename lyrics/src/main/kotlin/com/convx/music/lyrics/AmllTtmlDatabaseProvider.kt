/**
 * NexaMusic AMLL TTML Database provider.
 *
 * Uses AMLL's public HTTP API to search and fetch TTML on demand. It does not
 * bundle or redistribute upstream lyric files. Upstream data licensing varies
 * by source; see NOTICE. This provider implementation is GPL-3.0.
 */
package com.convx.music.lyrics

import com.convx.music.lyrics.bitchord.TtmlLyrics
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer
import java.util.Locale

/**
 * AMLL's API performs catalogue search against its own metadata. This avoids
 * assuming an iTunes Search result is always an AMLL Apple Music ID.
 */
object AmllTtmlDatabaseProvider : LyricsProvider {
    override val id = "AmllTtmlDatabase"
    override val displayName = "AMLL TTML Database"

    private val client by lazy {
        HttpClient(OkHttp) {
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
        if (title.isBlank()) {
            throw NoSuchElementException("Track title is unavailable")
        }

        val entryId = findEntryId(title, artist, album)
            ?: throw NoSuchElementException("No matching AMLL lyrics entry")

        val response = client.get("$API_BASE/v1/lyrics/get") {
            parameter("id", entryId)
        }
        if (response.status != HttpStatusCode.OK) {
            throw IllegalStateException("AMLL lyrics request failed (" + response.status.value + ")")
        }

        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = root["data"]?.jsonObject
            ?: throw IllegalStateException("AMLL returned no lyric data")
        val ttml = data["lyrics"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (ttml.isBlank() || !ttml.contains("<tt")) {
            throw NoSuchElementException("AMLL returned no TTML lyrics for this track")
        }

        val lines = TtmlLyrics.parse(ttml)
        if (lines.isEmpty()) {
            throw IllegalStateException("Could not parse AMLL TTML lyrics")
        }

        Result.success(lines.toConvxLyrics())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun findEntryId(title: String, artist: String, album: String?): Long? {
        val requestedTitle = normalize(title)
        val requestedArtists = artist.split(",").map(::normalize).filter { it.isNotBlank() }

        // AMLL's search endpoint performs fuzzy matching. Search by title and
        // the primary artist, then rank returned metadata locally to avoid
        // selecting a cover, remix, or similarly named recording.
        val searchArtists = requestedArtists.ifEmpty { listOf("") }
        for (searchArtist in searchArtists) {
            val response = client.get("$API_BASE/v1/lyrics/search") {
                parameter("musicName", title)
                if (searchArtist.isNotBlank()) parameter("artistName", searchArtist)
                parameter("page", 1)
                parameter("pageSize", 50)
            }
            if (response.status != HttpStatusCode.OK) continue

            val root = runCatching {
                Json.parseToJsonElement(response.bodyAsText()).jsonObject
            }.getOrNull() ?: continue
            val data = root["data"]?.jsonObject ?: continue
            val items = data["items"]?.jsonArray ?: continue

            val matches = items.mapNotNull { item ->
                val entry = item as? JsonObject ?: return@mapNotNull null
                val entryId = entry.longValue("id") ?: return@mapNotNull null
                val titles = entry.stringArray("musicNames").map(::normalize)
                val artists = entry.stringArray("artistNames").map(::normalize)
                val albums = entry.stringArray("albumNames").map(::normalize)

                val titleMatches = titles.any {
                    it == requestedTitle || it.contains(requestedTitle) || requestedTitle.contains(it)
                }
                val artistMatches = requestedArtists.isEmpty() || artists.any { candidate ->
                    requestedArtists.any { requested ->
                        candidate == requested || candidate.contains(requested) || requested.contains(candidate)
                    }
                }
                if (!titleMatches || !artistMatches) return@mapNotNull null

                val normalizedAlbum = normalize(album.orEmpty())
                val albumMatches = normalizedAlbum.isNotBlank() &&
                    albums.any { it == normalizedAlbum || it.contains(normalizedAlbum) || normalizedAlbum.contains(it) }
                val exactTitle = titles.any { it == requestedTitle }
                Triple(entryId, exactTitle, albumMatches)
            }

            matches.sortedWith(
                compareByDescending<Triple<Long, Boolean, Boolean>> { it.second }
                    .thenByDescending { it.third }
            ).firstOrNull()?.first?.let { return it }
        }
        return null
    }

    private fun JsonObject.longValue(key: String): Long? =
        this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

    private fun JsonObject.stringArray(key: String): List<String> =
        this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]"), "")

    private const val API_BASE = "https://api.amll.dev"
}
