# NexaMusic — 8SPINE Module

Version: 0.1.0 (testing)
Engine target: 8SPINE Module Engine v2.4.0-alpha
Provider: Jamendo

## Public source

https://nexamusic-module-source.onrender.com/index.json

## Playback

- Search uses Jamendo GET /v3.0/tracks/ with audioformat=flac.
- Stream resolution requests the selected format from the same tracks endpoint and uses its returned audio field.
- The module validates the format query parameter in the returned audio URL. A missing or mismatched format throws an error rather than reporting a successful stream.
- Track IDs are namespaced as jamendo:<id> to avoid collisions with other modules and stale cross-provider cache entries.
- LOSSLESS requests flac; HIGH requests mp32; LOW requests mp31.
- Jamendo does not provide verified bit-depth/sample-rate metadata in the response, so the module does not claim CD-quality from the API request alone.

## 8SPINE contract

- id, name, version, labels
- searchTracks(query, limit)
- getArtistTracks(artistId, limit)
- getTrackStreamUrl(id, quality)
