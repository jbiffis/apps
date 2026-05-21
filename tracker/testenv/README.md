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

Four seeded users, all with password `test123` (test-only; set by `run.sh`):

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

## Headless E2E (Playwright)

Real browser tests of the SPA, driven by the **official Playwright Docker image**
(browsers preinstalled — nothing installed on the host) against this running
stack. Specs live in `../frontend/e2e/` (config `../frontend/playwright.config.js`).

```bash
./run.sh                 # bring the env up to current code + seed data
( cd ../frontend && npm install )   # once: installs the @playwright/test runner
./e2e.sh                 # run all specs headless (in-network: http://tracker-test-web/tracker/)
./e2e.sh e2e/auth.spec.js   # run one spec
E2E_BASE_URL=http://<host>:18080/tracker/ ./e2e.sh   # target a remote env
```

`e2e.sh` runs `mcr.microsoft.com/playwright:v1.49.1-jammy` joined to the test
env's compose network (`tracker-test_default`), hitting the web service by name
(`http://tracker-test-web/tracker/`) — container-to-container, which avoids the
host-loopback/IPv6/sandbox issues that break Chromium under `--network host`.
It mounts `../frontend` so it uses the locally-installed `@playwright/test`
(version pinned to match the image's browsers). Specs log in as `carley/test123`
and cover auth, logging an entry (FAB → picker → fill required field → save),
the Stats tab, and the Me-tab hide/show round-trip. Failures retain a trace +
screenshot under `../frontend/test-results/`.
