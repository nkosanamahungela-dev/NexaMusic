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
import android.net.Uri
import com.tidal.sdk.auth.Auth
import com.tidal.sdk.auth.TidalAuth
import com.tidal.sdk.auth.model.AuthConfig
import com.tidal.sdk.auth.model.AuthResult
import com.tidal.sdk.auth.model.LoginConfig
import com.tidal.sdk.auth.model.QueryParameter
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
    private val auth: Auth,
    private val credentialsProvider: com.tidal.sdk.auth.CredentialsProvider,
) {
    val events: Flow<TidalPlaybackEvent>
        get() = player.playbackEngine.events

    val playbackState
        get() = player.playbackEngine.playbackState

    val assetPositionSeconds: Float
        get() = player.playbackEngine.assetPosition

    val isUserLoggedIn: Boolean
        get() = credentialsProvider.isUserLoggedIn()

    /** Starts TIDAL's Authorization Code + PKCE login flow. */
    fun createLoginUri(redirectUri: String): Uri {
        require(redirectUri.isNotBlank()) { "A registered TIDAL redirect URI is required." }
        return auth.initializeLogin(
            redirectUri,
            LoginConfig(customParams = setOf(QueryParameter(key = "appMode", value = "android"))),
        )
    }

    /** Completes the OAuth redirect using the untouched query component. */
    suspend fun completeLogin(redirectQuery: String): AuthResult<Nothing> {
        require(redirectQuery.isNotBlank()) { "The TIDAL login redirect query is missing." }
        return auth.finalizeLogin(redirectQuery)
    }

    suspend fun logout() = auth.logout()

    /**
     * Loads a catalog track through the official SDK. Public Client Credentials
     * access is preview-only; full-length playback depends on TIDAL approving
     * this app's Authorization Code client and the user's account entitlement.
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
        player.playbackEngine.immersiveAudio = false
    }

    /**
     * Mirrors the friend's quality selector while using only quality modes
     * actually exposed by the official Android SDK.
     *
     * HI_RES_DOLBY requests Hi-Res lossless and enables immersive audio;
     * availability still depends on TIDAL's track and account entitlements.
     */
    fun setQuality(option: String) {
        when (option.trim().uppercase()) {
            "HI_RES" -> {
                player.playbackEngine.streamingWifiAudioQuality = AudioQuality.HI_RES_LOSSLESS
                player.playbackEngine.streamingCellularAudioQuality = AudioQuality.HI_RES_LOSSLESS
                player.playbackEngine.immersiveAudio = false
            }
            "HI_RES_DOLBY" -> {
                player.playbackEngine.streamingWifiAudioQuality = AudioQuality.HI_RES_LOSSLESS
                player.playbackEngine.streamingCellularAudioQuality = AudioQuality.HI_RES_LOSSLESS
                player.playbackEngine.immersiveAudio = true
            }
            "CD", "LOSSLESS" -> {
                player.playbackEngine.streamingWifiAudioQuality = AudioQuality.LOSSLESS
                player.playbackEngine.streamingCellularAudioQuality = AudioQuality.LOSSLESS
                player.playbackEngine.immersiveAudio = false
            }
            "HIGH" -> requestHighQuality()
            "LOW" -> {
                player.playbackEngine.streamingWifiAudioQuality = AudioQuality.LOW
                player.playbackEngine.streamingCellularAudioQuality = AudioQuality.LOW
                player.playbackEngine.immersiveAudio = false
            }
            else -> throw IllegalArgumentException("Unsupported TIDAL quality option: $option")
        }
    }

    fun release() {
        player.release()
    }

    companion object {
        private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Creates the official TIDAL SDK player.
         *
         * Add the following to the ignored local.properties file:
         * tidal.clientid=YOUR_CLIENT_ID
         * tidal.clientredirecturi=nexamusic://tidal-auth/callback
         * tidal.clientscopes=YOUR_APPROVED_SCOPES
         *
         * Authorization Code login uses PKCE. No client secret is packaged in
         * the Android app.
         */
        fun create(application: Application): TidalSdkManager {
            val clientId = BuildConfig.TIDAL_CLIENT_ID
            val redirectUri = BuildConfig.TIDAL_REDIRECT_URI
            val scopes = BuildConfig.TIDAL_CLIENT_SCOPES.split(' ').filter { it.isNotBlank() }.toSet()

            require(clientId.isNotBlank()) {
                "TIDAL is not configured. Add tidal.clientid to local.properties."
            }
            require(redirectUri.isNotBlank()) {
                "TIDAL redirect URI is missing. Add tidal.clientredirecturi to local.properties."
            }

            val auth = TidalAuth.getInstance(
                AuthConfig(
                    clientId = clientId,
                    clientSecret = null,
                    credentialsKey = "nexamusic-tidal-auth",
                    scopes = scopes,
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
                auth = auth.auth,
                credentialsProvider = auth.credentialsProvider,
            )
        }
    }
}
