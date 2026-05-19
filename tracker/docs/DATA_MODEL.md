# Data model

All tables live in the `tracker` Postgres database (default `public` schema — single-app database, no need to namespace).

## Overview

```
users ──┐
        │  (1:N)
        ├──► logged_events ──► logged_event_options ──► event_properties
        │                                                       │
        │                                                       ▼
        │                                                property_presets
        │
        └──► event_types (created_by; for user-added trackers)
                  │
                  │  (self-ref, 1:N — categories nest)
                  └──► event_types
                  │
                  │  (1:N)
                  └──► event_properties
```

## Tables

### `users`

| col | type | notes |
|---|---|---|
| `id` | uuid | PK, default `gen_random_uuid()` |
| `username` | text | unique, lowercase, immutable |
| `display_name` | text | shown in greeting / "Good morning, Alex." |
| `password_hash` | text | bcrypt; never returned via API |
| `gender` | text | `male` / `female` / `other` / null. Drives default visibility of audience-gated trackers. |
| `created_at` | timestamptz | default `now()` |

Seeded users (see [PRIVACY.md](PRIVACY.md) for password handling):

- `carley` — display "Carley", gender `female`
- `jeremy` — display "Jeremy", gender `male`

### `event_types`

What you can log. Parents are categories (no own log form); leaves have properties and open the entry screen.

| col | type | notes |
|---|---|---|
| `id` | uuid | PK |
| `parent_id` | uuid | FK → `event_types.id`, nullable. Self-ref for categories. |
| `slug` | text | unique. URL-safe, stable identifier (e.g. `headache`, `advil-200mg`). |
| `name` | text | display name |
| `description` | text | optional |
| `icon` | text | icon name from the SVG set (`Pill`, `Water`, …) |
| `color_class` | text | `t-coral` / `t-amber` / `t-sky` / `t-plum` / `t-green` / null |
| `unit` | text | e.g. `mg`, `glasses`, `min` |
| `default_value` | numeric | e.g. 200 for "Advil 200mg" |
| `audience` | text | `all` (default) / `female` / `male`. Filters home tile visibility for users with matching gender. |
| `is_seed` | bool | true for catalog-shipped entries; cannot be deleted via API |
| `is_category` | bool | true if this is a parent-only container (no log form) |
| `sort_order` | int | within parent |
| `created_by` | uuid | FK → `users.id`, nullable for seed entries |
| `created_at` | timestamptz | default `now()` |

Indexes: `(parent_id, sort_order)`, `(slug)`.

### `event_properties`

Fields shown on the entry screen for an event type.

| col | type | notes |
|---|---|---|
| `id` | uuid | PK |
| `event_type_id` | uuid | FK → `event_types.id`, cascade delete |
| `name` | text | "Severity", "Location", "Dose" |
| `description` | text | optional helper text |
| `preset_id` | uuid | FK → `property_presets.id` |
| `required` | bool | default false |
| `sort_order` | int | within event_type |

### `property_presets`

Reusable widget definitions.

| col | type | notes |
|---|---|---|
| `id` | uuid | PK |
| `slug` | text | unique. e.g. `severity-1-5`, `face-mood-1-5`. |
| `name` | text | display name |
| `widget` | text | one of: `step`, `single_select`, `multi_select`, `face_select`, `number`, `text`, `duration`, `dose`, `bool` |
| `options` | jsonb | shape depends on widget; see below |
| `is_seed` | bool | true for catalog presets |

**`options` jsonb shapes by widget:**

- `step`, `single_select`, `multi_select`, `face_select`:
  `[{"value": 1, "label": "Mild"}, {"value": 2, "label": "…"}, …]`
- `face_select` may add `"emoji": "😐"` per option.
- `number`, `dose`: `{"min": 0, "max": 100, "step": 1, "default": 1, "unit": "mg"}`
- `duration`: `{"unit": "minutes", "default": 30}`
- `text`: `{"placeholder": "…", "maxLength": 500}`
- `bool`: `{"trueLabel": "Yes", "falseLabel": "No"}`

### `logged_events`

One row per entry.

| col | type | notes |
|---|---|---|
| `id` | uuid | PK |
| `user_id` | uuid | FK → `users.id` |
| `event_type_id` | uuid | FK → `event_types.id` |
| `occurred_at` | timestamptz | user-editable, defaults to `now()` at create time |
| `note` | text | optional |
| `created_at` | timestamptz | default `now()`, immutable |

Indexes:
- `(user_id, occurred_at DESC)` — Today/history queries
- `(user_id, event_type_id, occurred_at DESC)` — per-tracker history

### `logged_event_options`

Property values for the entry. One row per `event_property` filled in.

| col | type | notes |
|---|---|---|
| `id` | uuid | PK |
| `logged_event_id` | uuid | FK → `logged_events.id`, cascade delete |
| `event_property_id` | uuid | FK → `event_properties.id` |
| `value` | jsonb | flexible. number `5`, string `"front"`, array `["front","temple"]` |

Unique `(logged_event_id, event_property_id)` — one value per property per entry.

## Auth / row scoping rules

These are enforced **in the service layer** (not in the DB via RLS, since we use Spring Data JPA, not PostgREST). Service classes obtain the authenticated `userId` from `SecurityContextHolder` via a helper and pass it into every repo query.

- `logged_events`, `logged_event_options`: every read/write filters by `user_id`. There is no `findAll()` exposed for these.
- `users`: each user can read only their own row. Password hash never serialized to JSON.
- `event_types`, `event_properties`, `property_presets`: readable by all authenticated users. Insertable by any authenticated user (new types/properties are shared for everyone). Deletable only by the creator and only when `is_seed = false`.

## Audience filtering (UI default; not security)

`event_types.audience` controls *default* visibility on the home screen. A `female`-only tracker won't appear by default for `jeremy`; a `male`-only tracker won't appear by default for `carley`. Either user can explicitly enable a hidden tracker via the "All trackers" → edit screen (Phase 2 feature; Phase 1 just hides them).

**This is not access control.** All event types are readable by any logged-in user via `/api/event-types?include=all`. Logged data is per-user, always.

## Migrations

Located in `backend/src/main/resources/db/migration/`:

- `V1__schema.sql` — all tables, indexes, FKs
- `V2__seed_presets.sql` — `property_presets` rows
- `V3__seed_event_types.sql` — full catalog from [SEED_CATALOG.md](SEED_CATALOG.md)
- `V4__seed_users.sql` — Carley + Jeremy with bcrypted initial passwords (passwords supplied out-of-band, never committed)

Flyway runs these on Spring Boot startup. `spring.jpa.hibernate.ddl-auto=validate` ensures Hibernate doesn't drift from the migrations.
