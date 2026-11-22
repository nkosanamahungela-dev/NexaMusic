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
    id: String(item.id),
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

  const requested = String(quality || "LOSSLESS").toUpperCase();
  let audioFormat = "flac";
  if (requested === "LOW") audioFormat = "mp31";
  else if (requested === "HIGH") audioFormat = "mp32";
  else if (requested !== "LOSSLESS") {
    throw new Error("[NexaMusic] Unsupported quality: " + requested);
  }

  const payload = await requestJson(apiUrl("/tracks/", {
    id: String(trackId),
    limit: 1,
    audioformat: audioFormat,
    include: "licenses"
  }));

  const item = payload.results && payload.results[0];
  if (!item || !item.audio || !/^https:\/\//i.test(item.audio)) {
    throw new Error("[NexaMusic] Jamendo did not return a playable audio stream for this track.");
  }

  const codec = audioFormat === "flac" ? "FLAC" :
    (audioFormat === "ogg" ? "OGG" : "MP3");

  return {
    streamUrl: item.audio,
    track: {
      id: String(item.id),
      title: String(item.name || ""),
      artist: String(item.artist_name || ""),
      audioQuality: audioFormat === "flac" ? "HIGH" :
        (audioFormat === "mp32" ? "HIGH" : "LOW"),
      codec: codec,
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
  version: "0.2.0",
  labels: ["JAMENDO", "FLAC", "INDEPENDENT MUSIC"],
  searchTracks: searchTracks,
  getArtistTracks: getArtistTracks,
  getTrackStreamUrl: getTrackStreamUrl
};
