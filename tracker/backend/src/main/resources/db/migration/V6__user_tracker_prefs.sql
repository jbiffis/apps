-- Per-user tracker preferences: hide/show a tracker on the home grid (Me tab,
-- Phase 2c) and a custom display order (long-press reorder, Phase 2d). Absence
-- of a row means the default: visible, default sort. One row per
-- (user, event_type).

CREATE TABLE user_tracker_prefs (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type_id uuid        NOT NULL REFERENCES event_types(id) ON DELETE CASCADE,
    hidden        boolean     NOT NULL DEFAULT false,
    sort_order    integer,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, event_type_id)
);

CREATE INDEX user_tracker_prefs_user_idx ON user_tracker_prefs (user_id);
