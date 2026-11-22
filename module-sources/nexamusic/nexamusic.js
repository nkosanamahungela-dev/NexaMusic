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

function normalizeQuality(quality, ctx) {
  function unwrap(value) {
    for (let i = 0; i < 3 && value && typeof value === "object"; i++) {
      if (Object.prototype.hasOwnProperty.call(value, "value")) value = value.value;
      else break;
    }
    return String(value || "").trim().toUpperCase();
  }

  let selected = unwrap(quality);
  if (!selected && ctx && ctx.settings) selected = unwrap(ctx.settings.quality);
  if (!selected && ctx) selected = unwrap(ctx.quality);

  if (selected === "CD" || selected === "FLAC" || selected === "HI_RES") return "LOSSLESS";
  if (selected === "LOSSLESS" || selected === "HIGH" || selected === "LOW") return selected;
  // 8SPINE's standard quality argument is LOSSLESS/HIGH/LOW. Default to
  // LOSSLESS only when the host did not provide a selection.
  if (!selected) return "LOSSLESS";
  throw new Error("[NexaMusic] Unsupported quality selection: " + selected);
}

async function getTrackStreamUrl(trackId, quality, ctx) {
  assertConfigured();

  const suppliedId = String(trackId || "").trim();
  if (!suppliedId) throw new Error("[NexaMusic] A Jamendo track ID is required.");
  const id = suppliedId.indexOf("jamendo:") === 0
    ? suppliedId.slice("jamendo:".length)
    : suppliedId;

  const requested = normalizeQuality(quality, ctx);
  const audioFormat = requested === "LOSSLESS" ? "flac" :
    (requested === "HIGH" ? "mp32" : "mp31");

  // Jamendo documents results[].audio as the stream URL and audioformat
  // selects its encoding. Do not require a particular query-string shape:
  // Jamendo may change the URL representation while honoring audioformat.
  const payload = await requestJson(apiUrl("/tracks/", {
    id: id,
    limit: 1,
    audioformat: audioFormat,
    include: "licenses",
    imagesize: 300
  }));

  const item = payload.results && payload.results[0];
  if (!item || String(item.id) !== id) {
    throw new Error("[NexaMusic] Jamendo returned no matching track for ID " + id + ".");
  }

  const streamUrl = typeof item.audio === "string" ? item.audio.trim() : "";
  if (!streamUrl || !/^https:\/\//i.test(streamUrl)) {
    throw new Error("[NexaMusic] Jamendo did not return a playable " + audioFormat.toUpperCase() + " stream.");
  }

  const mimeType = audioFormat === "flac" ? "audio/flac" : "audio/mpeg";
  const qualityLabel = audioFormat === "flac" ? "FLAC" :
    (audioFormat === "mp32" ? "MP3 VBR" : "MP3 96 kbps");

  return {
    streamUrl: streamUrl,
    // Jamendo's audio field is the stream URL. Downloads are not exposed
    // separately here because download permission is track-dependent.
    mimeType: mimeType,
    streamType: "direct",
    track: {
      id: suppliedId,
      title: String(item.name || ""),
      artist: String(item.artist_name || ""),
      album: String(item.album_name || ""),
      duration: Number(item.duration) || 0,
      albumCover: item.album_image || item.image || null,
      audioQuality: qualityLabel,
      quality: qualityLabel,
      format: audioFormat === "flac" ? "flac" : "mp3",
      codec: audioFormat === "flac" ? "FLAC" : "MP3",
      mimeType: mimeType,
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
  // Jamendo stream URLs may be temporary/provider-managed. Resolve each
  // playback request afresh rather than persisting a stale stream URL.
  noStreamCache: true,
  settings: {
    quality: {
      type: "selector",
      label: "Audio Quality",
      description: "FLAC lossless format where available. Jamendo does not expose verified bit depth or sample rate, so CD-quality specifications are not claimed.",
      options: [
        { label: "FLAC (Lossless format)", value: "LOSSLESS" },
        { label: "High Quality (MP3 VBR)", value: "HIGH" },
        { label: "Low Quality (MP3 96 kbps)", value: "LOW" }
      ],
      defaultValue: "LOSSLESS"
    }
  },
  searchTracks: searchTracks,
  getArtistTracks: getArtistTracks,
  getTrackStreamUrl: getTrackStreamUrl
};
