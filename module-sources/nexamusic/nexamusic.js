/*
 * NexaMusic — 8SPINE-compatible module
 *
 * Runtime contract follows the NexaMusic spine ModuleManager:
 * module.exports.searchTracks(query, limit, context)
 * module.exports.getTrackStreamUrl(id, quality, context)
 *
 * Configure the API URL and endpoint paths in module settings.
 * This adapter deliberately refuses to call a stream LOSSLESS unless the
 * provider returns explicit FLAC + bit-depth + sample-rate metadata.
 */
(function () {
  "use strict";

  function readSettings(context) {
    var parsed = context || {};
    if (typeof parsed === "string") {
      try { parsed = JSON.parse(parsed); } catch (_) { parsed = {}; }
    }
    var values = (parsed && parsed.settings) || {};
    return function (key, fallback) {
      var entry = values[key];
      if (entry && typeof entry === "object" && Object.prototype.hasOwnProperty.call(entry, "value")) {
        return String(entry.value);
      }
      return entry == null ? fallback : String(entry);
    };
  }

  function requiredUrl(value, label) {
    if (!value) throw new Error(label + " is not configured in NexaMusic module settings.");
    if (!/^https:\/\//i.test(value)) throw new Error(label + " must use HTTPS.");
    return value;
  }

  function joinUrl(base, path) {
    return base.replace(/\/+$/, "") + "/" + String(path || "").replace(/^\/+/, "");
  }

  async function requestJson(url, options) {
    var response = await fetch(url, options || {});
    if (!response || !response.ok) {
      throw new Error("NexaMusic API request failed (HTTP " + (response ? response.status : "unknown") + ").");
    }
    return await response.json();
  }

  function firstDefined(object, keys, fallback) {
    for (var i = 0; i < keys.length; i++) {
      var value = object && object[keys[i]];
      if (value !== undefined && value !== null && value !== "") return value;
    }
    return fallback;
  }

  function mapTrack(item) {
    return {
      id: String(firstDefined(item, ["id", "trackId", "track_id", "uuid"], "")),
      title: String(firstDefined(item, ["title", "name", "track_name"], "Unknown title")),
      artist: String(firstDefined(item, ["artist", "artistName", "artist_name"], "Unknown artist")),
      artistId: firstDefined(item, ["artistId", "artist_id"], null),
      album: String(firstDefined(item, ["album", "albumName", "album_name"], "")),
      albumId: firstDefined(item, ["albumId", "album_id"], null),
      albumCover: firstDefined(item, ["albumCover", "cover", "coverUrl", "artwork"], null),
      duration: Number(firstDefined(item, ["duration", "durationSeconds", "length"], 0)) || 0,
      audioQuality: String(firstDefined(item, ["audioQuality", "quality"], "")),
      format: String(firstDefined(item, ["format", "codec"], "")).toUpperCase(),
      availableQualities: Array.isArray(item.availableQualities) ? item.availableQualities : []
    };
  }

  var moduleApi = {
    id: "nexamusic",
    name: "NexaMusic",
    version: "0.1.0",
    labels: ["LOSSLESS", "FLAC", "CD QUALITY"],
    noStreamCache: true,

    settings: {
      apiBaseUrl: {
        type: "text",
        label: "API base URL",
        description: "HTTPS base URL for the music API that supplies search results and direct audio streams.",
        defaultValue: ""
      },
      searchPath: {
        type: "text",
        label: "Search endpoint",
        description: "Path appended to the API base URL. The endpoint receives ?q= and &limit=.",
        defaultValue: "search"
      },
      streamPath: {
        type: "text",
        label: "Stream endpoint",
        description: "Path appended to the API base URL. The track ID is appended as a URL-encoded path segment.",
        defaultValue: "stream"
      },
      apiKey: {
        type: "text",
        label: "API key (optional)",
        description: "Optional bearer token for the configured API.",
        defaultValue: ""
      }
    },

    searchTracks: async function (query, limit, context) {
      var setting = readSettings(context);
      var base = requiredUrl(setting("apiBaseUrl", ""), "API base URL");
      var path = setting("searchPath", "search");
      var max = Math.max(1, Math.min(Number(limit) || 30, 100));
      var url = joinUrl(base, path) + "?q=" + encodeURIComponent(String(query || "")) + "&limit=" + max;
      var headers = {};
      var key = setting("apiKey", "");
      if (key) headers.Authorization = "Bearer " + key;

      var payload = await requestJson(url, { method: "GET", headers: headers });
      var items = Array.isArray(payload) ? payload :
        firstDefined(payload, ["tracks", "results", "items", "data"], []);
      if (!Array.isArray(items)) throw new Error("Search API response must contain a tracks/results/items array.");

      var tracks = items.map(mapTrack).filter(function (track) { return !!track.id; }).slice(0, max);
      return { tracks: tracks, total: Number(firstDefined(payload, ["total", "count"], tracks.length)) || tracks.length };
    },

    getTrackStreamUrl: async function (id, quality, context) {
      var setting = readSettings(context);
      var base = requiredUrl(setting("apiBaseUrl", ""), "API base URL");
      var path = setting("streamPath", "stream");
      var requested = String(quality || "LOSSLESS").toUpperCase();
      var url = joinUrl(base, path) + "/" + encodeURIComponent(String(id));
      var headers = {};
      var key = setting("apiKey", "");
      if (key) headers.Authorization = "Bearer " + key;

      var payload = await requestJson(url + "?quality=" + encodeURIComponent(requested), {
        method: "GET",
        headers: headers
      });

      var streamUrl = firstDefined(payload, ["streamUrl", "url", "directUrl", "audioUrl"], null);
      var trackMeta = firstDefined(payload, ["track", "metadata", "audio"], payload);
      if (!streamUrl || !/^https:\/\//i.test(String(streamUrl))) {
        throw new Error("The API did not return a valid HTTPS direct audio URL.");
      }

      var codec = String(firstDefined(trackMeta, ["codec", "format", "audioCodec"], "")).toUpperCase();
      var mimeType = String(firstDefined(trackMeta, ["mimeType", "mime_type", "contentType"], "")).toLowerCase();
      var bitDepth = Number(firstDefined(trackMeta, ["bitDepth", "bit_depth", "bitsPerSample"], 0)) || 0;
      var sampleRate = Number(firstDefined(trackMeta, ["sampleRate", "sample_rate", "samplingRate"], 0)) || 0;
      var isFlac = codec === "FLAC" || mimeType === "audio/flac" || mimeType === "audio/x-flac";

      if (requested === "LOSSLESS") {
        if (!isFlac) throw new Error("LOSSLESS unavailable: the provider did not identify this stream as FLAC.");
        if (bitDepth < 16 || sampleRate < 44100) {
          throw new Error("LOSSLESS unavailable: stream metadata does not meet CD quality (at least 16-bit / 44.1 kHz).");
        }
      }

      var track = {
        id: String(id),
        audioQuality: requested === "LOSSLESS" ? "LOSSLESS" : String(firstDefined(trackMeta, ["audioQuality", "quality"], requested)),
        mimeType: mimeType || (isFlac ? "audio/flac" : null),
        format: codec || (isFlac ? "FLAC" : null),
        codec: codec || (isFlac ? "FLAC" : null),
        bitDepth: bitDepth || null,
        sampleRate: sampleRate || null,
        bitrate: Number(firstDefined(trackMeta, ["bitrate", "bit_rate"], 0)) || null
      };

      return {
        streamUrl: String(streamUrl),
        track: track,
        mimeType: track.mimeType,
        expiresAt: Number(firstDefined(payload, ["expiresAt", "expires_at"], 0)) || null
      };
    }
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = moduleApi;
  }
})();
