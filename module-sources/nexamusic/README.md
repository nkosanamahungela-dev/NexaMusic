# NexaMusic 8SPINE Module

This directory contains the distributable NexaMusic module and its source index.

## Add the source

Publish this directory on an HTTPS static host. In NexaMusic, open **Module Sources → Add Source** and paste the public URL to `index.json`.

The index follows the category-based format consumed by this repository's `spine` module loader. The module download path is relative to the index URL.

## Configure the provider API

The module is an adapter, not a music catalogue or audio host. Configure the API base URL and endpoint paths in the module settings after installation.

Expected search endpoint:

`GET /search?q={query}&limit={limit}`

It may return an array or an object containing `tracks`, `results`, `items`, or `data`. Each item should include an ID, title, artist, and optional album/artwork/duration fields.

Expected stream endpoint:

`GET /stream/{trackId}?quality=LOSSLESS`

It must return JSON containing a direct HTTPS audio URL in `streamUrl`, `url`, `directUrl`, or `audioUrl`, plus audio metadata. For a LOSSLESS request, metadata must explicitly identify FLAC and report at least 16-bit and 44,100 Hz.

Example stream response:

```json
{
  "streamUrl": "https://media.example.org/audio/track.flac",
  "track": {
    "codec": "FLAC",
    "mimeType": "audio/flac",
    "bitDepth": 16,
    "sampleRate": 44100
  }
}
```

The example URL is illustrative only. No provider is bundled or implied.

## Lossless integrity

The module rejects a LOSSLESS response when the provider does not report FLAC, at least 16-bit depth, and at least 44.1 kHz. This prevents the module from *claiming* CD lossless based only on a quality label.

The module cannot independently prove the bytes behind a remote URL are lossless; the provider's metadata must be trustworthy, and the playback pipeline should inspect the actual media format when it opens the stream. A provider that transcodes or mislabels audio can still supply false metadata.

## Runtime compatibility

The JavaScript exports `searchTracks` and `getTrackStreamUrl` through `module.exports`, which is what the current NexaMusic QuickJS-based `spine` runtime loads. The public 8SPINE docs describe the same method contract, while the app's runtime-specific export wrapper is CommonJS-style.
