# Tracker — instructions for Claude

Read this before changing any code under `tracker/`.

## Privacy rules (non-negotiable)

1. **Never connect to the prod database.** No `docker exec`, no `psql` against any host that isn't a clearly disposable local container. The prod DB volume lives on the production server at `/var/lib/docker/volumes/apps/tracker-db/` — you cannot reach it from this machine. Do not try.
2. **Never commit data files.** `*.db`, `*.sqlite`, `*.sql.gz`, `dump.sql`, anything that looks like a snapshot. `.gitignore` covers most; if you generate one, delete it.
3. **Never log personal data in code.** No `log.info(loggedEvent.getNote())`, no echoing user input in error messages. PII stays out of logs entirely. Log IDs, not contents.
4. **Local dev uses throwaway data only.** If you write a seed script for testing, use obviously fake names (Test User, Demo, etc.) — never names from real users.
5. **Migrations don't touch personal data.** Schema changes only. Backfills that move real data must be run by a human against prod.

See [docs/PRIVACY.md](docs/PRIVACY.md) for the full trust-boundary picture.

## Stack conventions

Modeled on [`jbiffis/hockeypool`](https://github.com/jbiffis/hockeypool). Match its style unless there's a specific reason not to.

- **Java 21**, Spring Boot **3.4.1**, Maven via `./mvnw` wrapper. Do not bump unless asked.
- **Package by layer** — `config / controller / dto / model / repository / service / security`. Don't introduce package-by-feature.
- **`application.properties`**, not YAML.
- **Entities use JPA annotations.** UUIDs as primary keys (`@GeneratedValue(strategy = UUID)`).
- **DTOs are Java `record`s** in `dto/`. Don't expose `@Entity` classes from controllers.
- **No Lombok.** Records and idiomatic Java cover what's needed.
- **Migrations are plain SQL** in `src/main/resources/db/migration/`. Named `V{n}__short_description.sql`. Never rename a migration after it's been applied to any environment.
- **Hibernate ddl-auto = `validate`.** Flyway owns the schema; Hibernate only verifies.
- **Tests use JUnit 5.** Integration tests use Testcontainers for Postgres; unit tests use plain mocks.
- **Frontend is React + Vite (JS, not TS).** Tailwind for utility styling, plus `theme.css` for the palette tokens.
- **No external analytics, no third-party scripts.** Self-hosted only.

## Adding things — quick recipes

### Add a new event type to the seed catalog

1. Edit `docs/SEED_CATALOG.md` first (source of truth).
2. Add the row to `backend/src/main/resources/db/migration/V{next}__seed_event_type_{name}.sql` — never edit a migration that's already shipped.
3. If it needs new properties, add them in the same migration (`event_properties` rows referencing existing `property_presets`).
4. If it needs a new widget kind, that's a frontend change too — extend `FieldRenderer` and add the preset row.

### Add a new property preset

1. Edit `docs/SEED_CATALOG.md` → "Property presets" section.
2. Insert into `property_presets` in a new migration. `options` is `jsonb`.
3. Frontend `FieldRenderer` switches on the `widget` enum — add a case if it's a new widget kind.

### Add a new endpoint

1. Define the DTOs in `dto/` (records).
2. Service method in `service/` — does the work, takes the current user ID from `SecurityContextHolder` via a helper.
3. Controller in `controller/` — thin, delegates to service.
4. `Controller` is registered automatically by component scan.
5. Add a Testcontainers integration test in `src/test/java/.../controller/`. At minimum: happy path + auth-required negative case.
6. Update `docs/API.md`.

### Add a frontend page

1. Component in `src/pages/`.
2. Route in `App.jsx`.
3. API calls go through `src/api.js` (handles JWT, base URL, errors uniformly).
4. Match the palette tokens in `theme.css` — don't hardcode colors.

## Auth model — quick reference

- Every endpoint except `POST /api/auth/login` requires `Authorization: Bearer <jwt>`.
- JWT payload: `{ sub: userId, username, exp }`. HS256 with secret from `JWT_SECRET`.
- `JwtAuthFilter` populates `SecurityContext` so `@AuthenticationPrincipal` works in controllers.
- **All queries that touch `logged_events` or `logged_event_options` MUST filter by `user_id` of the authenticated user.** This is enforced in the service layer — there is no `findAll()`. If you write one, you've made a privacy bug.

## Don't do these

- Don't add Spring Cloud, Eureka, Resilience4j, or any other "enterprise" Spring extension. This is a small app.
- Don't introduce Kotlin alongside Java.
- Don't switch the frontend to TypeScript without asking first.
- Don't add ORM-level event listeners that touch other entities — keep cascade logic explicit in services.
- Don't add a "delete all" endpoint or expose raw SQL anywhere.
- Don't suggest moving the DB onto the shared `data-tracking-globe` network — it stays in its own container on its own network.

## When unsure, ask

Privacy and schema decisions are sticky. Ask before:
- Adding fields to `users` (especially anything queryable that could ID a person to outsiders).
- Adding any external API call from the backend.
- Changing the JWT payload shape.
- Adding any backend → frontend logging.

Defer to the docs (`docs/`) for everything else.
