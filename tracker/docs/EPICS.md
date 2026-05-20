# Epics — status tracker

> **Resumable work log.** If a new agent picks this up, read this file first, then `README.md` + `CLAUDE.md`. Update this file as work progresses.

## Status legend

- 🟢 **Done** — merged to main
- 🟡 **In progress** — actively being worked on; see "Current work" below
- 🔵 **Blocked** — waiting on external input (decision, secret, deploy access)
- ⚪ **Not started**

## Current work

**Epic:** 6 — Login screen **(not started)**
**Branch:** Epics 1-5 are merged to `main`.

**Epics 1–5 signed off 2026-05-20.** Java is 21. Backend (1-4) on `main` with a green full-suite run (proof in Epic 4). Epic 5 frontend scaffold on `main`: Vite SPA builds + serves; `dist/` is committed and bind-mounted into the shared Apache (proof in Epic 5).

**Test infra note:** the JUnit suite now runs two ways — Testcontainers (CI / Docker-compatible hosts) *or* against an external Postgres via `TRACKER_TEST_DB_URL` (this dev box, where Testcontainers can't talk to Docker 29.x). `AbstractIntegrationTest` picks automatically. The reproduce recipe is in the Epic 4 proof block.

**Frontend dev:** `cd tracker/frontend && npm install && npm run dev` (Vite at :5173, proxies `/tracker/api`→`localhost:8080/api`). After any frontend change, `npm run build` and **commit `dist/`** — Komodo deploy is restart-only and bind-mounts the committed build. The agent is fenced out of the prod `tracker` Komodo stack, so deploy + the in-browser visual check stay with the owner.

**Next (Epic 6): Login screen** — build on `src/api.js` (`auth.login`) + `src/auth.js` (`setSession`). React Router is installed. Read `docs/DESIGN.md` (fields/buttons) and `docs/API.md` (`POST /auth/login` → `{token, user}`, 401 `invalid_credentials`).

**Epic 3 recap (Catalog API):** `GET /api/event-types` (audience-filtered tree, `?include=all` bypass), `GET /api/event-types/{slug}`, `POST /api/event-types` (creates a shared non-seed type, auto-slugified from name), `DELETE /api/event-types/{id}` (creator-only, non-seed-only), `GET /api/property-presets`. Verified against throwaway Postgres: 26 presets, tree with categories+children, `lady-stuff` (female) visible to carley / hidden from jeremy / shown to jeremy with `?include=all`, leaf hydration (headache → severity-1-5 + headache-location-multi), create→204-delete by creator, seed delete→403, unknown→404.

Design notes for Epic 3 (mirror these going into Epic 4):
- `EventType`/`EventProperty` use **plain UUID** `parentId`/`createdBy`/`presetId` (not @ManyToOne) so `CatalogService` assembles the tree from 3 bulk queries — no lazy loading (open-in-view is off).
- `PropertyPreset.options` is a `JsonNode` mapped with `@JdbcTypeCode(SqlTypes.JSON)` — serializes as raw JSON in responses.
- `UserService` split out from `AuthService` (as predicted) — `currentGender()` drives audience filtering.
- Errors: added `exception/` package + `ApiExceptionHandler` (@RestControllerAdvice) for the uniform `{error,message}` shape (not_found/forbidden/conflict/validation_failed). Epic 4+ should throw these.

**Next (Epic 4): Logged events API — privacy-critical.** Every read/write MUST filter by `CurrentUser.id()`. No `findAll` on logged_events. See `docs/API.md` logged-events + home sections and the TC-4.x cross-user isolation cases in `docs/TEST_CASES.md`.

**Backend smoke-test note** (still relevant): Testcontainers can't reach Docker 29.x from this box (docker-java negotiates API 1.32, daemon needs ≥1.40). Two ways around it now: (a) the full JUnit suite via `TRACKER_TEST_DB_URL` against an external Postgres (see Epic 4 proof block), or (b) build the jar and boot against a throwaway `postgres:16-alpine` for manual curl checks.

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

- [x] `tracker/backend/pom.xml` (Spring Boot 3.4.1, Java 21, web, jpa, security, validation, flyway, postgres, h2-test, testcontainers, jjwt)
- [x] `tracker/backend/mvnw` + `mvnw.cmd` + `.mvn/wrapper/`
- [x] `tracker/backend/Dockerfile` (multi-stage temurin 21 jdk → jre, runs as non-root `tracker` user)
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

### Epic 2 — Auth (Spring Security + JWT) 🟢 (signed off 2026-05-20)

Per-user login, JWT issuance, auth filter, current-user helper.

- [x] `model/User.java` (entity)
- [x] `repository/UserRepository.java`
- [x] `service/AuthService.java` (login: load by username + bcrypt verify + issue token) — folded the planned `UserService` responsibilities in here; a separate UserService can split out in Epic 3 when catalog needs user lookups by gender.
- [x] `config/SecurityConfig.java` (stateless, JWT filter chain, CSRF off). **Refinement:** only `/api/auth/login` + `/api/health` are permit-all, not all of `/api/auth/**` — `/api/auth/me` requires a token. `@ConditionalOnWebApplication` so the CLI run doesn't build a servlet chain. `PasswordEncoder` lives in `config/PasswordConfig.java` (always-on, used by both web + CLI).
- [x] `security/JwtService.java` (HS256, sign + parse + validate; secret ≥32 bytes enforced at startup)
- [x] `security/JwtAuthFilter.java`
- [x] `security/CurrentUser.java` helper + `security/AuthUser.java` principal record
- [x] `controller/AuthController.java` — `POST /api/auth/login` → `{token, user}` + `GET /api/auth/me`
- [x] `dto/LoginRequest.java`, `dto/LoginResponse.java`, `dto/UserView.java` (records; UserView never carries the hash)
- [x] CLI: `cli/SetPasswordRunner.java` + `TrackerApplication` runs `set-password <user>` as `WebApplicationType.NONE`. Reads stdin (no echo on a TTY), bcrypts, updates. **Gotcha fixed:** must NOT be `@Transactional` while calling `System.exit` — exit kills the JVM before the tx commits, silently rolling back the write. Spring Data `save()` commits per-call without it.
- [x] Tests: `AuthControllerTest` — login happy path, wrong password 401, unknown user 401, missing token 401, valid token 200, garbage token 401. (`AbstractIntegrationTest` base shares one Testcontainers Postgres.)

**Definition of done:** ✅ verified on aivm (Java 21 jar booted against throwaway Postgres). Login as `carley`/`jeremy` returns a working JWT; `/api/auth/me` returns 200 with the token, 401 without; wrong/unknown creds return 401 `{"error":"invalid_credentials"}`; `set-password` rotates the hash (old pw → 401, new pw → 200 after).

---

### Epic 3 — Catalog API 🟢 (signed off 2026-05-20)

Read + write `event_types`, `event_properties`, `property_presets`. Tree response for home.

- [x] `model/EventType.java` (plain-UUID parent/createdBy, audience, is_category, is_seed)
- [x] `model/EventProperty.java`
- [x] `model/PropertyPreset.java` (jsonb options via `@JdbcTypeCode(SqlTypes.JSON)` on a `JsonNode`)
- [x] Repositories + `CatalogService` with audience filtering by current user gender (+ `UserService` split)
- [x] `controller/EventTypeController.java`: tree (`?include=all`), `/{slug}`, POST create, DELETE
- [x] `controller/PropertyPresetController.java` — `GET /api/property-presets`
- [x] DTOs in `dto/` (records) + `exception/` package & `ApiExceptionHandler` for uniform errors
- [x] Tests: `CatalogControllerTest` — tree shape, audience (carley vs jeremy + include=all), leaf hydration, preset count, create + delete (creator 204 / seed 403 / unknown 404), unauth 401

**Definition of done:** ✅ verified vs throwaway Postgres — Jeremy's default tree excludes `lady-stuff`, Carley's includes it, `?include=all` shows it to both; a POST-created type (audience defaults to `all`) is in the shared tree for everyone. (`create_thenDelete_byCreator` covers create+delete; cross-user *visibility* is inherent since catalog reads have no per-user filter beyond audience.)

---

### Epic 4 — Logged events API 🟢 (signed off 2026-05-20)

Save and read entries. **User-scoped at every layer** — there is no `findAll`.

- [x] `model/LoggedEvent.java` / `model/LoggedEventOption.java` (option value = JsonNode jsonb)
- [x] Repositories: `findByIdAndUserId`, `deleteByIdAndUserId`, `findScoped(userId, from, to, eventTypeId?, Pageable)`, `countScopedForTypes`. No unbounded queries — every method takes a userId.
- [x] `service/LoggedEventService.java` — every method derives owner from `CurrentUser.id()`; batch hydration (3 queries) for views.
- [x] `controller/LoggedEventController.java`: POST, GET list (`?from=&to=&eventTypeSlug=&limit=`, default 24h / 50, max 200), GET `/{id}`, DELETE `/{id}`.
- [x] `controller/HomeController.java`: `GET /api/home/hero` (medication/water/sleep, hardcoded targets — no goals schema yet), `GET /api/home/today` (today's entries oldest→newest, max 10).
- [x] DTOs (records): LogEventRequest/LogOptionRequest, LoggedEventView (+EventTypeRef/OptionView), LoggedEventsResponse, HeroCard.
- [x] Tests: `LoggedEventControllerTest` (11) — save+read round-trip, unknown type 404, **cross-user 404 on GET/DELETE of another's id**, **list excludes others' events**, filter by type, date-window excludes old, today/hero shapes, unauth 401.

**Definition of done:** ✅ verified by the full JUnit suite run against a real Postgres (see proof below) — `Tests run: 27, Failures: 0, Errors: 0`. Cross-user isolation holds: Jeremy gets 404 on Carley's event id and his list never contains it.

> **Proof (2026-05-20):** ran the whole suite against an external `postgres:16-alpine` (Testcontainers can't negotiate with this box's Docker 29.x; `AbstractIntegrationTest` now falls back to `TRACKER_TEST_DB_URL`). Result:
> ```
> CatalogControllerTest      Tests run: 9,  Failures: 0, Errors: 0
> AuthControllerTest         Tests run: 6,  Failures: 0, Errors: 0
> LoggedEventControllerTest  Tests run: 11, Failures: 0, Errors: 0
> TrackerApplicationTests    Tests run: 1,  Failures: 0, Errors: 0
> ---------------------------------------------------------------
> Tests run: 27, Failures: 0, Errors: 0, Skipped: 0   →  BUILD SUCCESS
> ```
> To reproduce on a host without Testcontainers-compatible Docker:
> ```bash
> docker network create tt && \
> docker run -d --name tt-db --network tt -e POSTGRES_DB=tracker \
>   -e POSTGRES_USER=tracker -e POSTGRES_PASSWORD=pw postgres:16-alpine
> docker run --rm --network tt -v "$PWD":/work -v "$HOME/.m2":/root/.m2 -w /work \
>   -e TRACKER_TEST_DB_URL=jdbc:postgresql://tt-db:5432/tracker \
>   -e TRACKER_TEST_DB_USER=tracker -e TRACKER_TEST_DB_PASSWORD=pw \
>   maven:3.9-eclipse-temurin-21 mvn -B test
> ```

---

### Epic 5 — Frontend scaffold 🟢 (signed off 2026-05-20)

React 19 + Vite SPA (JS) with Tailwind, PWA, JWT-aware fetch client, palette tokens, icon set.

- [x] `tracker/frontend/package.json`, `vite.config.js` (`base: '/tracker/'`, vite-plugin-pwa, dev proxy `/tracker/api`→`:8080/api`)
- [x] `tracker/frontend/eslint.config.js`, `postcss.config.js`, `tailwind.config.js` (tokens mapped to CSS vars, `darkMode: 'class'`)
- [x] `index.html` with Google Fonts preconnect (Nunito + DM Sans + JetBrains Mono)
- [x] `src/main.jsx` (applies stored theme before render), `src/App.jsx` (styled Home placeholder + theme toggle)
- [x] `src/theme.css` (CSS variables from `docs/DESIGN.md`, light `:root` + `.dark`) + `src/theme.js` (light/dark/system, `localStorage tracker.theme`)
- [x] `src/api.js` — base-relative fetch wrapper (`${BASE_URL}api`), `Authorization: Bearer …`, 401 clears session
- [x] `src/auth.js` — token/user storage + JWT-payload decode + `isExpired`/`isAuthenticated`
- [x] `src/icons/index.jsx` — 27 icons (24px/2px/currentColor) per DESIGN.md + `DynamicIcon`
- [x] `public/favicon.svg` + `scripts/generate-icons.mjs` → PWA manifest icons (192/512/maskable/apple)
- [x] `compose.yaml` mounts `./tracker/frontend/dist` at `/var/www/html/tracker`
- [x] `tracker-proxy.conf` serves `/tracker/` (FallbackResource SPA routing + asset/SW cache headers); `/tracker/api/` still proxied to backend

**Deviations from the original plan (intentional):**
- **No `tracker-frontend` nginx container / Dockerfile / nginx.conf.** This repo's other SPAs (book-log, dreamworld) are served as committed `dist/` bind-mounts into the shared Apache `web`; the tracker follows that convention. `dist/` is committed (see `.gitignore`) so Komodo's restart-only deploy can bind-mount it. The frontend carries no secret, so it doesn't belong in the isolated tracker stack.
- `postcss.config.js` (not `.cjs`) — the package is `type: module`.
- Icons authored fresh from the DESIGN.md spec (the `polished-icons.jsx` prototype wasn't in the repo).

**Proof (build + serve, 2026-05-20):**
```
npm install   → added 472 packages, 0 vulnerabilities
npm run lint  → 0 errors (1 react-refresh warning on the icons barrel)
npm run icons → icon-192/512, maskable-512, apple-touch-icon generated
npm run build → 18 modules transformed; dist/index.html + assets + sw.js + manifest; built in ~1.3s
# vite preview smoke (all asset paths are /tracker/-based):
GET /                          → 302 → /tracker/
GET /tracker/                  → 200   (<title>LifeTracker</title>)
GET /tracker/log/water         → 200   (SPA fallback to index.html)
GET /tracker/assets/index.css  → 200
GET /tracker/manifest.webmanifest → 200
```
Reproduce: `cd tracker/frontend && npm install && npm run build && npm run preview`.

**Not verified headless:** the actual in-browser render and the light/dark visual switch (the toggle button is wired to `theme.js`; the build/serve pass but a real browser check is the owner's to do on deploy).

**Definition of done:** `https://apps.biffis.com/tracker/` loads a styled page in both light + dark, installable as a PWA. *(Local build + serve green; prod load gated on the owner's deploy — agent is fenced out of the tracker stack. Tracked in Epic 10.)*

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

- [ ] Register `tracker/compose.yaml` as its own Komodo stack (NOT part of biffis-apps)
- [ ] Set `TRACKER_DB_PASSWORD` + `TRACKER_JWT_SECRET` in that stack's Komodo Environment
- [ ] Confirm `opencode-homelab` has NO permission on the tracker stack (secret isolation — see below)
- [ ] Push branch, merge to main
- [ ] On prod: `docker compose -f tracker/compose.yaml build`
- [ ] Deploy tracker stack first (creates `tracker-net`), then biffis-apps if its proxy changed
- [ ] Verify migrations ran
- [ ] Set Carley's + Jeremy's real passwords via the `set-password` CLI
- [ ] Smoke test login + log an entry from a phone
- [ ] Add PWA to home screen
- [ ] Document any surprises in this file

**Secret isolation (why the tracker is its own stack):** the agent's Komodo API
user (`opencode-homelab`) is non-admin but has explicit grants on several
stacks incl. biffis-apps. Any plain env-var secret on a stack it can reach is
readable via `InspectStackContainer` (resolved `Config.Env`). Splitting the
tracker into its own stack — and not granting that user on it — keeps
`TRACKER_JWT_SECRET` / `TRACKER_DB_PASSWORD` out of the agent's reach entirely.
`web` (in biffis-apps, which the agent can inspect) proxies to the backend over
`tracker-net` but carries no tracker secret.

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
