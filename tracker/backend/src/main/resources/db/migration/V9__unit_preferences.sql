-- Per-user measurement unit preferences. Presentation only — logged values are
-- always stored in canonical metric (kg / cm / °C); these columns just tell the
-- frontend how to display and enter them. Existing rows default to metric so
-- behaviour is unchanged until a user opts in.

ALTER TABLE users ADD COLUMN weight_unit      text NOT NULL DEFAULT 'kg' CHECK (weight_unit IN ('kg', 'lb'));
ALTER TABLE users ADD COLUMN height_unit      text NOT NULL DEFAULT 'cm' CHECK (height_unit IN ('cm', 'ftin'));
ALTER TABLE users ADD COLUMN temperature_unit text NOT NULL DEFAULT 'c'  CHECK (temperature_unit IN ('c', 'f'));

COMMENT ON COLUMN users.weight_unit      IS 'Display unit for weight: kg (canonical) or lb. Raw data stays kg.';
COMMENT ON COLUMN users.height_unit      IS 'Display unit for height: cm (canonical) or ftin (feet/inches). Raw data stays cm.';
COMMENT ON COLUMN users.temperature_unit IS 'Display unit for temperature: c (canonical) or f. Raw data stays °C.';
