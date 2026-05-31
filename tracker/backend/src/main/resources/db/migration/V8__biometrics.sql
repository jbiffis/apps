-- Biometrics: store only stable facts on the user; derive everything that
-- changes from the log (single source of truth). Weight and height are NOT
-- columns here — they are logged events (the trackers added below), so the
-- "current" value is always the most recent entry, never a stale copy.
--
-- Likewise age is NOT stored: it's derived from date_of_birth at read time.

ALTER TABLE users ADD COLUMN date_of_birth      date;
ALTER TABLE users ADD COLUMN biological_sex     text CHECK (biological_sex IN ('male', 'female', 'intersex'));
ALTER TABLE users ADD COLUMN blood_type         text CHECK (blood_type IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'));
ALTER TABLE users ADD COLUMN activity_level     text CHECK (activity_level IN ('sedentary', 'light', 'moderate', 'active', 'very_active'));
ALTER TABLE users ADD COLUMN weight_goal        text CHECK (weight_goal IN ('lose', 'maintain', 'gain'));
ALTER TABLE users ADD COLUMN drug_allergies     text;
ALTER TABLE users ADD COLUMN chronic_conditions text;

COMMENT ON COLUMN users.biological_sex IS 'Clinical sex for biomedical context; distinct from gender (identity / audience filtering).';
COMMENT ON COLUMN users.drug_allergies IS 'Free text — load-bearing for medication advice.';
COMMENT ON COLUMN users.chronic_conditions IS 'Free text — ongoing conditions relevant to biomedical questions.';

-- -------------------------------------------------------------------------
-- Weight & height measurement presets. Canonical metric units stored; the
-- frontend converts for display. number widget, same shape as temperature-c.
-- -------------------------------------------------------------------------
INSERT INTO property_presets (slug, name, widget, options, is_seed) VALUES
    ('weight-kg', 'Weight (kg)', 'number',
     '{"unit":"kg","min":20,"max":350,"step":0.1,"default":70}'::jsonb, true),
    ('height-cm', 'Height (cm)', 'number',
     '{"unit":"cm","min":50,"max":250,"step":0.5,"default":170}'::jsonb, true);

-- -------------------------------------------------------------------------
-- Weight & height trackers under Health, so there is a log to derive the
-- latest measurement from. Each has a single required Measurement property.
-- -------------------------------------------------------------------------
INSERT INTO event_types (parent_id, slug, name, icon, audience, is_seed, sort_order)
SELECT id, m.slug, m.name, m.icon, 'all', true, m.sort_order
FROM event_types, (VALUES
    ('weight', 'Weight', 'Weight',  70),
    ('height', 'Height', 'Steps',   80)
) AS m(slug, name, icon, sort_order)
WHERE event_types.slug = 'health';

INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order) VALUES
    ((SELECT id FROM event_types WHERE slug='weight'), 'Measurement',
     (SELECT id FROM property_presets WHERE slug='weight-kg'), true, 1),
    ((SELECT id FROM event_types WHERE slug='height'), 'Measurement',
     (SELECT id FROM property_presets WHERE slug='height-cm'), true, 1);
