# NexaMusic TIDAL integration — development

NexaMusic is integrating TIDAL using its official Android Player SDK, following
the native SDK lifecycle. This is intentionally separate from the 8SPINE
JavaScript URL-module contract used by the friend's private backend.

## Local configuration

Add the following to the ignored root `local.properties` file:

```properties
tidal.clientid=YOUR_TIDAL_CLIENT_ID
tidal.clientredirecturi=nexamusic://tidal-auth/callback
tidal.clientscopes=YOUR_APPROVED_SCOPES
```

Register the exact redirect URI in the TIDAL Developer Dashboard. Only use
scopes that TIDAL has approved for the NexaMusic application. No client secret
is embedded in the Android APK; the SDK's Authorization Code flow uses PKCE.

## Current implementation

- Official `com.tidal.sdk:player:0.0.39` dependency.
- Official Auth and EventProducer modules supplied transitively by Player.
- `TidalSdkManager` initializes Auth and Player, starts Authorization Code
  + PKCE login, completes the redirect callback, logs out, exposes player
  controls/events, and maps the friend's `HI_RES`, `HI_RES_DOLBY`, `CD`,
  `HIGH`, and `LOW` quality choices to official SDK options.
- MainActivity receives `nexamusic://tidal-auth/callback` and passes the
  untouched query component to TIDAL Auth's `finalizeLogin`.
- Playback quality requests include `LOSSLESS` (up to 1411 kbps FLAC) and
  `HI_RES_LOSSLESS`, as defined by TIDAL's SDK.

## Friend-style 8SPINE compatibility

The friend's module contract is a JavaScript URL-provider interface:
`searchTracks`, `getTrackStreamUrl`, `getTrackDownloadUrl`, `getAlbum`,
and `getArtist`. Its backend returns signed stream URLs and stream metadata.

TIDAL's official Android SDK owns stream resolution and playback internally.
It does not expose signed audio URLs for an 8SPINE JavaScript resolver. A
JavaScript module cannot call the native Player without an explicitly supported
native bridge in the 8SPINE runtime. NexaMusic must not fake a `streamUrl` or
claim that a native SDK request is an 8SPINE URL module.

The native SDK integration is therefore the authorised playback path. We can
preserve the friend-style module metadata, quality labels, search/album/artist
metadata conventions where supported, but actual playback must stay native.

## Access and verification

The public SDK README documents Client Credentials as the publicly supported
flow for 30-second previews. Its Auth module documents Authorization Code
login, while Device Login is marked for TIDAL internal apps only. Whether
NexaMusic's registered app is permitted full-length playback is controlled by
TIDAL's developer approval and the account's entitlement.

Selecting `LOSSLESS` requests that quality; it does not prove a full track was
delivered losslessly. Before claiming CD quality, verify successful full-track
playback and the actual stream quality reported by TIDAL's SDK.

## Remaining work

- Connect `TidalSdkManager` to NexaMusic's existing MediaSession and Now Playing
  controls, without replacing the current player for other providers.
- Add the TIDAL login entry point in app settings.
- Run a complete Android Gradle build and device playback test.
