# NexaMusic — 8SPINE Module

Version: 0.5.0  
Engine target: 8SPINE Module Engine v2.4.0-alpha  
Provider: Jamendo

## Public source

Add this URL in **Module Sources → Add Source**:

`https://nexamusic-module-source.onrender.com/index.json`

NexaMusic reads the source index and loads the module from its published HTTPS download URL.

## Provider configuration

The NexaMusic Jamendo Client ID is configured in `nexamusic.js`.

## Search and artist browsing

- General music search uses Jamendo `GET /v3.0/tracks/`.
- Artist-specific tracks use Jamendo `GET /v3.0/artists/tracks/`.
- Search returns title, artist, album, duration, artwork, licence metadata, track-page URL when supplied, and download-allowed metadata.
- Search requests include `fullcount=true`.

## Playback quality

- LOSSLESS requests `audioformat=flac` from Jamendo's `/v3.0/tracks/` method and uses the returned `results[0].audio` URL.
- HIGH requests `audioformat=mp32`.
- LOW requests `audioformat=mp31`.
- The module no longer constructs a `/tracks/file/` URL for playback. That endpoint is a redirect/download method and may reject tracks whose download permission is disabled.
- If Jamendo does not return a valid HTTPS audio URL in the requested format, the module throws a clear error rather than returning a fabricated stream URL.
- Quality metadata reflects the requested format; actual decoder output still needs verification in the app.

## 8SPINE contract

- `id`, `name`, `version`, `labels`
- `searchTracks(query, limit)`
- `getArtistTracks(artistId, limit)`
- `getTrackStreamUrl(id, quality)`
