# NexaMusic — 8SPINE Module

Version: 0.2.0  
Engine target: 8SPINE Module Engine v2.4.0-alpha  
Provider: Jamendo

## Public source

Add this URL in **Module Sources → Add Source**:

`https://nexamusic-module-source.onrender.com/index.json`

## Provider setup required

This module uses Jamendo's official public catalogue API for track search and stream URL resolution.

Before publishing for general use:

1. Create an application in the Jamendo Developer Portal: https://devportal.jamendo.com/
2. Copy its **Client ID**.
3. Replace `YOUR_JAMENDO_CLIENT_ID` in `nexamusic.js` with that Client ID.
4. Keep any Client Secret private. This read-only module does not need a Client Secret or OAuth flow.

Jamendo documents a shared Client ID for read-API testing only. Do not use that testing ID in a public release.

## Search and playback

- Search uses Jamendo `GET /v3.0/tracks/`.
- Results include title, artist, album, duration, artwork and available licence metadata.
- Playback resolves the selected track through Jamendo's track endpoint and requests the FLAC audio format by default.
- HIGH requests MP3 VBR; LOW requests MP3 96 kbps.

The module does not claim CD-quality (16-bit / 44.1 kHz). Jamendo's track response documents the requested FLAC format but does not provide verified bit-depth and sample-rate metadata in the fields used here. FLAC format alone is not proof of CD-quality source audio.

Respect each track's licence and Jamendo's API terms when using, caching, displaying, or distributing music.

## 8SPINE contract

The module returns the standard object directly:

- `id`, `name`, `version`, `labels`
- `searchTracks(query, limit)`
- `getTrackStreamUrl(id, quality)`
