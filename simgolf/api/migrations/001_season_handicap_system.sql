-- Existing seasons keep their original calculation and ranking behavior.
-- No scores, handicaps, points or prize data are changed by this migration.
ALTER TABLE seasons ADD COLUMN IF NOT EXISTS handicap_system VARCHAR(32)
    NOT NULL DEFAULT 'legacy'
    CHECK (handicap_system IN ('legacy', 'best_rounds_v1'));
