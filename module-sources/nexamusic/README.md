# NexaMusic — 8SPINE Module

Version: 0.1.0 (testing)
Engine target: 8SPINE Module Engine v2.4.0-alpha
Provider: Jamendo

## Public source

https://nexamusic-module-source.onrender.com/index.json

## Playback

- Search uses Jamendo GET /v3.0/tracks/ and requests FLAC metadata.
- Playback resolves the selected track ID again through /v3.0/tracks/ and uses the returned `audio` stream URL.
- Quality is exposed through an 8SPINE selector: FLAC (LOSSLESS), MP3 VBR (HIGH), and MP3 96 kbps (LOW).
- The resolver accepts the 8SPINE quality argument and its third context argument, including `ctx.settings.quality.value`.
- The module sets `noStreamCache: true` so the host does not persist potentially stale provider stream URLs.
- The stream result includes `mimeType`, `streamType`, and playback track metadata.
- Track IDs are namespaced as `jamendo:<id>` to avoid collisions.
- Jamendo documents FLAC as an available `audioformat`. Its API response does not provide verified bit depth/sample rate, so this module does not claim 16-bit / 44.1 kHz CD quality.

## 8SPINE contract

- `searchTracks(query, limit)`
- `getArtistTracks(artistId, limit)`
- `getTrackStreamUrl(id, quality, ctx)`
- `settings.quality` selector with LOSSLESS, HIGH, LOW values
