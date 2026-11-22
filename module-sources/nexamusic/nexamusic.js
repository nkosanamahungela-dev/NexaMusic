/*
 * NexaMusic TIDAL — 8SPINE URL-provider adapter
 * Version: 0.1.0 (testing)
 *
 * This module talks ONLY to a NexaMusic-controlled, TIDAL-authorised resolver.
 * It does not call TIDAL's private APIs or return fabricated audio URLs.
 *
 * Required backend contract: docs/TIDAL_8SPINE_BACKEND.md
 */
(function (root) {
  "use strict";

  var MODULE_ID = "tidal-official-hifi";
  var VERSION = "0.1.0";
  var SEARCH_TTL = 60000;
  var STREAM_TTL = 30000;
  var searchCache = {};
  var streamCache = {};
  var inflight = {};

  var QUALITY = {
    HI_RES: { label: "Hi-Res Lossless", backend: "HI_RES", atmos: false },
    HI_RES_DOLBY: { label: "Hi-Res + Dolby Atmos", backend: "HI_RES_DOLBY", atmos: true },
    CD: { label: "CD Lossless", backend: "CD", atmos: false },
    HIGH: { label: "High", backend: "HIGH", atmos: false },
    LOW: { label: "Low", backend: "LOW", atmos: false }
  };

  function fail(code, message) {
    var error = new Error("[NexaMusic TIDAL] " + message);
    error.code = code;
    throw error;
  }

  function settings() {
    var s = moduleDefinition.settings || {};
    return {
      baseUrl: String(s.apiBaseUrl && s.apiBaseUrl.value || s.apiBaseUrl || "").replace(/\/+$/, ""),
      token: String(s.apiToken && s.apiToken.value || s.apiToken || ""),
      quality: String(s.quality && s.quality.value || s.quality || "CD")
    };
  }

  function selectedQuality(override) {
    var s = settings();
    var key = String(override || s.quality || "CD").toUpperCase();
    if (!QUALITY[key]) key = "CD";
    return { key: key, value: QUALITY[key] };
  }

  function requireBaseUrl() {
    var base = settings().baseUrl;
    if (!base) {
      fail(
        "TIDAL_RESOLVER_NOT_CONFIGURED",
        "Set the NexaMusic-authorised resolver URL in this module's settings. " +
        "Search and playback are unavailable until that service is configured."
      );
    }
    if (!/^https:\/\//i.test(base)) {
      fail("TIDAL_INVALID_RESOLVER_URL", "The resolver URL must use HTTPS.");
    }
    return base;
  }

  function encode(value) {
    return encodeURIComponent(value == null ? "" : String(value));
  }

  function request(path, params) {
    var base = requireBaseUrl();
    var query = [];
    var key;
    params = params || {};
    for (key in params) {
      if (Object.prototype.hasOwnProperty.call(params, key) && params[key] != null) {
        query.push(encode(key) + "=" + encode(params[key]));
      }
    }

    var url = base + path + (query.length ? "?" + query.join("&") : "");
    var headers = { Accept: "application/json" };
    var token = settings().token;
    if (token) headers.Authorization = "Bearer " + token;

    if (typeof fetch !== "function") {
      fail("TIDAL_FETCH_UNAVAILABLE", "This 8SPINE runtime does not provide fetch().");
    }

    return fetch(url, { method: "GET", headers: headers })
      .then(function (response) {
        if (!response || !response.ok) {
          return response && response.text ? response.text().then(function (body) {
            fail("TIDAL_BACKEND_HTTP_" + (response.status || 0),
              "Resolver request failed (" + (response.status || "unknown") + "): " +
              String(body || "").slice(0, 240));
          }) : fail("TIDAL_BACKEND_ERROR", "Resolver request failed.");
        }
        return response.json();
      })
      .then(function (payload) {
        if (payload && payload.error) {
          fail(payload.error.code || "TIDAL_BACKEND_ERROR",
            payload.error.message || "The resolver rejected the request.");
        }
        return payload;
      });
  }

  function trackId(track) {
    if (track == null) return "";
    if (typeof track === "string" || typeof track === "number") return String(track);
    return String(track.tidalId || track.id || track.trackId || "");
  }

  function normalizeTrack(raw) {
    raw = raw || {};
    var id = trackId(raw);
    return {
      id: id,
      tidalId: id,
      title: raw.title || raw.name || "Unknown title",
      name: raw.title || raw.name || "Unknown title",
      artist: raw.artist || raw.artistName || "",
      artists: raw.artists || (raw.artist ? [{ name: raw.artist }] : []),
      album: raw.album || raw.albumTitle || "",
      albumId: raw.albumId || (raw.album && raw.album.id) || "",
      duration: Number(raw.duration || raw.durationSeconds || 0),
      artwork: raw.artwork || raw.cover || raw.coverUrl || raw.image || "",
      cover: raw.cover || raw.artwork || raw.coverUrl || raw.image || "",
      isrc: raw.isrc || raw.ISRC || "",
      explicit: !!raw.explicit,
      regularTrackId: raw.regularTrackId || raw.standardId || id,
      atmosTrackId: raw.atmosTrackId || raw.dolbyTrackId || "",
      streamType: raw.streamType || "",
      quality: raw.quality || "",
      source: "tidal"
    };
  }

  function cacheRead(cache, key) {
    var item = cache[key];
    if (!item || item.expiresAt <= Date.now()) {
      delete cache[key];
      return null;
    }
    return item.value;
  }

  function cacheWrite(cache, key, value, ttl) {
    cache[key] = { value: value, expiresAt: Date.now() + ttl };
    return value;
  }

  function searchTracks(query, options) {
    options = options || {};
    var q = String(query || "").trim();
    if (!q) return Promise.resolve([]);
    var selected = selectedQuality(options.quality);
    var cacheKey = q.toLowerCase() + "|" + selected.key;
    var cached = cacheRead(searchCache, cacheKey);
    if (cached) return Promise.resolve(cached);

    return request("/v1/catalog/search", {
      q: q,
      limit: options.limit || 30,
      quality: selected.value.backend,
      atmos: selected.value.atmos ? "true" : "false"
    }).then(function (payload) {
      var rows = Array.isArray(payload) ? payload :
        (payload.tracks || payload.results || payload.items || []);
      var tracks = rows.map(normalizeTrack).filter(function (track) { return !!track.id; });
      return cacheWrite(searchCache, cacheKey, tracks, SEARCH_TTL);
    });
  }

  function resolveTrack(track, mode, options) {
    options = options || {};
    var id = trackId(track);
    if (!id) return Promise.reject(new Error("[NexaMusic TIDAL] A valid TIDAL track ID is required."));
    var selected = selectedQuality(options.quality);
    var useAtmos = selected.value.atmos === true;

    // Atmos is opt-in only. Non-Atmos qualities always use the regular track ID.
    var resolvedId = id;
    if (track && typeof track === "object") {
      if (useAtmos && track.atmosTrackId) resolvedId = String(track.atmosTrackId);
      if (!useAtmos && track.regularTrackId) resolvedId = String(track.regularTrackId);
    }

    var cacheKey = mode + "|" + resolvedId + "|" + selected.key;
    var cached = cacheRead(streamCache, cacheKey);
    if (cached) return Promise.resolve(cached);
    if (inflight[cacheKey]) return inflight[cacheKey];

    var endpoint = mode === "download" ? "download" : "stream";
    inflight[cacheKey] = request("/v1/tracks/" + encode(resolvedId) + "/" + endpoint, {
      quality: selected.value.backend,
      atmos: useAtmos ? "true" : "false"
    }).then(function (payload) {
      var url = payload.url || payload.streamUrl || payload.downloadUrl;
      if (!url || !/^https:\/\//i.test(String(url))) {
        fail("TIDAL_INVALID_MEDIA_URL", "The authorised resolver did not return a valid HTTPS media URL.");
      }
      if (payload.atmos === true && !useAtmos) {
        fail("TIDAL_QUALITY_MISMATCH", "The resolver returned Atmos for a non-Atmos quality request.");
      }
      if (payload.atmos === false && useAtmos) {
        fail("TIDAL_QUALITY_UNAVAILABLE", "The resolver did not provide the requested Dolby Atmos rendition.");
      }
      var result = {
        url: url,
        streamUrl: mode === "stream" ? url : undefined,
        downloadUrl: mode === "download" ? url : undefined,
        fileName: payload.fileName || "",
        mimeType: payload.mimeType || "audio/flac",
        codec: payload.codec || "",
        streamType: payload.streamType || "",
        quality: payload.quality || selected.value.label,
        qualityKey: selected.key,
        isrc: payload.isrc || (track && track.isrc) || "",
        expiresAt: payload.expiresAt || payload.expiry || null,
        atmos: useAtmos
      };
      return cacheWrite(streamCache, cacheKey, result, STREAM_TTL);
    }).then(function (result) {
      delete inflight[cacheKey];
      return result;
    }, function (error) {
      delete inflight[cacheKey];
      throw error;
    });

    return inflight[cacheKey];
  }

  function getTrackStreamUrl(track, options) {
    return resolveTrack(track, "stream", options);
  }

  function getTrackDownloadUrl(track, options) {
    return resolveTrack(track, "download", options);
  }

  function getAlbum(albumId) {
    if (!albumId) return Promise.reject(new Error("[NexaMusic TIDAL] Album ID is required."));
    return request("/v1/albums/" + encode(albumId)).then(function (payload) {
      var album = payload.album || payload;
      album.tracks = (album.tracks || []).map(normalizeTrack);
      return album;
    });
  }

  function getArtist(artistId) {
    if (!artistId) return Promise.reject(new Error("[NexaMusic TIDAL] Artist ID is required."));
    return request("/v1/artists/" + encode(artistId));
  }

  function clearCache() {
    searchCache = {};
    streamCache = {};
    inflight = {};
  }

  var moduleDefinition = {
    id: MODULE_ID,
    name: "TIDAL (8SPINE Connector)",
    author: "NexaMusic",
    version: VERSION,
    description:
      "TIDAL catalog and URL-provider adapter for 8SPINE. Requires a NexaMusic-controlled " +
      "resolver authorised by TIDAL; no private TIDAL endpoints or fabricated media URLs.",
    labels: ["TIDAL", "LOSSLESS", "HI-RES", "ATMOS", "ISRC"],
    settings: {
      apiBaseUrl: {
        type: "text",
        label: "Authorised resolver HTTPS URL",
        default: ""
      },
      apiToken: {
        type: "password",
        label: "Resolver access token",
        default: ""
      },
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
    searchTracks: searchTracks,
    getTrackStreamUrl: getTrackStreamUrl,
    getTrackDownloadUrl: getTrackDownloadUrl,
    getAlbum: getAlbum,
    getArtist: getArtist,
    clearCache: clearCache
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = moduleDefinition;
  } else if (root) {
    root.NexaMusicTidalModule = moduleDefinition;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);
