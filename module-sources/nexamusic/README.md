# NexaMusic — 8SPINE Module

Version: 0.1.0 (testing)
Engine target: 8SPINE Module Engine v2.4.0-alpha
Provider adapter: Jamendo
Backend: https://nexamusic-stream-backend.onrender.com

## Architecture

The 8SPINE module calls the NexaMusic backend for search, artist tracks, and
fresh stream resolution. The backend calls Jamendo and proxies audio with
HTTP Range support. The module no longer calls Jamendo directly.

## Backend routes

- `GET /health`
- `GET /v1/search?q=...`
- `GET /v1/artists/:id/tracks`
- `GET /v1/resolve/:trackId?quality=LOSSLESS|HIGH|LOW`
- `GET|HEAD /v1/stream/:trackId?quality=LOSSLESS|HIGH|LOW`

## Public module source

- JSON: https://nexamusic-module-source.onrender.com/index.json
- JS: https://nexamusic-module-source.onrender.com/nexamusic.js

Jamendo's FLAC option does not establish verified bit depth or sample rate.
Tidal integration is not enabled because NexaMusic does not have authorized
Tidal API access. The module version remains 0.1.0 while testing.
