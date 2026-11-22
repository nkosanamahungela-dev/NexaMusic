# NexaMusic legacy stream backend

Version 0.1.0 — retired.

The previous Jamendo catalogue adapter and audio proxy have been removed.
This service remains temporarily available only to return a health response
and an HTTP 410 response for legacy stream routes.

NexaMusic is moving to TIDAL's official native Android Player SDK. The SDK
handles authentication, entitlement checks, stream resolution, playback, and
session reporting inside the Android application. The native SDK does not
supply public stream URLs for an 8SPINE URL module.

See the TIDAL SDK integration document in docs/.

Routes:
- GET /health — confirms that the legacy service is retired.
- All previous /v1 streaming routes return 410 Gone.

No provider credentials are required.
