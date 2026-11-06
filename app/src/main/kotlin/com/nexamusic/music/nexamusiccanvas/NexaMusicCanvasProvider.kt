package com.nexamusic.music.nexamusiccanvas

import com.nexamusic.music.artistvideo.ArtistVideoCanvasProvider
import com.nexamusic.music.artistvideo.ArtistVideoResponse
import com.nexamusic.music.canvas.CanvasArtwork

object NexaMusicCanvasProvider {
    suspend fun getBySongArtist(
        song: String,
        artist: String,
        album: String? = null,
    ): CanvasArtwork? = ArtistVideoCanvasProvider.getBySongArtist(song, artist, album)
        ?.toCanvasArtwork(album)

    suspend fun getByAlbumArtist(album: String, artist: String): CanvasArtwork? =
        ArtistVideoCanvasProvider.getBySongArtist(song = album, artist = artist, album = album)
            ?.toCanvasArtwork(album)
}

private fun ArtistVideoResponse.toCanvasArtwork(albumName: String?): CanvasArtwork = CanvasArtwork(
    name = name,
    artist = artist,
    albumId = albumId,
    albumName = albumName,
    static = static,
    animated = animated,
    videoUrl = videoUrl,
)
