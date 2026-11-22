/**
 * NexaMusic legacy stream backend — 0.1.0
 *
 * Jamendo URL resolution has been retired. Official TIDAL Android playback is
 * handled by the native TIDAL Player SDK in the app, not by a stream proxy.
 */
"use strict";

const http = require("node:http");

const PORT = Number(process.env.PORT) || 10000;
const HOST = "0.0.0.0";

function sendJson(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET,HEAD,OPTIONS",
    "access-control-allow-headers": "Content-Type,Accept"
  });
  res.end(body);
}

const server = http.createServer((req, res) => {
  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET,HEAD,OPTIONS",
      "access-control-allow-headers": "Content-Type,Accept"
    });
    return res.end();
  }

  if (req.method !== "GET" && req.method !== "HEAD") {
    return sendJson(res, 405, { error: "Method not allowed." });
  }

  if (req.url === "/" || req.url === "/health" || req.url === "/v1/health") {
    return sendJson(res, 200, {
      ok: true,
      service: "NexaMusic legacy stream backend",
      version: "0.1.0",
      status: "retired",
      provider: null,
      streamProxy: false,
      message: "Legacy Jamendo streaming is disabled. Native TIDAL SDK integration is in the Android app."
    });
  }

  return sendJson(res, 410, {
    error: "This stream endpoint has been retired.",
    status: "gone",
    replacement: "Official native TIDAL Android Player SDK",
    streamProxy: false
  });
});

server.listen(PORT, HOST, () => {
  process.stdout.write("NexaMusic retired stream backend listening on " + HOST + ":" + PORT + "\n");
});
