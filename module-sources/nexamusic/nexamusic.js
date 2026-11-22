/*
 * NexaMusic — 8SPINE Module Engine v2.4.0-alpha
 *
 * IMPORTANT:
 * Set API_BASE_URL to an API you own or are authorized to use.
 * Expected API:
 *   GET /search?q=<query>&limit=<limit>
 *   GET /stream/<trackId>?quality=LOSSLESS|HIGH|LOW
 *
 * Search response: { tracks: [{ id, title, artist, album, duration, albumCover }] }
 * Stream response: {
 *   streamUrl: "https://...",
 *   track: { codec:"FLAC", bitDepth:16, sampleRate:44100, audioQuality:"LOSSLESS" }
 * }
 *
 * The module intentionally has no fake/demo catalogue or fabricated audio URLs.
 */

const API_BASE_URL = "https://YOUR-NEXAMUSIC-API-HOST";

function assertConfiguredApi() {
  if (!/^https:\/\//i.test(API_BASE_URL) ||
      API_BASE_URL.indexOf("YOUR-NEXAMUSIC-API-HOST") !== -1) {
    throw new Error("[NexaMusic] Provider API is not configured. Set API_BASE_URL to your HTTPS API.");
  }
}

async function fetchJson(url) {
  const response = await fetch(url, {
    headers: { Accept: "application/json" }
  });
  if (!response || !response.ok) {
    throw new Error("[NexaMusic] API request failed: HTTP " +
      (response ? response.status : "unknown"));
  }
  return await response.json();
}

function pick(object, keys, fallback) {
  for (const key of keys) {
    if (object && object[key] !== undefined && object[key] !== null && object[key] !== "") {
      return object[key];
    }
  }
  return fallback;
}

function normalizeTrack(item) {
  return {
    id: String(pick(item, ["id", "trackId", "track_id"], "")),
    title: String(pick(item, ["title", "name"], "Unknown title")),
    artist: String(pick(item, ["artist", "artistName", "artist_name"], "Unknown artist")),
    album: String(pick(item, ["album", "albumName", "album_name"], "")),
    duration: Number(pick(item, ["duration", "durationSeconds"], 0)) || 0,
    albumCover: pick(item, ["albumCover", "coverUrl", "artwork"], null),
    isrc: pick(item, ["isrc", "ISRC"], null)
  };
}

async function searchTracks(query, limit = 30) {
  assertConfiguredApi();
  const safeLimit = Math.max(1, Math.min(Number(limit) || 30, 100));
  const url = API_BASE_URL.replace(/\/+$/, "") +
    "/search?q=" + encodeURIComponent(String(query || "")) +
    "&limit=" + safeLimit;
  const payload = await fetchJson(url);
  const items = Array.isArray(payload) ? payload :
    pick(payload, ["tracks", "results", "items"], []);
  if (!Array.isArray(items)) {
    throw new Error("[NexaMusic] Search API returned an invalid tracks list.");
  }
  const tracks = items.map(normalizeTrack).filter(track => track.id);
  return {
    tracks: tracks,
    total: Number(pick(payload, ["total", "count"], tracks.length)) || tracks.length
  };
}

async function getTrackStreamUrl(trackId, quality = "LOSSLESS") {
  assertConfiguredApi();
  const requested = String(quality || "LOSSLESS").toUpperCase();
  if (!["LOSSLESS", "HIGH", "LOW"].includes(requested)) {
    throw new Error("[NexaMusic] Unsupported requested quality: " + requested);
  }

  const url = API_BASE_URL.replace(/\/+$/, "") +
    "/stream/" + encodeURIComponent(String(trackId)) +
    "?quality=" + encodeURIComponent(requested);
  const payload = await fetchJson(url);
  const metadata = pick(payload, ["track", "metadata"], payload);
  const streamUrl = pick(payload, ["streamUrl", "url", "directUrl"], null);

  if (typeof streamUrl !== "string" || !/^https:\/\//i.test(streamUrl)) {
    throw new Error("[NexaMusic] Provider did not return a direct HTTPS audio URL.");
  }

  const codec = String(pick(metadata, ["codec", "format", "audioCodec"], "")).toUpperCase();
  const mimeType = String(pick(metadata, ["mimeType", "contentType"], "")).toLowerCase();
  const bitDepth = Number(pick(metadata, ["bitDepth", "bitsPerSample"], 0)) || 0;
  const sampleRate = Number(pick(metadata, ["sampleRate", "samplingRate"], 0)) || 0;
  const isFlac = codec === "FLAC" || mimeType === "audio/flac" || mimeType === "audio/x-flac";

  if (requested === "LOSSLESS") {
    if (!isFlac) {
      throw new Error("[NexaMusic] LOSSLESS rejected: provider did not identify the stream as FLAC.");
    }
    if (bitDepth < 16 || sampleRate < 44100) {
      throw new Error("[NexaMusic] LOSSLESS rejected: stream does not meet CD quality (16-bit / 44.1 kHz minimum).");
    }
  }

  return {
    streamUrl: streamUrl,
    track: {
      id: String(trackId),
      audioQuality: requested === "LOSSLESS" ? "LOSSLESS" :
        String(pick(metadata, ["audioQuality", "quality"], requested)),
      codec: codec || null,
      mimeType: mimeType || null,
      bitDepth: bitDepth || null,
      sampleRate: sampleRate || null,
      bitrate: Number(pick(metadata, ["bitrate"], 0)) || null
    }
  };
}

return {
  id: "nexamusic",
  name: "NexaMusic",
  version: "0.1.1",
  labels: ["FLAC", "LOSSLESS", "CD QUALITY"],
  searchTracks: searchTracks,
  getTrackStreamUrl: getTrackStreamUrl
};
