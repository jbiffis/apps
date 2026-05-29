# Privacy

This app stores personal health information. The architecture is designed so that **the only place that personal data exists is the Postgres data volume on the production server**.

## Trust boundaries

```
Production server (single VPS, Komodo-managed)
└── docker volume: /var/lib/docker/volumes/apps/tracker-db/
        ← THE ONLY PLACE PERSONAL DATA LIVES

Git repo (jbiffis/apps, this directory tree)
├── source code (Java, JS, SQL migrations, configs)
└── NO data files. No dumps. No backups. No personal info.

This dev machine (where Claude Code runs)
└── Cannot reach the prod server's Docker volumes.
    Cannot connect to the prod database.
    Only has the source code (git checkout).

Browser (Carley's, Jeremy's phone / laptop)
├── JWT in localStorage
└── React app state (current view's data only)
```

## What lives where

| Data | Location | Who/what can read it |
|---|---|---|
| Logged events, notes, options | `tracker-db` Postgres on prod | Spring Boot backend, authenticated as the owning user |
| `users.password_hash` | Same | Login service only (and authentication code in the backend) |
| Migration SQL | Git repo | Anyone with repo access (no personal data in migrations — schema and seed catalog only) |
| Plain-text passwords | Nowhere persistent | Set out-of-band when users are created; bcrypted on insert |
| JWT secret | `.env` on prod server (gitignored) | Backend process |
| DB password | `.env` on prod server (gitignored) | Backend + db containers |
| JWT (per-session) | User's browser `localStorage` | The user |

## What Claude (the AI) can and can't see

**Can read:** all source code in this repo, the structure (DDL), seed catalog, design docs, and any future commits. That's by design — Claude needs source to help with code.

**Cannot read:**
- Anything in the `tracker-db` Postgres volume on prod.
- Any user's logged events, notes, mood scores, medication history, periods, weight, etc.
- Plain-text passwords (none exist anywhere persistent).
- The contents of users' JWTs.

**Will not:**
- Attempt to query the prod database.
- Add endpoints that log user-supplied content (`note`, `options.value`).
- Add analytics, telemetry, or external API calls from the backend.
- Suggest moving the DB onto a shared network with other apps.

(See `tracker/CLAUDE.md` for the rules Claude follows when changing code here.)

## DB isolation

- `tracker-db` runs in its own container on a private Docker network `tracker-net`.
- **No host port is published.** You cannot `psql -h localhost` to it from the prod server's shell — you must `docker exec` into the container.
- It is not on the `data-tracking-globe` network — fully isolated from the other tracking apps' PostgREST DB.
- Volume is bind-mounted from `/var/lib/docker/volumes/apps/tracker-db/`. File system permissions on prod restrict it to root + the postgres container's user.

## Local development discipline

- **Never run with real personal data on this machine.** Use `compose.dev.yaml` which mounts a throwaway volume (`./.local-data/tracker-db/`, gitignored).
- Seed data for local dev uses fake users (Test User, Demo) — never `carley` or `jeremy`.
- Backend `application-dev.properties` may default to H2 in-memory for the fastest loop. Postgres-via-Docker for integration tests.
- If you `pg_dump` for any reason: dump goes outside the repo, gets deleted same day, never committed.

## .gitignore additions for tracker

```
tracker/.local-data/
tracker/backend/target/
tracker/backend/.idea/
tracker/backend/*.iml
tracker/frontend/node_modules/
tracker/frontend/dist/
tracker/**/*.db
tracker/**/*.sqlite*
tracker/**/dump.sql
tracker/**/.env
tracker/**/.env.local
```

## Initial user setup

Users `carley401@gmail.com` and `jeremy@biffis.com` are seeded by migration
`V4__seed_users.sql` (originally by username; `V7__email_login.sql` switched the
login identity to email). Both rows carry a **temporary password** and
`must_change_password = true`, so each user is forced to set their own password
on first login via the in-app change-password flow (`POST /auth/change-password`).

Two ways to (re)set a password:

```sh
# In-app: log in with the temp password → forced change screen → done.

# Out-of-band (operator reset), on the prod server:
docker compose exec tracker-backend java -jar app.jar set-password jeremy@biffis.com
```

(That CLI subcommand reads the new password from stdin, never echoes it, bcrypts
it, updates the row, and clears the force-change flag.) No real password lives in
the migration — only a throwaway temp hash the user replaces on first login.

## Threat model (Phase 1)

What this app defends against:
- Casual snooping of the URL or HTML — there's nothing to see without a valid JWT.
- One user reading another's data — enforced in every service method by `userId`-scoped queries.
- Public internet → DB — DB is on a private Docker network, no public port.
- Code or AI accidentally committing data — covered by `.gitignore`, code review, and the rules in `CLAUDE.md`.
- Stolen JWT (limited blast radius) — TTL 30 days. Phase 2 should add a rotate-on-suspicion mechanism.

What it explicitly does **not** defend against (would need separate work):
- Server-level compromise. If someone roots the VPS, they can read the DB volume.
- A malicious admin running `docker exec` and querying `logged_events` directly.
- Encrypted-at-rest on a stolen disk. Add `pgcrypto`/disk encryption later if the threat warrants.
- Phishing of user passwords.

## Backups (TBD)

There is no automated backup yet. The first backup story (Phase 1.5) should:
- Run `pg_dump` from a sidecar container on a cron schedule.
- Encrypt the dump (age / gpg) with a key only the owner holds.
- Push the encrypted dump off-server (S3-compatible, or a private B2 bucket).
- Never write unencrypted dumps to disk.
