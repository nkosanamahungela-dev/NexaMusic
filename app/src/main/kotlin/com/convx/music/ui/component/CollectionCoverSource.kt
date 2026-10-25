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
        artworkUrl = firstSongCoverUrl ?: fallbackPlaylistCoverUrl,
    )
    else -> CollectionCoverSource(
        artworkUrl = customPlaylistCoverUrl ?: firstSongCoverUrl ?: fallbackPlaylistCoverUrl,
    )
}
