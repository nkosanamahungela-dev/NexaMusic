# TIDAL SDK integration (development)

NexaMusic now has a development integration point for TIDAL's official Android Player SDK.

## Credentials

For local debug builds only, add these entries to the ignored root `local.properties` file:

```properties
tidal.clientid=YOUR_TIDAL_CLIENT_ID
tidal.clientsecret=YOUR_TIDAL_CLIENT_SECRET
```

Do not commit `local.properties`, publish the client secret, or include it in a release APK. NexaMusic's Gradle configuration injects the client secret into debug builds only; release builds receive an empty value.

## Current implementation

- Uses the official `com.tidal.sdk:player:0.0.74` Android SDK.
- Uses the SDK's Auth and EventProducer dependencies.
- Creates the SDK player from the application process when debug credentials are present.
- Exposes playback actions and SDK playback events through `TidalSdkManager`.
- Requests TIDAL's `LOSSLESS` quality by default and supports the SDK's `HI_RES_LOSSLESS` option.
- Does not extract, proxy, or manufacture TIDAL stream URLs.

## Access limitation

TIDAL's published Android SDK documentation states that the publicly supported Client Credentials flow provides 30-second previews. Full-length playback through Authorization Code or Device Login is currently restricted to internally developed TIDAL applications. This code does not bypass that restriction.

NexaMusic must receive explicit TIDAL approval for an eligible full-length playback flow before CD-quality or Hi-Res full-track playback can be enabled. Setting the requested SDK quality to `LOSSLESS` does not itself grant that entitlement.

## Integration status

This is the native SDK bootstrap and playback-controller foundation. The existing NexaMusic Media3 session and Now Playing UI have not yet been switched to the TIDAL SDK engine. That requires a deliberate playback-session/UI adapter; the TIDAL SDK does not expose a raw stream URL for the existing 8SPINE resolver.

Keep the old 8SPINE Jamendo stream source disabled. TIDAL native playback and 8SPINE URL modules are separate integration paths.
