# NexaMusic TIDAL 8SPINE resolver contract (0.1.0)

This document defines the API expected by the NexaMusic JavaScript module. It is
an integration contract for a resolver that NexaMusic controls and is authorised
to operate. It is **not** a description of TIDAL's private API.

The module must be configured with an HTTPS base URL and, if required, a bearer
token. It will not contact any resolver until a base URL is configured.

## Required endpoints

All endpoints return JSON and use UTF-8.

### Search

`GET /v1/catalog/search?q={query}&limit=30&quality={tier}&atmos={true|false}`

Response may be an array of tracks or an object containing `tracks`,
`results`, or `items`.

Each track should provide:
- `id` (TIDAL catalog track ID)
- `title`, `artist`, `album`, `duration`
- `artwork` or `coverUrl`
- `isrc`
- optional `regularTrackId` and `atmosTrackId`

### Stream and download

`GET /v1/tracks/{trackId}/stream?quality={tier}&atmos={true|false}`

`GET /v1/tracks/{trackId}/download?quality={tier}&atmos={true|false}`

Response:
```json
{
  "url": "https://authorised-media-url.example/...",
  "mimeType": "audio/flac",
  "codec": "FLAC",
  "quality": "CD Lossless",
  "atmos": false,
  "isrc": "EXAMPLE1234567",
  "expiresAt": "2026-11-22T18:00:00Z"
}
```

The example URL is illustrative only. The resolver must return a real URL only
when it has obtained it through an authorised TIDAL integration. The module
requires HTTPS media URLs and rejects a Dolby Atmos response when a non-Atmos
tier was selected. It selects the regular track ID for every non-Atmos tier and
uses the Atmos sibling ID only for `HI_RES_DOLBY`.

### Album and artist

- `GET /v1/albums/{albumId}` — album metadata and optional `tracks` array.
- `GET /v1/artists/{artistId}` — artist metadata.

### Errors

An error response may use:
```json
{ "error": { "code": "ACCESS_NOT_APPROVED", "message": "Playback access is not enabled." } }
```

The module surfaces the backend error code and message. The resolver must
enforce account entitlement, playback rights, territory restrictions, quality
availability, token expiry, and download permissions server-side.

## Quality policy

| Module setting | Backend tier | Atmos |
| --- | --- | --- |
| Hi-Res Lossless | `HI_RES` | Never |
| Hi-Res + Dolby Atmos | `HI_RES_DOLBY` | Only when explicitly selected |
| CD Lossless | `CD` | Never |
| High | `HIGH` | Never |
| Low | `LOW` | Never |

A backend must not silently substitute Atmos for a non-Atmos request or claim a
quality that was not actually delivered. Signed media URLs should be short-lived
and must not be logged or exposed to other users.

## Current status

The JavaScript adapter implements the 8SPINE-facing method layer, metadata
normalisation, short-lived search/stream caches, in-flight request deduplication,
quality selection, regular/Atmos sibling selection, and fail-closed URL checks.

**Actual TIDAL search, playback, and downloads remain unavailable until a
TIDAL-authorised resolver implementing this contract is deployed and configured.**
The official Android Player SDK does not provide signed URLs to this JavaScript
module, so it cannot be used as a URL resolver.
