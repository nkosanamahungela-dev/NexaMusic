/**
 * NexaMusic Stream Backend — 0.1.0
 * Jamendo-backed 8SPINE resolver with HTTP Range proxying.
 *
 * This service only resolves Jamendo catalogue streams. It does not claim
 * verified CD bit depth/sample rate, and it does not provide Tidal access.
 */
"use strict";

const http = require("node:http");
const { URL } = require("node:url");
const { Readable } = require("node:stream");
const { pipeline } = require("node:stream/promises");

const PORT = Number(process.env.PORT) || 10000;
const HOST = "0.0.0.0";
const API_BASE = "https://api.jamendo.com/v3.0";
const CLIENT_ID = process.env.JAMENDO_CLIENT_ID || "2f3829f8";
const PUBLIC_BASE_URL = (process.env.PUBLIC_BASE_URL || "").replace(/\/$/, "");
const USER_AGENT = "NexaMusic/0.1.0 (8SPINE stream backend)";

const QUALITY_FORMATS = Object.freeze({
  LOSSLESS: "flac",
  FLAC: "flac",
  CD: "flac",
  HI_RES: "flac",
  HIGH: "mp32",
  LOW: "mp31"
});

function sendJson(res, status, value, extraHeaders = {}) {
  const body = JSON.stringify(value);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "access-control-allow-origin": "*",
    ...extraHeaders
  });
  res.end(body);
}

function qualityFormat(value) {
  const key = String(value || "LOSSLESS").trim().toUpperCase();
  const format = QUALITY_FORMATS[key];
  if (!format) {
    const error = new Error("Unsupported quality. Use LOSSLESS, HIGH, or LOW.");
    error.statusCode = 400;
    throw error;
  }
  return { key, format };
}

function validTrackId(value) {
  const id = String(value || "").replace(/^jamendo:/i, "");
  if (!/^\d{1,20}$/.test(id)) {
    const error = new Error("A valid Jamendo numeric track ID is required.");
    error.statusCode = 400;
    throw error;
  }
  return id;
}

async function jamendoJson(path, params) {
  const url = new URL(API_BASE + path);
  url.searchParams.set("client_id", CLIENT_ID);
  url.searchParams.set("format", "json");
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== undefined && value !== null && value !== "") {
      url.searchParams.set(key, String(value));
    }
  }

  const response = await fetch(url, {
    headers: { accept: "application/json", "user-agent": USER_AGENT },
    signal: AbortSignal.timeout(15000)
  });
  if (!response.ok) {
    const error = new Error("Jamendo API returned HTTP " + response.status + ".");
    error.statusCode = 502;
    throw error;
  }
  const payload = await response.json();
  if (!payload || !payload.headers || payload.headers.status !== "success") {
    const message = payload && payload.headers && payload.headers.error_message;
    const error = new Error(message || "Jamendo returned an unsuccessful API response.");
    error.statusCode = 502;
    throw error;
  }
  return payload;
}

function mapTrack(item) {
  return {
    id: "jamendo:" + String(item.id),
    title: String(item.name || "Unknown title"),
    artist: String(item.artist_name || "Unknown artist"),
    artistId: item.artist_id == null ? null : String(item.artist_id),
    album: String(item.album_name || ""),
    duration: Number(item.duration) || 0,
    albumCover: item.album_image || item.image || null,
    source: "Jamendo",
    license: item.license_ccurl || null,
    trackPageUrl: item.shareurl || item.shorturl || null,
    audiodownloadAllowed: item.audiodownload_allowed === true || item.audiodownload_allowed === "true"
  };
}

async function getTrack(trackId, format) {
  const payload = await jamendoJson("/tracks/", {
    id: trackId,
    limit: 1,
    audioformat: format,
    include: "licenses",
    imagesize: 300
  });
  const item = Array.isArray(payload.results) ? payload.results[0] : null;
  if (!item || String(item.id) !== trackId) {
    const error = new Error("Jamendo did not return the requested track.");
    error.statusCode = 404;
    throw error;
  }
  const audio = typeof item.audio === "string" ? item.audio.trim() : "";
  if (!/^https:\/\//i.test(audio)) {
    const error = new Error("Jamendo did not provide a playable stream for this track and format.");
    error.statusCode = 502;
    throw error;
  }
  return { item, audio };
}

function baseUrl(req) {
  if (PUBLIC_BASE_URL) return PUBLIC_BASE_URL;
  const forwardedProto = String(req.headers["x-forwarded-proto"] || "https").split(",")[0].trim();
  const host = req.headers.host;
  return forwardedProto + "://" + host;
}

async function handle(req, res) {
  const requestUrl = new URL(req.url, "http://" + (req.headers.host || "localhost"));
  const path = requestUrl.pathname;

  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET,HEAD,OPTIONS",
      "access-control-allow-headers": "Range,Content-Type,Accept",
      "access-control-max-age": "86400"
    });
    return res.end();
  }

  if (req.method !== "GET" && req.method !== "HEAD") {
    return sendJson(res, 405, { error: "Method not allowed." }, { allow: "GET, HEAD, OPTIONS" });
  }

  try {
    if (path === "/" || path === "/health" || path === "/v1/health") {
      return sendJson(res, 200, {
        ok: true,
        service: "NexaMusic Stream Backend",
        version: "0.1.0",
        provider: "Jamendo",
        capabilities: ["search", "artist-tracks", "stream-resolution", "range-proxy"],
        tidal: false
      });
    }

    if (path === "/v1/search") {
      const query = String(requestUrl.searchParams.get("q") || "").trim();
      const limit = Math.max(1, Math.min(Number(requestUrl.searchParams.get("limit")) || 30, 50));
      if (!query) return sendJson(res, 200, { tracks: [], total: 0 });
      const payload = await jamendoJson("/tracks/", {
        search: query,
        limit,
        type: "single albumtrack",
        audioformat: "flac",
        include: "licenses",
        fullcount: true,
        imagesize: 300
      });
      const results = Array.isArray(payload.results) ? payload.results : [];
      const tracks = results.filter(x => x && x.id != null).map(mapTrack);
      return sendJson(res, 200, {
        tracks,
        total: Number(payload.headers.results_fullcount) || Number(payload.headers.results_count) || tracks.length
      });
    }

    const artistMatch = path.match(/^\/v1\/artists\/(\d+)\/tracks$/);
    if (artistMatch) {
      const limit = Math.max(1, Math.min(Number(requestUrl.searchParams.get("limit")) || 30, 50));
      const payload = await jamendoJson("/artists/tracks/", {
        id: artistMatch[1],
        limit,
        track_type: "single albumtrack",
        audioformat: "flac",
        include: "licenses",
        imagesize: 300
      });
      const artist = Array.isArray(payload.results) ? payload.results[0] : null;
      const tracks = artist && Array.isArray(artist.tracks) ? artist.tracks.filter(x => x && x.id != null).map(mapTrack) : [];
      return sendJson(res, 200, {
        artist: artist ? {
          id: String(artist.id),
          name: String(artist.name || "Unknown artist"),
          image: artist.image || null,
          pageUrl: artist.shareurl || artist.shorturl || null
        } : null,
        tracks,
        total: Number(payload.headers.results_count) || tracks.length
      });
    }

    const resolveMatch = path.match(/^\/v1\/resolve\/(\d+)$/);
    if (resolveMatch) {
      const id = validTrackId(resolveMatch[1]);
      const { key, format } = qualityFormat(requestUrl.searchParams.get("quality"));
      const { item } = await getTrack(id, format);
      const streamUrl = baseUrl(req) + "/v1/stream/" + id + "?quality=" + encodeURIComponent(key);
      const downloadAllowed = item.audiodownload_allowed === true || item.audiodownload_allowed === "true";
      return sendJson(res, 200, {
        streamUrl,
        downloadUrl: downloadAllowed && item.audiodownload ? item.audiodownload : null,
        fileName: String(item.name || "NexaMusic track") + (format === "flac" ? ".flac" : ".mp3"),
        mimeType: format === "flac" ? "audio/flac" : "audio/mpeg",
        streamType: "direct",
        quality: format === "flac" ? "FLAC" : (format === "mp32" ? "MP3 VBR" : "MP3 96 kbps"),
        format,
        provider: "Jamendo",
        track: mapTrack(item)
      });
    }

    const streamMatch = path.match(/^\/v1\/stream\/(\d+)$/);
    if (streamMatch) {
      const id = validTrackId(streamMatch[1]);
      const { format } = qualityFormat(requestUrl.searchParams.get("quality"));
      const { audio } = await getTrack(id, format);

      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 20000);
      let upstream;
      try {
        const headers = {
          "user-agent": USER_AGENT,
          "accept": req.headers.accept || "*/*",
          "accept-encoding": "identity"
        };
        if (req.headers.range) headers.range = req.headers.range;
        upstream = await fetch(audio, { method: req.method, headers, signal: controller.signal, redirect: "follow" });
      } finally {
        clearTimeout(timer);
      }

      if (!upstream.ok && upstream.status !== 206) {
        if (upstream.body) await upstream.body.cancel().catch(() => {});
        return sendJson(res, 502, { error: "Jamendo audio server returned HTTP " + upstream.status + "." });
      }

      const responseHeaders = {
        "access-control-allow-origin": "*",
        "cache-control": "no-store",
        "content-type": upstream.headers.get("content-type") || (format === "flac" ? "audio/flac" : "audio/mpeg"),
        "accept-ranges": upstream.headers.get("accept-ranges") || "bytes"
      };
      for (const header of ["content-length", "content-range", "last-modified", "etag"]) {
        const value = upstream.headers.get(header);
        if (value) responseHeaders[header] = value;
      }
      res.writeHead(upstream.status, responseHeaders);
      if (req.method === "HEAD" || !upstream.body) {
        if (upstream.body) await upstream.body.cancel().catch(() => {});
        return res.end();
      }
      res.on("close", () => {
        if (!res.writableEnded) controller.abort();
      });
      await pipeline(Readable.fromWeb(upstream.body), res);
      return;
    }

    return sendJson(res, 404, { error: "Route not found." });
  } catch (error) {
    if (res.headersSent) {
      res.destroy(error);
      return;
    }
    const status = Number(error.statusCode) || 500;
    return sendJson(res, status, {
      error: error.message || "Unexpected backend error.",
      provider: "Jamendo"
    });
  }
}

const server = http.createServer((req, res) => {
  handle(req, res).catch(error => {
    if (!res.headersSent) sendJson(res, 500, { error: error.message || "Unexpected server error." });
    else res.destroy(error);
  });
});

server.listen(PORT, HOST, () => {
  process.stdout.write("NexaMusic Stream Backend listening on " + HOST + ":" + PORT + "\n");
});
