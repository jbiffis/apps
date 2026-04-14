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


## Architecture Overview

- **Frontend**: React SPA in `frontend/`, served at `/simgolf/`
- **API**: PHP REST API in `api/`, served at `/simgolf/api/`
- **Database**: PostgreSQL, schema in `postgres/initdb.d/001_schema.sql`
- API base URL: `https://apps.biffis.com/simgolf/api`

## Player IDs

| ID | Name    |
|----|---------|
| 1  | Shea    |
| 2  | Sonat   |
| 3  | Michael |
| 4  | Cody    |
| 5  | Jeremy  |
| 6  | Lance   |
| 7  | Tim     |
| 8  | Lindsay |

## Entering Scorecard Data from Screenshots

When the user provides a SimGolf scorecard screenshot, follow this workflow:

### 1. Extract data from the image
- **Course name** (may be partially hidden at top)
- **Hole data**: par and yardage for all 18 holes (front + back nine)
- **Player scores**: hole-by-hole strokes for the front or back nine played
- **Round settings**: date, nine played (front/back), tees
- **Verify** each player's hole scores sum to the displayed OUT/IN total

### 2. Check existing state
Fetch in parallel:
```
GET /courses          — check if course already exists
GET /tournaments/{id} — get tournament details, existing rounds, season_id
```

### 3. Create course (if new)
Two separate API calls (required):
1. `POST /courses` with front 9 holes (1–9) and name
2. `PUT /courses/{id}` with back 9 holes (10–18) — uses upsert

### 4. Create the round
```
POST /rounds
{
  "season_id": <from tournament>,
  "tournament_id": <given by user>,
  "course_id": <from step 3 or existing>,
  "nine": "front" or "back",
  "played_date": "YYYY-MM-DD",  (from round settings DATE field)
  "round_number": <given by user>,
  "is_practice": false
}
```

### 5. Enter scores (parallel calls)
For each player shown on the scorecard:
```
POST /rounds/{roundId}/scores
{ "player_id": <id>, "holes": {"1": 5, "2": 4, ...} }
```
- Only enter scores for players who have scores (not `--`)
- Players not on the scorecard are automatically marked absent

### 6. Verify
```
GET /rounds/{roundId}
```
Check gross totals match the scorecard image.
