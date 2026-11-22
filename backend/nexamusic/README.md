# NexaMusic Stream Backend

Version 0.1.0 — testing.

Node.js 20+ service for the NexaMusic 8SPINE module. It keeps Jamendo API calls
and audio stream resolution behind a dedicated backend and proxies byte-range
requests so compatible players can seek through audio.

## Routes

- `GET /health` — service health and capabilities.
- `GET /v1/search?q=...` — search Jamendo tracks.
- `GET /v1/artists/:id/tracks` — artist tracks.
- `GET /v1/resolve/:trackId?quality=LOSSLESS` — fresh stream resolution.
- `GET|HEAD /v1/stream/:trackId?quality=LOSSLESS` — audio proxy with Range support.

Quality values: `LOSSLESS` / `FLAC`, `HIGH`, `LOW`.

## Environment

- `PORT` — supplied by Render.
- `JAMENDO_CLIENT_ID` — optional; defaults to the public NexaMusic app Client ID.
- `PUBLIC_BASE_URL` — optional; set to the Render service URL if reverse-proxy host detection is unsuitable.

Jamendo's FLAC option does not establish verified bit depth or sample rate.
This service does not provide Tidal access or claim CD-quality master metadata.
