# API

Base URL: `https://apps.biffis.com/tracker/api`
(local dev: `http://localhost:8080/api`, fronted by Vite at `http://localhost:5173/api` via dev proxy)

All responses are JSON. Times are ISO 8601 UTC. IDs are UUID v4.

## Auth

### `POST /auth/login`
Exchange email + password for a JWT.

Request:
```json
{ "email": "carley401@gmail.com", "password": "…" }
```

Response 200:
```json
{
  "token": "eyJhbGciOi…",
  "user": { "id": "…", "email": "carley401@gmail.com", "displayName": "Carley", "gender": "female" },
  "mustChangePassword": false
}
```

`mustChangePassword: true` means the account still has its temp password — the
client must route the user to the change-password flow before anything else.

Response 401: `{ "error": "invalid_credentials" }`

Token: HS256 JWT, claims `{ sub: <userId>, email, exp }`. TTL 30 days. Sent on every subsequent request as `Authorization: Bearer <token>`.

### `POST /auth/change-password`
Set a new password for the authenticated user (forced first-login change or a
voluntary change). Verifies the current password, clears the force-change flag,
and returns a fresh token so the client drops the stale one.

Request:
```json
{ "currentPassword": "…", "newPassword": "… (min 8 chars)" }
```

Response 200: same shape as login (`mustChangePassword` is now `false`).
Response 401: `{ "error": "invalid_credentials" }` (wrong current password).

### `POST /auth/logout`
Optional. Client-side simply deletes the token. Server may keep a denylist in a later phase.

## Catalog

### `GET /event-types`
Returns all event types the caller can see (audience-filtered for their gender by default; pass `?include=all` to bypass).

Response 200:
```json
[
  {
    "id": "…",
    "parentId": null,
    "slug": "health",
    "name": "Health",
    "icon": "Heart",
    "colorClass": "t-coral",
    "isCategory": true,
    "isSeed": true,
    "audience": "all",
    "sortOrder": 1,
    "children": [
      { "id": "…", "slug": "headache", "name": "Headache", "icon": "Habit", "isCategory": false, "audience": "all", "properties": [ /* see below */ ] },
      …
    ]
  },
  …
]
```

Properties on a leaf event type:
```json
{
  "id": "…", "name": "Severity", "required": true, "sortOrder": 1,
  "preset": {
    "id": "…", "slug": "severity-1-5", "widget": "step",
    "options": [{"value":1,"label":"Mild"}, {"value":2,"label":"Annoying"}, …]
  }
}
```

### `GET /event-types/{slug}`
Single event type with properties. Used when navigating directly to `/log/{slug}`.

### `POST /event-types`
Create a new (non-seed) event type. Available to all authenticated users; the result is visible to everyone.

Request:
```json
{
  "parentSlug": "medication",
  "name": "Aleve",
  "icon": "Pill",
  "colorClass": "t-amber",
  "audience": "all",
  "properties": [
    { "name": "Dose", "presetSlug": "dose-tablets", "required": true, "sortOrder": 1 },
    { "name": "With", "presetSlug": "with-food-single", "sortOrder": 2 }
  ]
}
```

Response 201: the created event type with properties hydrated.

### `DELETE /event-types/{id}`
Only allowed if `is_seed = false` and `created_by = <caller>`. Otherwise 403.

### `GET /property-presets`
Lists all property presets. Used by the "new tracker" form so the user can pick widgets.

## Logged events

### `POST /logged-events`
Save an entry.

Request:
```json
{
  "eventTypeSlug": "advil-200mg",
  "occurredAt": "2026-05-19T13:41:00Z",
  "note": "after lunch",
  "options": [
    { "propertyName": "Dose", "value": 2 },
    { "propertyName": "With", "value": "Lunch" }
  ]
}
```

(`propertyName` is convenience; resolved server-side. Alternatively pass `propertyId`.)

Response 201:
```json
{
  "id": "…",
  "eventType": { "slug": "advil-200mg", "name": "Advil 200 mg" },
  "occurredAt": "2026-05-19T13:41:00Z",
  "note": "after lunch",
  "options": [
    { "property": "Dose", "value": 2 },
    { "property": "With", "value": "Lunch" }
  ],
  "createdAt": "2026-05-19T13:41:02Z"
}
```

### `GET /logged-events`
Lists the caller's entries. Always scoped to the authenticated user — no cross-user reads possible.

Query params:
- `from`, `to` — ISO timestamps, half-open `[from, to)`. Defaults to last 24h.
- `eventTypeSlug` — filter by tracker.
- `limit` — default 50, max 200.

Response:
```json
{ "events": [ /* same shape as POST 201 */ ], "nextCursor": null }
```

### `GET /logged-events/{id}`
One entry. 404 if the caller isn't the owner (we don't distinguish to avoid leaking IDs).

### `PUT /logged-events/{id}`
Replace an entry the caller owns (edit flow). Same body as `POST`; `occurredAt`/`note` are overwritten and the option set is fully replaced. Keeps the same id. 404 if the caller isn't the owner. Response 200: same shape as the `POST` 201.

### `DELETE /logged-events/{id}`
**Soft-delete** (Phase 2): sets `deleted_at` so the entry drops out of every read but the row survives. 204. 404 if the caller isn't the owner. Idempotent.

### `POST /logged-events/{id}/restore`
Undo a soft-delete — clears `deleted_at`, keeping the same id/createdAt. Used by the Home long-press "undo" toast. Response 200: same shape as `POST`. 404 if the caller isn't the owner.

## Me / preferences

### `GET /me/tracker-prefs`
The caller's per-tracker preferences. Only trackers with a saved pref are returned (absence = default: visible, default order).
```json
[ { "eventTypeSlug": "water", "hidden": true, "sortOrder": null } ]
```

### `PUT /me/tracker-prefs/{slug}`
Upsert the caller's pref for one tracker. Partial — null fields are left unchanged. `{ "hidden": true }` hides it from the home grid + log picker; `{ "sortOrder": 3 }` sets its position (Phase 2d). 404 if the slug is unknown. Response 200: the updated `TrackerPrefView`.

### `GET /me/biometrics`
The caller's biometric profile. **Stored** fields are stable facts on the user row; **derived** fields are computed at read time so they never go stale — `age` from `dateOfBirth`, `latestWeightKg`/`latestHeightCm` from the most recent logged Weight/Height entry, and `bmi` from the two latest measurements. Weight/height are never stored here; log the Weight/Height trackers to change them.
```json
{
  "dateOfBirth": "1990-05-15", "biologicalSex": "male", "bloodType": "O+",
  "activityLevel": "moderate", "weightGoal": "maintain",
  "drugAllergies": "penicillin", "chronicConditions": "none",
  "age": 36, "latestWeightKg": 70.5, "latestHeightCm": 165.0, "bmi": 25.9
}
```
Any field may be null (unset / never logged).

### `PUT /me/biometrics`
Replace the **stored** fields (the derived fields are read-only and ignored if sent). Each field is optional; null or `""` clears it. Validated against the same vocabularies as the DB constraints: `biologicalSex` ∈ {male, female, intersex}; `bloodType` ∈ {A+, A−, B+, B−, AB+, AB−, O+, O−}; `activityLevel` ∈ {sedentary, light, moderate, active, very_active}; `weightGoal` ∈ {lose, maintain, gain}; `dateOfBirth` must be in the past. Invalid values → `422 validation_failed`. Response 200: the full `BiometricsView` (stored + freshly-derived).

## Stats

### `GET /stats?days=N`
Activity aggregates for the caller over the trailing `N` days (default 84, max 366). UTC-day buckets.
```json
{
  "from": "…", "to": "…",
  "totalEntries": 1234,
  "currentStreakDays": 4, "longestStreakDays": 19,
  "perTracker": [ { "eventTypeSlug": "water", "name": "Water", "icon": "Water", "count": 410 }, … ],
  "daily": [ { "date": "2026-05-21", "count": 17 }, … ]
}
```

## Home aggregates

These are convenience endpoints to keep the Home screen fast (one round-trip).

### `GET /home/hero`
Returns the 3 hero cards (Phase 1: hardcoded order — Medication, Water, Sleep).

```json
[
  { "eventTypeSlug": "medication", "name": "Medication", "icon": "Pill", "primary": true,
    "valueText": "2", "subText": "/3", "captionText": "1 left", "progress": 0.66 },
  { "eventTypeSlug": "water", "name": "Water", "icon": "Water", "primary": false,
    "valueText": "5", "subText": "/8", "captionText": "glasses", "progress": 0.625 },
  { "eventTypeSlug": "sleep", "name": "Sleep", "icon": "Sleep", "primary": false,
    "valueText": "7.2", "subText": "h", "captionText": "last night", "progress": 0.90 }
]
```

### `GET /home/today`
Today's entries for the home feed (oldest → newest), max 10. Same shape as `/logged-events`.

## Errors

Uniform shape:
```json
{ "error": "snake_case_code", "message": "Optional human-readable detail" }
```

Common codes: `invalid_credentials`, `unauthorized`, `forbidden`, `not_found`, `validation_failed` (with `fieldErrors: {field: message}`), `conflict`.

HTTP status mirrors REST convention (400 / 401 / 403 / 404 / 409 / 422 / 500).

## Versioning

No version in the URL in Phase 1. If the contract has to break later, introduce `/v2/...` and keep `/api/...` (= v1) running for one release.
