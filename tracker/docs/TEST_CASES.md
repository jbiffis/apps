# Test cases

Manual + automated test coverage for the tracker backend. Automated tests
live in `backend/src/test/`; this doc is the **manual / acceptance** plan —
what to run by hand before calling an epic done or after a deploy.

Statuses: ✅ executable now (Epic 1–2 shipped) · ⏳ acceptance criteria for an
epic not yet built.

## Conventions

- `$BASE` = `http://localhost:8080/api` (local) or `https://apps.biffis.com/tracker/api` (prod).
- No frontend yet, so everything here is `curl`. Once Epics 5–9 land, each
  case gets a UI equivalent.
- Never run these against prod with real data unless explicitly noted as a
  prod-only check (deploy verification). Local uses a throwaway Postgres.
- `jq` assumed for readability; not required.

---

## How to stand up a throwaway backend (local)

Testcontainers can't reach Docker 29.x from the dev box (see EPICS.md), so
verify the prod way:

```bash
cd tracker/backend
# build (skip the testcontainers test which is blocked on this host)
docker run --rm -v "$PWD":/work -v "$HOME/.m2":/root/.m2 -w /work \
  maven:3.9-eclipse-temurin-21 mvn -B -q -DskipTests package

docker network create tracker-test
docker run -d --name tt-db --network tracker-test \
  -e POSTGRES_DB=tracker -e POSTGRES_USER=tracker -e POSTGRES_PASSWORD=testpw \
  postgres:16-alpine
docker run -d --name tt-app --network tracker-test -p 18080:8080 \
  -e DB_HOST=tt-db -e DB_USERNAME=tracker -e DB_PASSWORD=testpw \
  -e JWT_SECRET=test-secret-at-least-32-chars-long-aaaa \
  -v "$PWD/target/tracker-backend-0.0.1-SNAPSHOT.jar":/app.jar:ro \
  eclipse-temurin:21-jre java -jar /app.jar

# BASE for the cases below
export BASE=http://localhost:18080/api

# teardown when done
# docker rm -f tt-app tt-db && docker network rm tracker-test
```

Seeded login for local tests: `carley401@gmail.com` / `password` (temp password
reseeded by V7; same for `jeremy@biffis.com`). Both seeded rows have
`must_change_password = true`, so a fresh login returns `mustChangePassword: true`.

---

## Epic 1 — Backend scaffold + schema ✅

### TC-1.1 Health endpoint is public
- **When:** `curl -s $BASE/health`
- **Then:** `200`, body `{"ok":true,"app":"tracker","version":"0.0.1-SNAPSHOT"}`. No auth required.

### TC-1.2 All migrations applied
- **When:** check the running container's startup logs (`docker logs tt-app | grep Flyway`).
- **Then:** "Successfully applied 4 migrations", schema at v4, no `ddl-auto` validation error.

### TC-1.3 Seed data present
- **When (DB):** `docker exec tt-db psql -U tracker -d tracker -c "SELECT count(*) FROM property_presets;"` etc.
- **Then:** `property_presets` = 28, `event_types` = 54, `event_properties` = 61, `users` = 2.
  (V8 added the `weight-kg`/`height-cm` presets and the Weight/Height trackers.)

### TC-1.4 Schema matches entities (no drift)
- **When:** app boots with `spring.jpa.hibernate.ddl-auto=validate`.
- **Then:** context loads without a `SchemaManagementException`. (Failure here means an entity diverged from a migration.)

---

## Epic 2 — Auth (JWT) ✅

### TC-2.1 Login happy path
- **When:**
  ```bash
  curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
    -d '{"email":"carley401@gmail.com","password":"password"}'
  ```
- **Then:** `200`. Body has a non-empty `token`, `user: {id, email:"carley401@gmail.com", displayName:"Carley", gender:"female"}`, and `mustChangePassword: true` for a freshly-seeded row. **No `passwordHash` field anywhere.**

### TC-2.2 Wrong password
- **When:** same as TC-2.1 with `"password":"wrong"`.
- **Then:** `401`, body `{"error":"invalid_credentials"}`. Response time should be similar to a correct login (no obvious user-enumeration timing tell — bcrypt runs either way... see TC-2.3 note).

### TC-2.3 Unknown user
- **When:** `{"email":"nobody@biffis.com","password":"x"}`.
- **Then:** `401`, `{"error":"invalid_credentials"}` — **identical** to TC-2.2. The error must not reveal whether the email exists.
- *Note:* current impl returns early when the user is absent (skips bcrypt), a minor timing oracle. Acceptable for Phase 1 (LAN-only, low threat). Flag for Phase 2 hardening if exposed publicly.

### TC-2.4 Malformed login body
- **When:** `POST /auth/login` with `{}` or missing fields.
- **Then:** `400` (bean validation on `@NotBlank @Email email` / `@NotBlank password`). No stack trace in the body (`server.error.include-stacktrace=never`).

### TC-2.5 Protected endpoint without token
- **When:** `curl -s -o /dev/null -w '%{http_code}' $BASE/auth/me`
- **Then:** `401`. No `WWW-Authenticate: Basic` header (must not trigger a browser basic-auth popup).

### TC-2.6 Protected endpoint with valid token
- **When:** login, capture `token`, then `curl $BASE/auth/me -H "Authorization: Bearer $TOKEN"`.
- **Then:** `200`, body identifies the logged-in user (`email`).

### TC-2.7 Protected endpoint with garbage / tampered token
- **When:** `…/auth/me -H "Authorization: Bearer not.a.jwt"` and a token with a flipped character.
- **Then:** `401` in both cases. A token signed with a different secret must also fail.

### TC-2.8 Expired token (manual / future)
- **When:** issue a token with a very short TTL (set `tracker.jwt.ttl-days` low, or craft one), wait past expiry, call `/auth/me`.
- **Then:** `401`. (Default TTL is 30d; this is an occasional check, not every run.)

### TC-2.9 set-password CLI rotates the hash
- **When:**
  ```bash
  docker exec -i tt-app sh -c 'echo "newpass123" | java -jar /app.jar set-password carley'
  ```
- **Then:** prints "Password updated for carley." Login with the OLD password → `401`; login with `newpass123` → `200`. (Regression guard: a prior bug rolled this back because `System.exit` fired before the tx committed.)

### TC-2.10 set-password unknown user
- **When:** `… set-password ghost`.
- **Then:** prints "No such user: ghost", non-zero exit, no DB change.

### TC-2.11 JWT secret length guard
- **When:** boot the app with `JWT_SECRET` shorter than 32 bytes.
- **Then:** startup fails fast with a clear error (HS256 needs ≥256-bit key). The app must not start with a weak secret.

---

## Epic 3 — Catalog API ⏳ (acceptance criteria)

### TC-3.1 Event-type tree shape
- `GET /event-types` → top-level categories, each with nested `children`; leaves carry `properties[]` with hydrated `preset`.

### TC-3.2 Audience filtering by gender
- As `jeremy` (male): the female-only category (e.g. "Lady stuff") is **absent** by default.
- As `carley` (female): it **is** present.
- `GET /event-types?include=all` returns everything regardless of gender — confirm it's read-only visibility, not access control.

### TC-3.3 Create a shared event type
- `POST /event-types` (e.g. a new medication) as `carley` → `201`. `GET` as `jeremy` shows it too (catalog is shared).

### TC-3.4 Delete permission rules
- Creator can `DELETE` their own non-seed type → `204`.
- Deleting a seed type → `403`. Deleting someone else's type → `403`.

### TC-3.5 Property presets
- `GET /property-presets` → all 28, each with a valid `widget` + `options` jsonb of the right shape for its widget.

---

## Epic 4 — Logged events API ⏳ (acceptance criteria) — **privacy-critical**

### TC-4.1 Save + read round-trip
- `POST /logged-events` (e.g. Advil, dose 2, "after lunch") → `201`. `GET /logged-events/{id}` returns it with options hydrated.

### TC-4.2 Cross-user isolation (the important one)
- As `carley`, create an event; note its id.
- As `jeremy`, `GET /logged-events/{thatId}` → `404` (not 403 — we don't confirm the id exists).
- As `jeremy`, `GET /logged-events` (list) → does **not** include carley's event.
- **Any** endpoint touching `logged_events` must filter by the caller's `user_id`. There is no `findAll`.

### TC-4.3 Date-range + type filters
- `GET /logged-events?from=&to=` honors the half-open `[from,to)`. `?eventTypeSlug=` filters to one tracker. `limit` caps results (default 50, max 200).

### TC-4.4 Home aggregates
- `GET /home/hero` → 3 cards (medication/water/sleep) with correct progress for the caller's *own* data.
- `GET /home/today` → today's entries for the caller only, oldest→newest, max 10.

### TC-4.5 Delete
- `DELETE /logged-events/{id}` for own event → `204`, gone on next read. Another user's id → `404`.

---

## Epics 5–9 — Frontend ⏳ (acceptance criteria, fill in when built)

- **Login screen:** unauthenticated visit to any path → `/tracker/login`; valid creds → home; logout clears JWT.
- **Home:** greeting, 3 hero cards, "All trackers" grid (audience-filtered), "Today" feed, all from the logged-in user's data. Dark mode persists.
- **Entry:** each widget kind renders + saves; time chips work; 5s undo deletes the entry if tapped; "Save another" resets the form.
- **History:** last 30 days grouped by day; tap → read-only detail.
- **PWA:** installable; loads in light + dark.

---

## Deploy verification (prod) — run after every deploy

Order matters — do these on prod after `docker compose build tracker-backend` + Komodo restart, **with real `TRACKER_DB_PASSWORD` + `TRACKER_JWT_SECRET` set first**:

1. `curl -s https://apps.biffis.com/tracker/api/health` → `200`.
2. Backend logs: Flyway applied any pending migrations cleanly; no validation error.
3. Set real passwords (first deploy only): `set-password carley`, `set-password jeremy`. Confirm the placeholder password no longer works (TC-2.9 pattern).
4. Login as each user → `200` + token. `/auth/me` with the token → `200`.
5. `/auth/me` without a token → `401`.
6. From a phone on the LAN: same login round-trip (sanity that the proxy path `/tracker/api/*` is wired).

If any step fails, **do not** announce the app to users — roll back to the previous image (Komodo) and investigate.
