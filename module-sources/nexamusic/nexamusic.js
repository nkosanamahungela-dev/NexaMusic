/*
 * NexaMusic 8SPINE URL module retired.
 *
 * TIDAL's official Android SDK owns authentication and playback internally;
 * it does not expose a public stream URL for an 8SPINE URL resolver.
 * The previous Jamendo adapter has been removed.
 */
throw new Error(
  "[NexaMusic] This legacy 8SPINE URL module is retired. " +
  "NexaMusic is moving to TIDAL's official native Android Player SDK."
);
