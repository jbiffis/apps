# Tracker test environment

A self-contained, **local** stack for clicking through the app with a realistic
dataset. Isolated from the prod `tracker/compose.yaml` (own Docker project,
ephemeral volume, throwaway secrets) — safe to blow away anytime.

## Run

```bash
cd tracker/testenv
./run.sh           # build + start + seed (~90 days of data)
./run.sh --fresh   # wipe volumes first for a clean DB
```

Then open `http://<this-host-ip>:18080/tracker/` (the script prints the URL).

## Users

Four seeded users, all with password `changeme-on-first-login`:

| user   | gender | notes |
|--------|--------|-------|
| carley | female | sees `lady-stuff` trackers |
| jeremy | male   | |
| morgan | female | added by `run.sh` |
| dave   | male   | added by `run.sh` |

## What's in it

- **Postgres** (`tracker-test-db`) — Flyway runs the real migrations, so the
  full seed catalog + carley/jeremy exist on first boot.
- **Backend** (`tracker-test-backend`) — the real image (`../backend`).
- **nginx** (`tracker-test-web`) — serves the built SPA at `/tracker/` and
  reverse-proxies `/tracker/api` to the backend, mirroring the prod path.

`seed.mjs` logs in as each user, reads their gender-filtered catalog, and posts
~90 days of entries through the REST API at realistic cadences (water several
times a day, meals/coffee daily, sleep nightly, occasional headaches/workouts/
weight, etc.). It's idempotent: `run.sh` TRUNCATEs logged events before each
seed. Tune with env vars (`TRACKER_TEST_DAYS`, `TRACKER_TEST_USERS`, …).

## Notes

- This is **test-only**. The secrets in `compose.test.yaml` are throwaway; the
  prod stack keeps its real secrets in its own (agent-fenced) Komodo stack.
- The whole `tracker/` tree is in the repo's root `.dockerignore`, so none of
  this is baked into the shared Apache image.
