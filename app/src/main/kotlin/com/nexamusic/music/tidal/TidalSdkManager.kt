/*
 * NexaMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * TIDAL SDK integration foundation.
 *
 * This uses TIDAL's official Android Player/Auth SDK. With the publicly
 * supported Client Credentials flow, TIDAL documents preview playback only.
 * Full-length playback must remain disabled unless TIDAL approves NexaMusic
 * for an eligible user-authentication flow.
 */
package com.nexamusic.music.tidal

import android.app.Application
import com.nexamusic.music.BuildConfig
import com.tidal.sdk.auth.TidalAuth
import com.tidal.sdk.auth.model.AuthConfig
import com.tidal.sdk.auth.network.NetworkLogLevel
import com.tidal.sdk.eventproducer.EventProducer
import com.tidal.sdk.eventproducer.model.ConsentCategory
import com.tidal.sdk.eventproducer.model.EventsConfig
import com.tidal.sdk.player.Player as TidalPlayer
import com.tidal.sdk.player.common.model.AudioQuality
import com.tidal.sdk.player.common.model.MediaProduct
import com.tidal.sdk.player.common.model.ProductType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import com.tidal.sdk.player.playbackengine.model.Event as TidalPlaybackEvent

/**
 * Owns the official TIDAL Player SDK instance for NexaMusic.
 *
 * This class intentionally does not expose stream URLs. TIDAL's SDK owns
 * stream resolution, entitlement checks, playback, and session reporting.
 */
class TidalSdkManager private constructor(
    private val player: TidalPlayer,
    private val credentialsProvider: com.tidal.sdk.auth.CredentialsProvider,
) {
    val events: Flow<TidalPlaybackEvent>
        get() = player.playbackEngine.events

    val playbackState
        get() = player.playbackEngine.playbackState

    val assetPositionSeconds: Float
        get() = player.playbackEngine.assetPosition

    /**
     * Authenticates using the public Client Credentials flow and starts the
     * requested TIDAL catalog track. Public credentials are limited to
     * previews unless TIDAL has separately authorised the application.
     */
    suspend fun playTrack(trackId: String) {
        require(trackId.isNotBlank()) { "A TIDAL track ID is required." }

        val credentials = credentialsProvider.getCredentials()
        check(!credentials.successData?.token.isNullOrBlank()) {
            "TIDAL authentication failed. Check the NexaMusic developer credentials."
        }

        player.playbackEngine.load(
            MediaProduct(
                productType = ProductType.TRACK,
                productId = trackId.trim(),
            ),
        )
        player.playbackEngine.play()
    }

    fun pause() = player.playbackEngine.pause()

    fun resume() = player.playbackEngine.play()

    fun seekTo(seconds: Float) {
        require(seconds >= 0f) { "Seek position cannot be negative." }
        player.playbackEngine.seek(seconds)
    }

    fun setNextTrack(trackId: String?) {
        player.playbackEngine.setNext(
            trackId?.takeIf { it.isNotBlank() }?.let {
                MediaProduct(ProductType.TRACK, it.trim())
            },
        )
    }

    /** Requests the best CD-lossless quality available to the authenticated account. */
    fun requestCdLossless() {
        player.playbackEngine.streamingWifiAudioQuality = AudioQuality.LOSSLESS
        player.playbackEngine.streamingCellularAudioQuality = AudioQuality.LOSSLESS
    }

    /** Requests TIDAL Hi-Res FLAC where the account and track support it. */
    fun requestHiResLossless() {
        player.playbackEngine.streamingWifiAudioQuality = AudioQuality.HI_RES_LOSSLESS
        player.playbackEngine.streamingCellularAudioQuality = AudioQuality.HI_RES_LOSSLESS
    }

    fun requestHighQuality() {
        player.playbackEngine.streamingWifiAudioQuality = AudioQuality.HIGH
        player.playbackEngine.streamingCellularAudioQuality = AudioQuality.HIGH
    }

    fun release() {
        player.release()
    }

    companion object {
        private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Creates the official TIDAL SDK player.
         *
         * Add the following to the ignored local.properties file for local
         * debug testing only:
         * tidal.clientid=YOUR_CLIENT_ID
         * tidal.clientsecret=YOUR_CLIENT_SECRET
         *
         * The secret is included in debug builds only. Release builds receive
         * an empty secret and cannot accidentally ship this development value.
         */
        fun create(application: Application): TidalSdkManager {
            val clientId = BuildConfig.TIDAL_CLIENT_ID
            val clientSecret = BuildConfig.TIDAL_CLIENT_SECRET

            require(clientId.isNotBlank()) {
                "TIDAL is not configured. Add tidal.clientid to local.properties."
            }
            require(clientSecret.isNotBlank()) {
                "TIDAL debug credentials are missing. Add tidal.clientsecret to local.properties."
            }

            val auth = TidalAuth.getInstance(
                AuthConfig(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    credentialsKey = "nexamusic-tidal-auth",
                    enableCertificatePinning = true,
                    logLevel = NetworkLogLevel.NONE,
                ),
                application,
            )

            val eventProducer = EventProducer.getInstance(
                credentialsProvider = auth.credentialsProvider,
                config = EventsConfig(
                    maxDiskUsageBytes = 1_000_000,
                    blockedConsentCategories = setOf(
                        ConsentCategory.TARGETING,
                        ConsentCategory.PERFORMANCE,
                    ),
                    appVersion = BuildConfig.VERSION_NAME,
                ),
                context = application.applicationContext,
                coroutineScope = eventScope,
            )

            val tidalPlayer = TidalPlayer(
                application = application,
                credentialsProvider = auth.credentialsProvider,
                eventSender = eventProducer.eventSender,
                version = BuildConfig.VERSION_NAME,
                enableFloatOutput = true,
            ).apply {
                playbackEngine.streamingWifiAudioQuality = AudioQuality.LOSSLESS
                playbackEngine.streamingCellularAudioQuality = AudioQuality.LOSSLESS
            }

            return TidalSdkManager(
                player = tidalPlayer,
                credentialsProvider = auth.credentialsProvider,
            )
        }
    }
}
