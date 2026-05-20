# Tracker

Personal event-tracking app. Log anything — headaches, medications, water, mood, period, workouts — see your history, spot patterns over time.

**Live at:** `https://apps.biffis.com/tracker` (planned)
**Status:** Phase 1 in progress
**Owners:** Carley, Jeremy

---

## At a glance

- **Backend:** Spring Boot 3.4.1 · Java 21 · Spring Data JPA · Spring Security + JWT · Flyway · PostgreSQL
- **Frontend:** React 19 + Vite · Tailwind · `vite-plugin-pwa`
- **DB:** PostgreSQL 16 in its own Docker container, no host port, volume on prod only
- **Auth:** Per-user password → JWT (Bearer). Two seeded users: Carley, Jeremy.
- **Deploy:** Komodo restart on push to `main`. Backend and frontend images need a manual `docker compose build` on the prod server when source changes (Komodo does restart-only).

Mirrors the structure of [`jbiffis/hockeypool`](https://github.com/jbiffis/hockeypool) (package-by-layer, `mvnw` wrapper, `application.properties`, multi-stage Dockerfile, nginx-served React frontend).

## Structure

```
tracker/
├── README.md                    ← this file
├── CLAUDE.md                    ← instructions for Claude when working on tracker
├── docs/
│   ├── DATA_MODEL.md            ← schema + relationships + auth rules
│   ├── SEED_CATALOG.md          ← starter event-type hierarchy
│   ├── DESIGN.md                ← palette, typography, components
│   ├── API.md                   ← REST endpoints
│   ├── PRIVACY.md               ← what lives where, what Claude can/can't see
│   └── TEST_CASES.md            ← manual/acceptance test plan + deploy verification
├── backend/                     ← Spring Boot app
│   ├── pom.xml
│   ├── Dockerfile
│   ├── mvnw, mvnw.cmd
│   ├── run.sh
│   └── src/
│       ├── main/
│       │   ├── java/com/biffis/tracker/
│       │   │   ├── TrackerApplication.java
│       │   │   ├── config/        (Spring Security, Jackson, CORS)
│       │   │   ├── controller/    (REST endpoints)
│       │   │   ├── dto/           (request/response records)
│       │   │   ├── model/         (@Entity classes)
│       │   │   ├── repository/    (JpaRepository interfaces)
│       │   │   ├── service/       (business logic)
│       │   │   └── security/      (JWT filter + helpers)
│       │   └── resources/
│       │       ├── application.properties
│       │       └── db/migration/  (Flyway SQL: V1__schema.sql, V2__seed_*.sql, …)
│       └── test/java/com/biffis/tracker/
└── frontend/                    ← React + Vite SPA
    ├── package.json
    ├── vite.config.js           ← base: '/tracker/'
    ├── Dockerfile               ← node build → nginx serve
    ├── nginx.conf
    ├── index.html
    └── src/
        ├── main.jsx, App.jsx
        ├── api.js               ← fetch wrapper, JWT injection
        ├── theme.css            ← palette tokens (light + dark)
        ├── icons/               ← SVG component set
        ├── components/          (AppShell, BottomNav, FieldRenderer, …)
        └── pages/               (Home, Entry, History, Login, Me)
```

## Local dev

> ⚠ Use throwaway data only. See [PRIVACY.md](docs/PRIVACY.md). Never run with real personal data on this machine.

```bash
# Backend (from tracker/backend/)
./mvnw spring-boot:run            # runs on :8080 with H2 in-memory by default in dev

# Frontend (from tracker/frontend/)
npm install
npm run dev                       # Vite dev server, proxies /api → :8080

# Or full stack with throwaway Postgres:
cd ../..
docker compose -f compose.yaml -f compose.dev.yaml up --build tracker-db tracker-backend tracker-frontend
```

## Deploy

1. Push to `main`.
2. SSH to prod, run `docker compose build tracker-backend tracker-frontend` (only when Java/Node sources or Dockerfiles changed).
3. Komodo restart picks up the new images.
4. Flyway runs pending migrations on backend startup.

See [docs/API.md](docs/API.md), [docs/DATA_MODEL.md](docs/DATA_MODEL.md), [docs/DESIGN.md](docs/DESIGN.md), [docs/PRIVACY.md](docs/PRIVACY.md), [docs/SEED_CATALOG.md](docs/SEED_CATALOG.md) for detail.
