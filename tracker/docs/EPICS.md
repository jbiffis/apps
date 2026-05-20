# Epics — status tracker

> **Resumable work log.** If a new agent picks this up, read this file first, then `README.md` + `CLAUDE.md`. Update this file as work progresses.

## Status legend

- 🟢 **Done** — merged to main
- 🟡 **In progress** — actively being worked on; see "Current work" below
- 🔵 **Blocked** — waiting on external input (decision, secret, deploy access)
- ⚪ **Not started**

## Current work

**Epic:** 2 — Auth (Spring Security + JWT) **(starting)**
**Started:** 2026-05-20
**Branch:** `claude/event-tracking-app-RiLUG`

**Epic 1 signed off 2026-05-20.** Verified on the aivm box (has Docker 29.3.1).

> **Smoke-test note for future agents:** `./mvnw test` (Testcontainers) does **not** work against very new Docker daemons (29.x) with this Testcontainers version — the bundled docker-java client negotiates API 1.32, which the daemon rejects ("client version 1.32 is too old, minimum 1.40"). Pinning `DOCKER_API_VERSION` didn't help. Instead of fighting it, Epic 1 was verified the prod-equivalent way: built the jar (`mvn -DskipTests package`), booted it against a throwaway `postgres:16-alpine` container on a private network, and confirmed:
> - Flyway **applied all 4 migrations** cleanly, `validate` passed (no Hibernate drift).
> - All 6 tables + `flyway_schema_history` created.
> - Seed counts: **26** property_presets, **52** event_types, **59** event_properties, **2** users.
> - `GET /api/health` → `{"ok":true,"app":"tracker","version":"0.0.1-SNAPSHOT"}`.
>
> If someone wants the Testcontainers path green, bump Testcontainers (and transitively docker-java) — but that needs sign-off per CLAUDE.md ("don't bump versions unless asked").

**Epic 2 plan:** replace permit-all `SecurityConfig` with a stateless JWT filter chain. `User` entity/repo/service, `JwtService` (HS256, secret from `tracker.jwt.secret`), `JwtAuthFilter`, `CurrentUser` helper, `AuthController` (`POST /api/auth/login`), DTOs as records. Only `/api/auth/**` permit-all; everything else authenticated. BCrypt password checks. Testcontainers tests for login happy-path / wrong-password 401 / missing-token 401 (will run them via the real-postgres boot pattern above since Testcontainers itself is blocked).

**If you need to take over:** `git log` this branch for the latest commit, then update this section.

---

## Phase 1 epics

Scope: **Home + Entry + basic history**, two seeded users (Carley, Jeremy). Stats/Me/long-press-reorder are Phase 2.

### Epic 1 — Backend scaffold + schema 🟢 (signed off 2026-05-20)

Set up the Spring Boot project, get Postgres talking, run migrations, expose a health check.

- [x] `tracker/backend/pom.xml` (Spring Boot 3.4.1, Java 17, web, jpa, security, validation, flyway, postgres, h2-test, testcontainers, jjwt)
- [x] `tracker/backend/mvnw` + `mvnw.cmd` + `.mvn/wrapper/`
- [x] `tracker/backend/Dockerfile` (multi-stage temurin 17 jdk → jre, runs as non-root `tracker` user)
- [x] `tracker/backend/run.sh`
- [x] `tracker/backend/.gitignore`
- [x] `tracker/backend/.dockerignore`
- [x] `src/main/java/com/biffis/tracker/TrackerApplication.java`
- [x] `src/main/resources/application.properties` (env-driven DB + app config)
- [ ] ~~`src/main/resources/application-dev.properties` (H2 in-memory)~~ — skipped; H2 doesn't support `gen_random_uuid()` or `jsonb`, so dev uses real Postgres via compose
- [x] `src/main/resources/db/migration/V1__schema.sql` (all 6 tables + indexes + FKs + CHECK constraints)
- [x] `src/main/resources/db/migration/V2__seed_presets.sql` (26 presets)
- [x] `src/main/resources/db/migration/V3__seed_event_types.sql` (full catalog from `SEED_CATALOG.md`)
- [x] `src/main/resources/db/migration/V4__seed_users.sql` (Carley + Jeremy with placeholder bcrypt; passwords reset out-of-band on prod)
- [x] `config/SecurityConfig.java` — Epic 1 permit-all placeholder; Epic 2 will replace
- [x] `controller/HealthController.java` — `GET /api/health` → `{"ok": true, "app": "tracker", "version": "0.0.1-SNAPSHOT"}`
- [x] `compose.yaml` additions: `tracker-backend`, `tracker-db`, `tracker-net` network, `tracker-uploads` volume; mount `tracker-proxy.conf` into `web`
- [x] `tracker-proxy.conf` at repo root (API only for now; frontend block commented out for Epic 5)
- [x] `Dockerfile` updated to COPY new proxy conf
- [x] `.env.example` (top-level) with `TRACKER_DB_PASSWORD`, `TRACKER_JWT_SECRET` keys (no values)
- [x] Top-level `.gitignore` (covers `.env`, tracker dev artifacts)
- [x] `tracker/` added to `.dockerignore` (prevent source from being bundled into Apache image)
- [x] `mvnw package` builds cleanly
- [x] `TrackerApplicationTests` smoke test (Testcontainers Postgres + Spring context load + Flyway run)
- [x] **Smoke test on a machine with Docker** — Testcontainers blocked by Docker 29.x API-version mismatch (see Current work note); verified instead by booting the jar against a throwaway `postgres:16-alpine`: all 4 migrations applied, `validate` passed, health 200, seeds correct (26/52/59/2).
- [ ] **Verify on prod** (`curl https://apps.biffis.com/tracker/api/health` returns 200) — gated on deploy; tracked in Epic 10.
- [x] Commit + push (Epic 1 code)

**Definition of done:** `docker compose up tracker-db tracker-backend` from a clean checkout boots the API, Flyway applies all four migrations, `curl https://apps.biffis.com/tracker/api/health` returns 200 on prod.

---

### Epic 2 — Auth (Spring Security + JWT) ⚪

Per-user login, JWT issuance, auth filter, current-user helper.

- [ ] `model/User.java` (entity)
- [ ] `repository/UserRepository.java`
- [ ] `service/UserService.java` (load by username, password verification helper)
- [ ] `config/SecurityConfig.java` (stateless, JWT filter chain, CSRF off, /api/auth/** permit-all)
- [ ] `security/JwtService.java` (HS256, sign + parse + validate)
- [ ] `security/JwtAuthFilter.java`
- [ ] `security/CurrentUser.java` helper (`@AuthenticationPrincipal` or `SecurityContextHolder` wrapper)
- [ ] `controller/AuthController.java` — `POST /api/auth/login` → `{token, user}`
- [ ] `dto/LoginRequest.java`, `dto/LoginResponse.java`, `dto/UserView.java`
- [ ] CLI subcommand or one-shot tool for setting passwords on prod (described in PRIVACY.md)
- [ ] Tests: login happy path, wrong password, missing token on protected endpoint

**Definition of done:** Logging in as `carley` returns a working JWT that authenticates subsequent requests; bad creds return 401; unauthenticated calls to protected endpoints return 401.

---

### Epic 3 — Catalog API ⚪

Read + write `event_types`, `event_properties`, `property_presets`. Tree response for home.

- [ ] `model/EventType.java` (self-referential parent, audience enum, is_category, is_seed)
- [ ] `model/EventProperty.java`
- [ ] `model/PropertyPreset.java` (jsonb options column via `@JdbcTypeCode(SqlTypes.JSON)`)
- [ ] Repositories + services with audience filtering by current user gender
- [ ] `controller/EventTypeController.java`:
    - `GET /api/event-types` (tree, audience-filtered; `?include=all` bypass)
    - `GET /api/event-types/{slug}` (single, with properties)
    - `POST /api/event-types` (create new, shared)
    - `DELETE /api/event-types/{id}` (creator-only, non-seed only)
- [ ] `controller/PropertyPresetController.java` — `GET /api/property-presets`
- [ ] DTOs in `dto/`
- [ ] Tests: tree shape, audience filter for Carley vs Jeremy, create + delete permission rules

**Definition of done:** `GET /api/event-types` as Jeremy excludes the Lady stuff category by default; same call as Carley includes it. New medication created via POST is visible to both users.

---

### Epic 4 — Logged events API ⚪

Save and read entries. **User-scoped at every layer** — there is no `findAll`.

- [ ] `model/LoggedEvent.java`
- [ ] `model/LoggedEventOption.java`
- [ ] Repositories: `findByUserIdAndOccurredAtBetween`, `findByUserIdAndIdOrThrow`, etc. No unbounded queries.
- [ ] `service/LoggedEventService.java`
- [ ] `controller/LoggedEventController.java`:
    - `POST /api/logged-events`
    - `GET /api/logged-events?from=&to=&eventTypeSlug=&limit=`
    - `GET /api/logged-events/{id}`
    - `DELETE /api/logged-events/{id}`
- [ ] `controller/HomeController.java`:
    - `GET /api/home/hero` (3 hardcoded trackers — medication, water, sleep — with progress)
    - `GET /api/home/today` (today's entries for the feed)
- [ ] DTOs
- [ ] Tests:
    - Save + read round-trip
    - **Cross-user isolation**: Carley cannot read Jeremy's events; 404 on direct GET of another user's id
    - Filter by date range, by event type
    - Hero/Today aggregates produce correct shapes

**Definition of done:** End-to-end with Postman/curl: log in, POST an entry, GET it back; logging in as the other user returns 0 events.

---

### Epic 5 — Frontend scaffold ⚪

React + Vite SPA (JS) with Tailwind, PWA, JWT-aware fetch client, palette tokens, icon set.

- [ ] `tracker/frontend/package.json`, `vite.config.js` (`base: '/tracker/'`, vite-plugin-pwa)
- [ ] `tracker/frontend/Dockerfile` (node build → nginx)
- [ ] `tracker/frontend/nginx.conf`
- [ ] `tracker/frontend/eslint.config.js`, `postcss.config.cjs`, `tailwind.config.js`
- [ ] `index.html` with Google Fonts preconnect (Nunito + DM Sans + JetBrains Mono)
- [ ] `src/main.jsx`, `src/App.jsx`
- [ ] `src/theme.css` (CSS variables from `docs/DESIGN.md`, light + dark)
- [ ] `src/api.js` — fetch wrapper with `Authorization: Bearer …`, 401 → redirect to login
- [ ] `src/auth.js` — JWT storage helpers
- [ ] `src/icons/` — port `polished-icons.jsx` to typed JSX components
- [ ] PWA manifest icons (need source artwork — see Phase 1.5 in docs)
- [ ] `compose.yaml` add `tracker-frontend` service
- [ ] `tracker-proxy.conf` updated to serve `/tracker/` from frontend container

**Definition of done:** `https://apps.biffis.com/tracker/` loads a styled "Hello" page in both light + dark, installable as a PWA.

---

### Epic 6 — Login screen ⚪

- [ ] `src/pages/Login.jsx` (form + submit + error display)
- [ ] Auth guard wrapper component
- [ ] Logout (clears JWT, redirects to /login)

**Definition of done:** Visiting any path while unauthenticated lands on /tracker/login. Valid creds redirect to home.

---

### Epic 7 — Home screen ⚪

- [ ] `components/AppShell.jsx` (app bar + content + bottom nav layout)
- [ ] `components/BottomNav.jsx` (Home/Log/Stats/Me — only Home wired)
- [ ] `pages/Home.jsx`:
    - Greeting (Thursday · Apr 23 / Good morning, Carley.)
    - Top-3 hero cards from `/api/home/hero`
    - "All trackers" 4-col grid from `/api/event-types` tree
    - "Today" list from `/api/home/today`
    - FAB
- [ ] `components/TrackerPickerSheet.jsx` — bottom sheet shown when tapping a category tile or the FAB
- [ ] Dark mode toggle in Me-tab placeholder or app bar menu, persisted in localStorage

**Definition of done:** Home screen visually matches `lifetracker-polished.html` and pulls real data for the logged-in user.

---

### Epic 8 — Entry screen ⚪

- [ ] `pages/Entry.jsx` route `/log/:slug`
- [ ] `components/FieldRenderer.jsx` — switch on `preset.widget`:
    - `step`, `single_select`, `multi_select`, `face_select` → chip/pill groups
    - `number`, `dose`, `duration` → stepper + free input
    - `text` → textarea
    - `bool` → toggle
- [ ] `components/TimeChips.jsx` (Just now / 15m / 1h / Yesterday / Pick) + native datetime picker fallback
- [ ] Save flow: POST → navigate home → toast with 5s undo (DELETE on undo, no network call if not undone within the window)
- [ ] "Save another" — stays on screen, resets form
- [ ] Validation errors surfaced inline

**Definition of done:** Can log a Headache (severity + locations + note), a medication (dose + with food), Water (count), and a Mood face. Entries appear in Today feed.

---

### Epic 9 — History screen ⚪

- [ ] `pages/History.jsx` route `/history`
- [ ] Grouped by day (Today / Yesterday / dated)
- [ ] Tap row → read-only detail modal/page

**Definition of done:** Last 30 days of entries visible, grouped, readable.

---

### Epic 10 — Deploy + initial users ⚪

- [ ] Push branch, merge to main
- [ ] On prod: `docker compose build tracker-backend tracker-frontend`
- [ ] Komodo restart
- [ ] Verify migrations ran
- [ ] Set Carley's + Jeremy's real passwords via the CLI subcommand
- [ ] Smoke test login + log an entry from a phone
- [ ] Add PWA to home screen
- [ ] Document any surprises in this file

**Definition of done:** Carley and Jeremy can log in on their phones at `apps.biffis.com/tracker`, save an entry, and see it in history.

---

## Out of scope (Phase 2+)

Logged here so they don't get sneaked in:

- Stats tab (charts, streaks, heatmaps, correlations)
- Me tab (profile, hide/show trackers, export to CSV/JSON)
- Long-press to drag/reorder home tiles, create categories
- Smart suggestions / time-of-day pinning
- Notifications / reminders
- Sharing / read-only doctor link
- Goals & streaks as first-class fields
- Encrypted backups to off-site storage (Phase 1.5 — recommended sooner than later)

---

## Cross-cutting follow-ups

Track small tech debt or follow-ups discovered during epic work here. Each should become its own epic or a bullet under an existing one once big enough.

- *(empty)*
