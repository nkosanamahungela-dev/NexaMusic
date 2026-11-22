/*
 * NexaMusic TIDAL compatibility module
 * Version: 0.1.0 (testing)
 *
 * This module intentionally does not resolve or fabricate TIDAL audio URLs.
 * TIDAL's official Android Player SDK owns authenticated stream resolution.
 * Full-track playback must be performed through that native SDK integration.
 */

(function (root) {
  "use strict";

  var MODULE_ID = "tidal-official-hifi";
  var VERSION = "0.1.0";

  function nativePlaybackRequired(method) {
    var error = new Error(
      "[NexaMusic TIDAL] " + method +
      " is unavailable in the JavaScript URL-module runtime. " +
      "Connect TIDAL and use NexaMusic's official native TIDAL Player integration."
    );
    error.code = "TIDAL_NATIVE_PLAYBACK_REQUIRED";
    throw error;
  }

  var moduleDefinition = {
    id: MODULE_ID,
    name: "TIDAL (Official Native Player)",
    author: "NexaMusic",
    version: VERSION,
    description:
      "Testing compatibility module. TIDAL account playback and quality " +
      "selection are handled by the official native Android Player SDK. " +
      "This module does not expose or fabricate signed stream URLs.",
    labels: ["TIDAL", "LOSSLESS", "HI-RES", "ATMOS", "ISRC"],
    noStreamCache: true,
    settings: {
      quality: {
        type: "select",
        label: "Audio quality",
        default: "CD",
        options: [
          { value: "HI_RES", label: "Hi-Res Lossless" },
          { value: "HI_RES_DOLBY", label: "Hi-Res + Dolby Atmos" },
          { value: "CD", label: "CD Lossless" },
          { value: "HIGH", label: "High" },
          { value: "LOW", label: "Low" }
        ]
      }
    },

    /*
     * These methods deliberately fail closed. The 8SPINE JavaScript runtime
     * has no documented, authorised bridge to TidalSdkManager, and the public
     * native SDK does not return signed URLs to JavaScript.
     */
    searchTracks: function () {
      return nativePlaybackRequired("Catalog search");
    },
    getTrackStreamUrl: function () {
      return nativePlaybackRequired("Stream URL resolution");
    },
    getTrackDownloadUrl: function () {
      return nativePlaybackRequired("Track downloads");
    },
    getAlbum: function () {
      return nativePlaybackRequired("Album lookup");
    },
    getArtist: function () {
      return nativePlaybackRequired("Artist lookup");
    }
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = moduleDefinition;
  } else if (root) {
    root.NexaMusicTidalModule = moduleDefinition;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);
