# NexaMusic — 8SPINE Module

Version: 0.3.0  
Engine target: 8SPINE Module Engine v2.4.0-alpha  
Provider: Jamendo

## Public source

Add this URL in **Module Sources → Add Source**:

`https://nexamusic-module-source.onrender.com/index.json`

NexaMusic reads the source index and loads the module from its published HTTPS download URL.

## Provider configuration

The NexaMusic Jamendo Client ID is configured in `nexamusic.js`. The module uses Jamendo's read-only public API and does not require a Client Secret or OAuth flow.

## Search and artist browsing

- General music search uses Jamendo `GET /v3.0/tracks/`.
- Artist-specific tracks use Jamendo `GET /v3.0/artists/tracks/`.
- Search returns title, artist, album, duration, artwork, licence metadata, track-page URL when supplied, and the API's download-allowed flag.
- Search requests include `fullcount=true` and prefer `headers.results_fullcount` when returned, falling back to `headers.results_count`.

## Playback

- Playback resolves a selected track through `GET /v3.0/tracks/?id=...`.
- LOSSLESS requests Jamendo's `flac` format.
- HIGH requests `mp32` (MP3 VBR).
- LOW requests `mp31` (MP3 96 kbps).
- The module does not claim verified CD-quality (16-bit / 44.1 kHz), because the response fields used here do not verify bit depth and sample rate.

## 8SPINE contract

The module returns the standard object directly:

- `id`, `name`, `version`, `labels`
- `searchTracks(query, limit)`
- `getArtistTracks(artistId, limit)`
- `getTrackStreamUrl(id, quality)`

