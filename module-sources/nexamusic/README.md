# NexaMusic — 8SPINE Module

Version: 0.1.1  
Engine target: 8SPINE Module Engine v2.4.0-alpha

## Public source

Add this URL in **Module Sources → Add Source**:

`https://nexamusic-module-source.onrender.com/index.json`

The index uses the `category:music` catalogue format and its `download` field points to the hosted JavaScript module.

## Required provider integration

This repository does not include a music catalogue or an audio-stream provider. Before this module can search or play tracks, set `API_BASE_URL` near the top of `nexamusic.js` to an HTTPS API that you own or are authorized to use.

The module expects:

- `GET /search?q={query}&limit={limit}` → `{ tracks: [...], total: number }`
- `GET /stream/{trackId}?quality=LOSSLESS|HIGH|LOW` → `{ streamUrl, track }`

Each search track should provide `id`, `title`, and `artist`. Optional fields include `album`, `duration`, `albumCover`, and `isrc`.

For LOSSLESS, the stream response must explicitly identify FLAC and report at least 16-bit / 44,100 Hz. Otherwise the module rejects it. Metadata checks are a guard against false labels, not cryptographic proof of the audio bytes; the actual stream should also be inspected by the playback pipeline.

## 8SPINE contract

The script returns the module object directly, as required by the 8SPINE loader:

- `id`, `name`, `version`, `labels`
- `searchTracks(query, limit)`
- `getTrackStreamUrl(id, quality)`

No CommonJS `module.exports` wrapper is used.
