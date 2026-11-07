package com.music.spotify

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Kotlin client for NexaMusic's SpotAPI gateway.
 * The service URL is configured by the Android app; no Spotify credentials are used.
 */
object SpotApi {
    @Volatile
    private var baseUrl: String = ""

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 20_000
            }
            expectSuccess = true
        }
    }

    fun configure(serviceBaseUrl: String) {
        baseUrl = serviceBaseUrl.trim().trimEnd('/')
    }

    fun isConfigured(): Boolean = baseUrl.startsWith("https://")

    suspend fun searchTracks(query: String, limit: Int = 20): Result<List<SpotApiTrack>> =
        runCatching {
            require(query.isNotBlank()) { "Search query cannot be empty" }
            require(query.length <= 120) { "Search query is too long" }
            require(limit in 1..50) { "Limit must be between 1 and 50" }
            val endpoint = baseUrl.takeIf { it.startsWith("https://") }
                ?: error("SpotAPI service is not configured")
            client.get("$endpoint/v1/search/tracks") {
                parameter("q", query)
                parameter("limit", limit)
            }.body<SpotApiSearchResponse<SpotApiTrack>>().items
        }
}

@Serializable
data class SpotApiSearchResponse<T>(
    val query: String = "",
    val items: List<T> = emptyList(),
)

@Serializable
data class SpotApiTrack(
    val name: String = "",
    val uri: String = "",
    @SerialName("duration_ms") val durationMs: Long? = null,
    val artists: List<String> = emptyList(),
    val album: String? = null,
) {
    val id: String
        get() = uri.substringAfterLast(':').takeIf { it != uri }.orEmpty()
}
