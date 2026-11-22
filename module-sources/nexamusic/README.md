# NexaMusic native TIDAL playback

Version: 0.1.0 (testing)

The previous Jamendo-backed 8SPINE URL module has been retired.

NexaMusic is moving to TIDAL's official Android Player SDK. That SDK manages
authentication, track entitlement, stream resolution, playback, and session
events inside the Android application. It does not provide a public stream URL
that can be returned from a JavaScript 8SPINE module.

For the native integration, see the TIDAL SDK integration document in docs/.

The old module index is intentionally empty so clients cannot accidentally
load the retired Jamendo adapter. Do not re-enable this entry as a TIDAL URL
module; native TIDAL playback must use the official SDK.
