/**
 * Cover selection for immersive collection headers.
 * Album posters always stay album-specific; an available Canvas is rendered as a separate hero overlay.
 */
package com.convx.music.ui.component

import com.convx.music.canvas.CanvasArtwork

internal data class CollectionCoverSource(
    val artworkUrl: String?,
    val animatedArtwork: CanvasArtwork? = null,
)

/** One source rule shared by Album, local Playlist, and community Playlist screens. */
internal fun resolveCollectionCoverSource(
    isAlbum: Boolean,
    albumArtworkUrl: String? = null,
    animatedAlbumArtwork: CanvasArtwork? = null,
    /** Official YouTube Music genre/mood/editorial cover, when present. */
    providerPlaylistCoverUrl: String? = null,
    customPlaylistCoverUrl: String? = null,
    firstSongCoverUrl: String? = null,
    isCommunityPlaylist: Boolean = false,
    fallbackPlaylistCoverUrl: String? = null,
): CollectionCoverSource = when {
    isAlbum -> CollectionCoverSource(
        artworkUrl = albumArtworkUrl,
        animatedArtwork = animatedAlbumArtwork,
    )
    isCommunityPlaylist -> CollectionCoverSource(
        // YouTube Music's own editorial/genre/mood cover is authoritative.
        artworkUrl = providerPlaylistCoverUrl ?: fallbackPlaylistCoverUrl ?: firstSongCoverUrl,
    )
    else -> CollectionCoverSource(
        // User cover first, then first song only when no playlist cover exists.
        artworkUrl = customPlaylistCoverUrl ?: firstSongCoverUrl ?: fallbackPlaylistCoverUrl,
    )
}
