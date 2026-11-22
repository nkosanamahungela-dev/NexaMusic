/*
 * NexaMusic — 8SPINE Module Engine v2.4.0-alpha
 * Provider: NexaMusic backend (Jamendo catalogue adapter)
 * Version: 0.1.0 — testing
 *
 * The module talks only to the NexaMusic backend. Provider API access,
 * stream resolution, and audio byte-range forwarding stay server-side.
 */

const NEXAMUSIC_API = "https://nexamusic-stream-backend.onrender.com/v1";

async function backendJson(path) {
  const response = await fetch(NEXAMUSIC_API + path, {
    method: "GET",
    headers: { Accept: "application/json" },
    signal: AbortSignal.timeout(20000)
  });

  let payload;
  try {
    payload = await response.json();
  } catch (_) {
    throw new Error("[NexaMusic] Backend returned an invalid JSON response.");
  }

  if (!response.ok) {
    throw new Error("[NexaMusic] Backend request failed (HTTP " +
      response.status + "): " + String(payload && payload.error || "Unknown error"));
  }
  return payload;
}

function encode(value) {
  return encodeURIComponent(String(value == null ? "" : value));
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
  if (!selected) return "LOSSLESS";
  throw new Error("[NexaMusic] Unsupported quality selection: " + selected);
}

function normalizeTrack(track) {
  return {
    id: String(track.id || ""),
    title: String(track.title || "Unknown title"),
    artist: String(track.artist || "Unknown artist"),
    album: String(track.album || ""),
    duration: Number(track.duration) || 0,
    albumCover: track.albumCover || null,
    source: "Jamendo",
    license: track.license || null,
    trackPageUrl: track.trackPageUrl || null,
    audiodownloadAllowed: track.audiodownloadAllowed === true
  };
}

async function searchTracks(query, limit) {
  const search = String(query || "").trim();
  if (!search) return { tracks: [], total: 0 };

  const safeLimit = Math.max(1, Math.min(Number(limit) || 30, 50));
  const payload = await backendJson("/search?q=" + encode(search) + "&limit=" + safeLimit);
  const tracks = Array.isArray(payload.tracks) ? payload.tracks : [];
  return {
    tracks: tracks.map(normalizeTrack),
    total: Number(payload.total) || tracks.length
  };
}

async function getArtistTracks(artistId, limit) {
  const id = String(artistId || "").trim();
  if (!/^\d+$/.test(id)) throw new Error("[NexaMusic] A valid Jamendo artist ID is required.");

  const safeLimit = Math.max(1, Math.min(Number(limit) || 30, 50));
  const payload = await backendJson("/artists/" + encode(id) + "/tracks?limit=" + safeLimit);
  const tracks = Array.isArray(payload.tracks) ? payload.tracks : [];
  return {
    artist: payload.artist || null,
    tracks: tracks.map(normalizeTrack),
    total: Number(payload.total) || tracks.length
  };
}

async function getTrackStreamUrl(trackId, quality, ctx) {
  const suppliedId = String(trackId || "").trim();
  if (!suppliedId) throw new Error("[NexaMusic] A track ID is required.");

  const id = suppliedId.indexOf("jamendo:") === 0
    ? suppliedId.slice("jamendo:".length)
    : suppliedId;
  if (!/^\d+$/.test(id)) throw new Error("[NexaMusic] Invalid Jamendo track ID.");

  const selectedQuality = normalizeQuality(quality, ctx);
  const result = await backendJson("/resolve/" + encode(id) +
    "?quality=" + encode(selectedQuality));

  if (!result || typeof result.streamUrl !== "string" ||
      result.streamUrl.indexOf("https://") !== 0) {
    throw new Error("[NexaMusic] Backend did not return a valid stream URL.");
  }

  return {
    streamUrl: result.streamUrl,
    downloadUrl: result.downloadUrl || null,
    fileName: result.fileName || "NexaMusic track",
    mimeType: result.mimeType || "audio/flac",
    streamType: result.streamType || "direct",
    expiresAt: result.expiresAt || null,
    track: result.track || { id: suppliedId },
    quality: result.quality || selectedQuality,
    provider: "NexaMusic"
  };
}

return {
  id: "nexamusic",
  name: "NexaMusic",
  version: "0.1.0",
  labels: ["NEXAMUSIC", "JAMENDO", "FLAC", "INDEPENDENT MUSIC"],
  noStreamCache: true,
  settings: {
    quality: {
      type: "selector",
      label: "Audio Quality",
      description: "Select the available Jamendo encoding. FLAC format does not guarantee verified CD bit depth or sample rate.",
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
