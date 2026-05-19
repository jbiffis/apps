-- Tracker schema. See tracker/docs/DATA_MODEL.md for the canonical description.
--
-- Conventions:
--   * UUID primary keys via gen_random_uuid() (pgcrypto extension).
--   * snake_case column names.
--   * timestamptz for all instants.
--   * jsonb (not json) for flexible blobs.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -------------------------------------------------------------------------
-- users
-- -------------------------------------------------------------------------
CREATE TABLE users (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    username      text        NOT NULL UNIQUE,
    display_name  text        NOT NULL,
    password_hash text        NOT NULL,
    gender        text                 CHECK (gender IN ('male', 'female', 'other')),
    created_at    timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE  users IS 'Authenticated app users. password_hash is bcrypt; never returned via API.';
COMMENT ON COLUMN users.gender IS 'Drives default visibility of audience-gated event types. Not access control.';

-- -------------------------------------------------------------------------
-- property_presets — reusable widget definitions referenced by event_properties
-- -------------------------------------------------------------------------
CREATE TABLE property_presets (
    id       uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    slug     text    NOT NULL UNIQUE,
    name     text    NOT NULL,
    widget   text    NOT NULL CHECK (widget IN (
        'step', 'single_select', 'multi_select', 'face_select',
        'number', 'text', 'duration', 'dose', 'bool'
    )),
    options  jsonb   NOT NULL DEFAULT '{}'::jsonb,
    is_seed  boolean NOT NULL DEFAULT false
);

-- -------------------------------------------------------------------------
-- event_types — what you can log; self-referential for categories
-- -------------------------------------------------------------------------
CREATE TABLE event_types (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id     uuid                 REFERENCES event_types(id) ON DELETE RESTRICT,
    slug          text        NOT NULL UNIQUE,
    name          text        NOT NULL,
    description   text,
    icon          text        NOT NULL,
    color_class   text                 CHECK (color_class IN ('t-coral', 't-amber', 't-sky', 't-plum', 't-green')),
    unit          text,
    default_value numeric,
    audience      text        NOT NULL DEFAULT 'all' CHECK (audience IN ('all', 'male', 'female')),
    is_seed       boolean     NOT NULL DEFAULT false,
    is_category   boolean     NOT NULL DEFAULT false,
    sort_order    integer     NOT NULL DEFAULT 0,
    created_by    uuid                 REFERENCES users(id) ON DELETE SET NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX event_types_parent_sort_idx ON event_types (parent_id NULLS FIRST, sort_order);

-- -------------------------------------------------------------------------
-- event_properties — fields shown on the entry screen for an event type
-- -------------------------------------------------------------------------
CREATE TABLE event_properties (
    id            uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type_id uuid    NOT NULL REFERENCES event_types(id) ON DELETE CASCADE,
    name          text    NOT NULL,
    description   text,
    preset_id     uuid    NOT NULL REFERENCES property_presets(id) ON DELETE RESTRICT,
    required      boolean NOT NULL DEFAULT false,
    sort_order    integer NOT NULL DEFAULT 0,
    UNIQUE (event_type_id, name)
);
CREATE INDEX event_properties_event_type_sort_idx ON event_properties (event_type_id, sort_order);

-- -------------------------------------------------------------------------
-- logged_events — one row per entry
-- -------------------------------------------------------------------------
CREATE TABLE logged_events (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type_id uuid        NOT NULL REFERENCES event_types(id) ON DELETE RESTRICT,
    occurred_at   timestamptz NOT NULL DEFAULT now(),
    note          text,
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX logged_events_user_occurred_idx       ON logged_events (user_id, occurred_at DESC);
CREATE INDEX logged_events_user_type_occurred_idx  ON logged_events (user_id, event_type_id, occurred_at DESC);

-- -------------------------------------------------------------------------
-- logged_event_options — selected/entered values for the entry's properties
-- -------------------------------------------------------------------------
CREATE TABLE logged_event_options (
    id                 uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    logged_event_id    uuid    NOT NULL REFERENCES logged_events(id) ON DELETE CASCADE,
    event_property_id  uuid    NOT NULL REFERENCES event_properties(id) ON DELETE RESTRICT,
    value              jsonb   NOT NULL,
    UNIQUE (logged_event_id, event_property_id)
);
CREATE INDEX logged_event_options_event_idx ON logged_event_options (logged_event_id);
