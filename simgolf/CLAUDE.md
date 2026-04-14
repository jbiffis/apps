# Project Instructions for AI Agents

This file provides instructions and context for AI coding agents working on this project.

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:ca08a54f -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

## Session Completion

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   bd dolt push
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
<!-- END BEADS INTEGRATION -->


## Build & Test

```bash
# Frontend
cd simgolf/frontend && npm install && npm run build

# Docker (full stack)
cd simgolf && docker compose up --build
```

## Architecture Overview

- **Frontend**: React 19 + TypeScript + Vite, served by nginx (port 8082)
- **API**: PHP 8.3-FPM, routes defined in `api/src/routes.php`, router in `api/public/index.php`
- **Database**: PostgreSQL, schema in `postgres/initdb.d/001_schema.sql`
- **Routing**: nginx reverse-proxies `/api/*` to PHP-FPM

All API endpoints return JSON. No authentication is currently implemented.

## Scorecard Submission API (OCR Workflow)

These endpoints allow submitting scorecard data extracted from photos. The intended workflow is:

1. User uploads a photo of a scorecard to Claude Code
2. Claude performs OCR to extract course info, player names, and scores
3. Claude calls these endpoints to submit the data directly

### `POST /api/courses/find-or-create`

Looks up a course by name (case-insensitive). Creates it with hole data if not found.

**Request:**
```json
{
  "name": "Pebble Beach",
  "holes": [
    {"hole_number": 1, "par": 4, "yardage": 380},
    {"hole_number": 2, "par": 5, "yardage": 502},
    ...
  ]
}
```

- `holes` is required only when the course doesn't already exist (exactly 9 holes).

**Response:**
```json
{
  "course": {"id": 1, "name": "Pebble Beach", ...},
  "holes": [...],
  "created": false
}
```

### `POST /api/rounds/submit-scorecard`

One-shot endpoint to create a round and enter all player scores. Also supports appending scores to an existing round (for split groups photographed separately).

**Creating a new round:**
```json
{
  "season_id": 1,
  "tournament_id": 2,
  "is_practice": false,
  "nine": "front",
  "played_date": "2026-04-14",
  "round_number": 3,
  "course": {
    "name": "Pebble Beach",
    "holes": [
      {"hole_number": 1, "par": 4, "yardage": 380},
      ...
    ]
  },
  "players": [
    {"name": "Shea", "scores": [4, 5, 3, 4, 5, 4, 3, 5, 4]},
    {"name": "Michael", "scores": [5, 4, 4, 3, 6, 5, 4, 4, 5]}
  ]
}
```

**Appending to an existing round** (second scorecard photo from a split group):
```json
{
  "round_id": 15,
  "players": [
    {"name": "Jeremy", "scores": [4, 5, 3, 4, 5, 4, 3, 5, 4]},
    {"name": "Lance", "scores": [5, 4, 4, 3, 6, 5, 4, 4, 5]}
  ]
}
```

**Field details:**
- `round_id` — if provided, appends scores to this existing round (ignores season/course/date fields)
- `season_id`, `nine`, `played_date` — required when creating a new round
- `tournament_id` — null for practice rounds
- `round_number` — auto-calculated from season if omitted
- `course.name` — used for find-or-create lookup (case-insensitive)
- `course.holes` — only needed if the course doesn't exist yet
- `players[].name` — matched case-insensitively against existing players in the database
- `players[].scores` — accepts either a simple array `[4, 5, 3, ...]` (index = hole 1-9) or objects `[{"hole_number": 1, "strokes": 4}, ...]`

**Response:**
```json
{
  "success": true,
  "round": {"id": 15, "season_id": 1, "course_name": "Pebble Beach", ...},
  "players_matched": [
    {"player_id": 1, "name": "Shea", "holes_entered": 9},
    {"player_id": 3, "name": "Michael", "holes_entered": 9}
  ],
  "players_unmatched": ["UnknownName"]
}
```

**Important notes:**
- The response `round.id` should be saved and used as `round_id` for subsequent scorecard photos from the same round
- Scores are upserted — resubmitting is safe and will overwrite previous values
- Unmatched player names are returned in `players_unmatched` so the caller can handle them
- The entire operation runs in a transaction — if anything fails, nothing is committed

### OCR Submission Workflow (for Claude Code)

When the user provides a scorecard photo:

1. Read the image and extract: course name, hole pars, hole yardages, player names, and per-hole scores
2. Determine `season_id` and `tournament_id` (ask the user or look up via `GET /api/seasons` and `GET /api/seasons/{id}/rounds`)
3. Call `POST /api/rounds/submit-scorecard` with the extracted data
4. Save the returned `round.id`
5. If a second photo is provided for the same round, call again with `round_id` set to the saved ID
6. Report results: which players were matched, any unmatched names, and a link to view the round

## Conventions & Patterns

- API routes are closures in `api/src/routes.php` keyed by `"METHOD /path"` strings
- Route parameters use `{paramName}` syntax, resolved via regex in the router
- All DB writes use prepared statements; batch operations use transactions
- Scores use `ON CONFLICT ... DO UPDATE` (upsert) for idempotent writes
- Player names in the database: Shea, Sonat, Michael, Cody, Jeremy, Lance, Tim, Lindsay
