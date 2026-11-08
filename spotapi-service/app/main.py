from __future__ import annotations

import logging
from typing import Any

from spotapi.v2 import Public
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

logger = logging.getLogger("nexamusic.spotapi")

app = FastAPI(
    title="NexaMusic SpotAPI Gateway",
    version="0.1.0",
    description="Public Spotify catalogue search for NexaMusic. This service does not proxy audio.",
)

# Set NEXAMUSIC_CORS_ORIGINS to a comma-separated allowlist in deployment.
# Android native clients do not require browser CORS access.
import os

_allowed_origins = [
    origin.strip()
    for origin in os.getenv("NEXAMUSIC_CORS_ORIGINS", "").split(",")
    if origin.strip()
]
if _allowed_origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=_allowed_origins,
        allow_methods=["GET"],
        allow_headers=["*"],
    )


def _read(value: Any, *path: str) -> Any:
    current = value
    for part in path:
        if current is None:
            return None
        if isinstance(current, dict):
            current = current.get(part)
        else:
            current = getattr(current, part, None)
    return current


def _artist_name(artist: Any) -> str | None:
    return (
        _read(artist, "profile", "name")
        or _read(artist, "name")
        or _read(artist, "profile", "username")
    )


def _items(value: Any) -> list[Any]:
    if value is None:
        return []
    items = _read(value, "items")
    if items is not None:
        return list(items)
    if isinstance(value, (list, tuple)):
        return list(value)
    return []


def _serialize_track(track: Any) -> dict[str, Any]:
    duration = _read(track, "duration", "total_milliseconds")
    artists = [_artist_name(item) for item in _items(_read(track, "artists"))]
    return {
        "name": _read(track, "name"),
        "uri": _read(track, "uri"),
        "duration_ms": duration,
        "artists": [name for name in artists if name],
        "album": _read(track, "album_of_track", "name") or _read(track, "album", "name"),
        "image_url": (
            _read(
                (_items(_read(track, "album_of_track", "cover_art", "sources")) or [None])[0],
                "url",
            )
        ),
    }


def _serialize_artist(artist: Any) -> dict[str, Any]:
    return {
        "name": _artist_name(artist),
        "uri": _read(artist, "uri"),
        "followers": _read(artist, "followers", "total"),
    }


def _serialize_album(album: Any) -> dict[str, Any]:
    artists = [_artist_name(item) for item in _items(_read(album, "artists"))]
    sources = _items(_read(album, "cover_art", "sources"))
    return {
        "name": _read(album, "name"),
        "uri": _read(album, "uri"),
        "artists": [name for name in artists if name],
        "release_date": _read(album, "date", "iso_string")
        or _read(album, "date", "year"),
        "image_url": _read(sources[0], "url") if sources else None,
    }


def _serialize_playlist(playlist: Any) -> dict[str, Any]:
    image_items = _items(_read(playlist, "images", "items"))
    image_sources = _items(_read(image_items[0], "sources")) if image_items else []
    owner = _read(playlist, "owner_v2", "data")
    return {
        "name": _read(playlist, "name"),
        "uri": _read(playlist, "uri"),
        "description": _read(playlist, "description"),
        "owner": _read(owner, "name"),
        "image_url": _read(image_sources[0], "url") if image_sources else None,
    }


async def _search(kind: str, query: str, limit: int) -> list[dict[str, Any]]:
    try:
        search = getattr(Public, f"search_{kind}")
        results: list[dict[str, Any]] = []
        async for page in search(query):
            for item in page:
                if kind == "tracks":
                    results.append(_serialize_track(item))
                elif kind == "artists":
                    results.append(_serialize_artist(item))
                elif kind == "albums":
                    results.append(_serialize_album(item))
                elif kind == "playlists":
                    results.append(_serialize_playlist(item))
                else:
                    raise ValueError(f"Unsupported search type: {kind}")
                if len(results) >= limit:
                    return results
        return results
    except Exception as exc:
        logger.exception("SpotAPI %s search failed", kind)
        raise HTTPException(
            status_code=502,
            detail="Spotify catalogue search is temporarily unavailable.",
        ) from exc


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "nexamusic-spotapi"}


@app.get("/v1/search/tracks")
async def search_tracks(
    q: str = Query(min_length=1, max_length=120),
    limit: int = Query(default=20, ge=1, le=50),
) -> dict[str, Any]:
    return {"query": q, "items": await _search("tracks", q, limit)}


@app.get("/v1/search/artists")
async def search_artists(
    q: str = Query(min_length=1, max_length=120),
    limit: int = Query(default=20, ge=1, le=50),
) -> dict[str, Any]:
    return {"query": q, "items": await _search("artists", q, limit)}


@app.get("/v1/search/albums")
async def search_albums(
    q: str = Query(min_length=1, max_length=120),
    limit: int = Query(default=20, ge=1, le=50),
) -> dict[str, Any]:
    return {"query": q, "items": await _search("albums", q, limit)}


@app.get("/v1/search/playlists")
async def search_playlists(
    q: str = Query(min_length=1, max_length=120),
    limit: int = Query(default=20, ge=1, le=50),
) -> dict[str, Any]:
    return {"query": q, "items": await _search("playlists", q, limit)}
