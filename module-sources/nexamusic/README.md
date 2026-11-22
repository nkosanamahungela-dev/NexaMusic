# NexaMusic — 8SPINE Module

Version: 0.4.0  
Engine target: 8SPINE Module Engine v2.4.0-alpha  
Provider: Jamendo

## Public source

Add this URL in **Module Sources → Add Source**:

`https://nexamusic-module-source.onrender.com/index.json`

NexaMusic reads the source index and loads the module from its published HTTPS download URL.

## Provider configuration

The NexaMusic Jamendo Client ID is configured in `nexamusic.js`. The module uses Jamendo's read-only public API.

## Search and artist browsing

- General music search uses Jamendo `GET /v3.0/tracks/`.
- Artist-specific tracks use Jamendo `GET /v3.0/artists/tracks/`.
- Search returns title, artist, album, duration, artwork, licence metadata, track-page URL when supplied, and the API's download-allowed flag.
- Search requests include `fullcount=true` and prefer `headers.results_fullcount` when returned, falling back to `headers.results_count`.

## Playback quality

- LOSSLESS requests the Jamendo `/v3.0/tracks/file/` redirect endpoint with `audioformat=flac` and `action=stream`.
- HIGH requests `audioformat=mp32` (MP3 VBR).
- LOW requests `audioformat=mp31` (MP3 96 kbps).
- Playback metadata reports LOSSLESS only when that quality was requested; the player must still confirm the actual decoded stream format. The module does not invent sample-rate or bit-depth values.

## 8SPINE contract

- `id`, `name`, `version`, `labels`
- `searchTracks(query, limit)`
- `getArtistTracks(artistId, limit)`
- `getTrackStreamUrl(id, quality)`
