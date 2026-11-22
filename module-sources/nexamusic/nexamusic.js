/*
 * NexaMusic — 8SPINE Module Engine v2.4.0-alpha
 * Provider: Jamendo public catalogue API
 *
 * Jamendo Client ID is configured for the NexaMusic application.
 *
 * Search: GET /v3.0/tracks/
 * Playback: GET /v3.0/tracks/?id=...&audioformat=flac
 */

const JAMENDO_CLIENT_ID = "2f3829f8";
const JAMENDO_API = "https://api.jamendo.com/v3.0";

function assertConfigured() {
  if (!JAMENDO_CLIENT_ID || JAMENDO_CLIENT_ID === "YOUR_JAMENDO_CLIENT_ID") {
    throw new Error("[NexaMusic] Jamendo is not configured. Add your Jamendo developer Client ID to the module.");
  }
}

async function requestJson(url) {
  const response = await fetch(url, {
    method: "GET",
    headers: { Accept: "application/json" }
  });

  if (!response || !response.ok) {
    throw new Error("[NexaMusic] Jamendo request failed (HTTP " +
      (response ? response.status : "unknown") + ").");
  }

  const payload = await response.json();
  if (!payload || !payload.headers || payload.headers.status !== "success") {
    const message = payload && payload.headers && payload.headers.error_message;
    throw new Error("[NexaMusic] Jamendo API error: " + (message || "Unknown response"));
  }
  return payload;
}

function apiUrl(path, params) {
  const pairs = [];
  pairs.push("client_id=" + encodeURIComponent(JAMENDO_CLIENT_ID));
  pairs.push("format=json");
  for (const key in params) {
    if (Object.prototype.hasOwnProperty.call(params, key) &&
        params[key] !== undefined && params[key] !== null && params[key] !== "") {
      pairs.push(encodeURIComponent(key) + "=" + encodeURIComponent(String(params[key])));
    }
  }
  return JAMENDO_API + path + "?" + pairs.join("&");
}

function mapTrack(item) {
  return {
    id: "jamendo:" + String(item.id),
    title: String(item.name || "Unknown title"),
    artist: String(item.artist_name || "Unknown artist"),
    album: String(item.album_name || ""),
    duration: Number(item.duration) || 0,
    albumCover: item.album_image || item.image || null,
    source: "Jamendo",
    license: item.license_ccurl || null,
    trackPageUrl: item.shareurl || item.shorturl || null,
    audiodownloadAllowed: item.audiodownload_allowed === true || item.audiodownload_allowed === "true",
    audioFormat: "FLAC",
    // Jamendo's API supplies a requested format but does not expose
    // verified bit-depth/sample-rate metadata in this response.
    // Do not claim CD-quality (16-bit/44.1 kHz) from format alone.
    audioQuality: "HIGH"
  };
}

async function searchTracks(query, limit) {
  assertConfigured();
  const search = String(query || "").trim();
  if (!search) return { tracks: [], total: 0 };

  const safeLimit = Math.max(1, Math.min(Number(limit) || 30, 50));
  const payload = await requestJson(apiUrl("/tracks/", {
    search: search,
    limit: safeLimit,
    type: "single albumtrack",
    audioformat: "flac",
    include: "licenses",
    fullcount: true,
    imagesize: 300
  }));

  const results = Array.isArray(payload.results) ? payload.results : [];
  const tracks = results
    .filter(function (item) { return item && item.id !== undefined && item.id !== null; })
    .map(mapTrack);

  return {
    tracks: tracks,
    total: Number(payload.headers.results_fullcount) ||
      Number(payload.headers.results_count) || tracks.length
  };
}

async function getArtistTracks(artistId, limit) {
  assertConfigured();
  const id = String(artistId || "").trim();
  if (!id) throw new Error("[NexaMusic] An artist ID is required.");

  const safeLimit = Math.max(1, Math.min(Number(limit) || 30, 50));
  const payload = await requestJson(apiUrl("/artists/tracks/", {
    id: id,
    limit: safeLimit,
    track_type: "single albumtrack",
    audioformat: "flac",
    include: "licenses",
    imagesize: 300
  }));

  const artists = Array.isArray(payload.results) ? payload.results : [];
  const artist = artists[0];
  const tracks = artist && Array.isArray(artist.tracks) ? artist.tracks : [];
  return {
    artist: artist ? {
      id: String(artist.id),
      name: String(artist.name || "Unknown artist"),
      image: artist.image || null,
      pageUrl: artist.shareurl || artist.shorturl || null
    } : null,
    tracks: tracks
      .filter(function (item) { return item && item.id !== undefined && item.id !== null; })
      .map(mapTrack),
    total: Number(payload.headers.results_count) || tracks.length
  };
}

async function getTrackStreamUrl(trackId, quality) {
  assertConfigured();

  const suppliedId = String(trackId || "").trim();
  if (!suppliedId) throw new Error("[NexaMusic] A Jamendo track ID is required.");
  // Namespace IDs so 8SPINE cannot reuse a stale stream cached for another
  // provider or for an earlier NexaMusic module implementation.
  const id = suppliedId.indexOf("jamendo:") === 0
    ? suppliedId.slice("jamendo:".length)
    : suppliedId;

  const requested = String(quality || "LOSSLESS").toUpperCase();
  let audioFormat;
  let reportedQuality;
  if (requested === "LOSSLESS") {
    audioFormat = "flac";
    reportedQuality = "LOSSLESS";
  } else if (requested === "HIGH") {
    audioFormat = "mp32";
    reportedQuality = "HIGH";
  } else if (requested === "LOW") {
    audioFormat = "mp31";
    reportedQuality = "LOW";
  } else {
    throw new Error("[NexaMusic] Unsupported quality: " + requested);
  }

  // Jamendo's /tracks/ method returns the playable URL in results[].audio.
  // Request the chosen format here rather than constructing /tracks/file/,
  // which is a download/redirect endpoint and can reject tracks whose
  // download permission is disabled.
  const payload = await requestJson(apiUrl("/tracks/", {
    id: id,
    limit: 1,
    audioformat: audioFormat,
    include: "licenses",
    imagesize: 300
  }));

  const item = payload.results && payload.results[0];
  if (!item || String(item.id) !== id) {
    throw new Error("[NexaMusic] Jamendo did not return metadata for track " + id + ".");
  }

  const streamUrl = typeof item.audio === "string" ? item.audio.trim() : "";
  if (!streamUrl || !/^https:\/\//i.test(streamUrl)) {
    throw new Error("[NexaMusic] Jamendo did not provide a playable " + audioFormat.toUpperCase() + " stream for track " + id + ".");
  }

  // Jamendo's documented audio URL carries the actual requested format in
  // its query string (format=flac/mp32/mp31). Reject mismatches so 8SPINE
  // can try another candidate instead of silently playing a lossy stream.
  let returnedFormat = "";
  try {
    returnedFormat = new URL(streamUrl).searchParams.get("format") || "";
  } catch (_) {
    throw new Error("[NexaMusic] Jamendo returned an unreadable audio URL for track " + id + ".");
  }
  if (returnedFormat.toLowerCase() !== audioFormat) {
    throw new Error("[NexaMusic] Jamendo returned " + (returnedFormat || "an unknown format") +
      " when " + audioFormat + " was requested for track " + id + ".");
  }

  return {
    streamUrl: streamUrl,
    track: {
      id: String(item.id),
      title: String(item.name || ""),
      artist: String(item.artist_name || ""),
      album: String(item.album_name || ""),
      duration: Number(item.duration) || 0,
      albumCover: item.album_image || item.image || null,
      audioQuality: reportedQuality,
      codec: audioFormat === "flac" ? "FLAC" : "MP3",
      provider: "Jamendo",
      license: item.license_ccurl || null,
      trackPageUrl: item.shareurl || item.shorturl || null,
      audiodownloadAllowed: item.audiodownload_allowed === true || item.audiodownload_allowed === "true"
    }
  };
}

return {
  id: "nexamusic",
  name: "NexaMusic",
  version: "0.1.0",
  labels: ["JAMENDO", "FLAC", "INDEPENDENT MUSIC"],
  searchTracks: searchTracks,
  getArtistTracks: getArtistTracks,
  getTrackStreamUrl: getTrackStreamUrl
};
