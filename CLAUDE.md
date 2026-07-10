# Webapps

Simple PHP web applications served via Apache in Docker.

## Architecture

- **Dockerfile** — PHP 8.3 + Apache image, copies all app folders into `/var/www/html/`
- **compose.yaml** — Docker Compose config, maps port 8081
- Each app lives in its own folder (e.g., `beer/`)

## Deployment

Deployed via **Komodo** on a remote server. Komodo pulls from `git@github.com:jbiffis/apps.git` and restarts the container.

**Important:** Komodo does NOT rebuild the Docker image on deploy — it only restarts the container. PHP source files are served via bind mount (`./beer:/var/www/html/beer`), so code changes take effect on pull + restart. However, changes to `Dockerfile` or `apache-security.conf` require a manual image rebuild.

## Beer Order App (`beer/`)

Easter beer order form for Dépanneur Rapido specials.

### Files

- `beers.php` — Shared beer data, helper functions, CSRF/validation utilities
- `index.php` — Order form with live-updating totals
- `confirm.php` — Order confirmation and save
- `admin.php` — Password-protected admin view (orders, payment tracking, summary)
- Orders stored in `orders.json` at `/var/www/data/beer/` (outside web root, bind-mounted from `/var/lib/docker/volumes/apps/beer/` on host)

### Environment Variables

- `ADMIN_PASSWORD` — Required for admin page access

### Adding Beers

Beer IDs are array indexes in `beers.php`. **Always append new beers to the end of the array** to avoid breaking existing orders that reference IDs.

## Golf Stats App (`golf/`)

Viewer for Garmin golf rounds, parsed from the watch's local `.FIT` files (no
Garmin cloud/API). Served at `/golf/`. Pure PHP, self-contained (no external
assets/JS libraries; charts and the round map are inline SVG).

### Files

- `fit.php` — dependency-free FIT binary decoder (`FitFile::parse()`): header,
  definition/data messages, all base types, endianness, arrays, strings,
  compressed-timestamp headers, developer fields, CRC. Reusable for any FIT file.
- `golf.php` — golf interpretation + storage + aggregation. Understands Garmin's
  golf messages (verified against real device files):
  - msg **190** course (name, par, tee, slope, rating, length)
  - msg **193** hole definition (number, length, par, stroke index, pin GPS)
  - msg **192** hole result (number, score, putts — putts field auto-detected
    per device by cross-checking the msg 191 summary total)
  - msg **191** round summary (player, front/back/total score, total putts)
  - msg **194** shot-by-shot GPS; msg **18/20** activity session + GPS/HR track
- `_ui.php` — shared page chrome, score badges, inline SVG scorecard + heart-rate
  charts, and the round map. The map uses **Leaflet** (vendored locally at
  `golf/vendor/leaflet/`, not a CDN) over an aerial tile layer (Esri World
  Imagery, with an OpenStreetMap street toggle); the track/shots/pins are
  overlaid as vectors. Only the map tiles are fetched externally, by the
  viewer's browser at view time — the app itself needs no outbound network.
- `index.php` — dashboard (aggregate stats, scoring trend, round list, upload).
- `upload.php` — validates + parses uploaded `.FIT` files, persists a round.
- `round.php` — single-round detail (scorecard, activity, map, raw messages).
- `delete.php` — removes a round.

### Data

Parsed rounds are stored as JSON in `/var/www/data/golf/rounds/` (outside web
root, bind-mounted from `/var/lib/docker/volumes/apps/golf/` on host — mirror the
beer app's host-dir setup and ensure it's writable by `www-data`/uid 33). If the
dir isn't writable the app degrades gracefully to per-session (ephemeral) storage.

### Two files per round

Garmin splits a golf round across an **activity** file (`ACTIVITY_*.fit` — GPS
track, HR, time, distance) and a **scorecard** file (`SCORE_*` /
`Golf-SCORECARD_RAWDATA-*.fit` — hole scores, putts, par, pins, shots). The app
merges both by local date into one round when both are uploaded.

## Book-Log App workflow

When making changes to the `book-log/` app on behalf of the owner's daughter, after committing and pushing to the feature branch, also merge the feature branch into `main` and push `main` so the auto-deploy is triggered. Sequence: commit on feature branch → push feature branch → checkout main → pull → merge feature branch (no-ff) → push main → checkout feature branch.
