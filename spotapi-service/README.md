# NexaMusic SpotAPI service

This folder contains a small Python service that adapts SpotAPI's **public catalogue search** for the Android app.

## Included endpoints

- `GET /health`
- `GET /v1/search/tracks?q=...`
- `GET /v1/search/artists?q=...`
- `GET /v1/search/albums?q=...`

Each search accepts an optional `limit` from 1 to 50 (default 20). Responses use a small stable JSON shape for the Kotlin client.

## Run locally

Requires Python 3.11+.

```bash
cd spotapi-service
python -m venv .venv
# Activate the virtual environment, then:
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

The service will be available at `http://127.0.0.1:8000`; interactive API docs are at `/docs`.

## Deploy

A Render Blueprint is included at the repository root (`render.yaml`). In Render, create a new Blueprint from this repository and deploy the `nexamusic-spotapi` web service. Wait for its `/health` check to pass, then copy the HTTPS service URL.

Configure Android builds with `SPOTAPI_BASE_URL` in `local.properties` or as an environment variable. The app injects this into `BuildConfig`; leave it empty until the service has a real HTTPS URL. The Kotlin client intentionally refuses non-HTTPS URLs. Set `NEXAMUSIC_CORS_ORIGINS` only if browser clients need access; native Android requests do not use browser CORS.

## Scope and limitations

- Uses SpotAPI's async v2 public search interface (`spotapi==2.0.0b2`).
- No Spotify account login, passwords, cookies, CAPTCHA services, or private account endpoints are included.
- Returns catalogue metadata only. It does **not** provide Spotify audio streams or playback.
- YouTube playback and NexaMusic's existing player remain separate.
- SpotAPI v2 is a beta release; upstream changes may require adapter updates.
- SpotAPI is GPL-3.0. Review its license and obligations before distributing a combined service or derivative work.

Upstream: https://github.com/Aran404/SpotAPI
