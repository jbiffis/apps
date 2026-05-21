-- Soft-delete for logged events. DELETE now sets deleted_at instead of removing
-- the row, so an entry can be restored (the Home long-press "undo" restores the
-- same row rather than re-creating it). Every per-user read filters
-- deleted_at IS NULL.

ALTER TABLE logged_events ADD COLUMN deleted_at timestamptz;

-- Partial index keeps the hot (user, time) window queries fast over live rows.
CREATE INDEX logged_events_user_occurred_live_idx
    ON logged_events (user_id, occurred_at DESC)
    WHERE deleted_at IS NULL;
