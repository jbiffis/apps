-- Application code already rounds every handicap write to one decimal
-- place (bestRoundHandicap, the manual PUT /handicaps override), but the
-- column itself allowed two. Narrow it so one decimal is the stored
-- precision, not just a convention every write path has to remember.
ALTER TABLE handicaps ALTER COLUMN value TYPE NUMERIC(5,1) USING round(value, 1);
