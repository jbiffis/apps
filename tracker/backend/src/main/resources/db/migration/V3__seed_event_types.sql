-- Seed event_types + event_properties. See tracker/docs/SEED_CATALOG.md.
-- All rows here have is_seed=true and cannot be deleted via API.
--
-- Pattern: insert parents first, leaves second (subquery on parent slug),
-- properties last (subqueries on event_type slug + preset slug).

-- ==========================================================================
-- Top-level categories
-- ==========================================================================
INSERT INTO event_types (slug, name, icon, color_class, audience, is_seed, is_category, sort_order) VALUES
    ('health',       'Health',       'Heart',   't-coral', 'all',    true, true, 10),
    ('medication',   'Medication',   'Pill',    't-amber', 'all',    true, true, 20),
    ('food',         'Food',         'Coffee',  't-sky',   'all',    true, true, 30),
    ('recreational', 'Recreational', 'Sparkle', 't-plum',  'all',    true, true, 40),
    ('activity',     'Activity',     'Steps',   't-green', 'all',    true, true, 50),
    ('lady-stuff',   'Lady stuff',   'Heart',    NULL,     'female', true, true, 60);

-- Top-level leaf (Journal)
INSERT INTO event_types (slug, name, icon, audience, is_seed, sort_order) VALUES
    ('journal', 'Journal', 'Journal', 'all', true, 70);

-- ==========================================================================
-- Health → sub-categories
-- ==========================================================================
INSERT INTO event_types (parent_id, slug, name, icon, audience, is_seed, is_category, sort_order)
SELECT id, sub.slug, sub.name, sub.icon, 'all', true, true, sub.sort_order
FROM event_types, (VALUES
    ('eyes',           'Eyes',           'Sun',     20),
    ('ent',            'Ears/Nose/Throat','Mood',   30),
    ('digestive',      'Digestive',      'Food',    40),
    ('general-body',   'General body',   'Workout', 50)
) AS sub(slug, name, icon, sort_order)
WHERE event_types.slug = 'health';

-- Health → direct leaves
INSERT INTO event_types (parent_id, slug, name, icon, audience, is_seed, sort_order)
SELECT id, leaf.slug, leaf.name, leaf.icon, 'all', true, leaf.sort_order
FROM event_types, (VALUES
    ('headache', 'Headache', 'Sparkle', 10),
    ('mood',     'Mood',     'Mood',    60)
) AS leaf(slug, name, icon, sort_order)
WHERE event_types.slug = 'health';

-- Eyes → leaves
INSERT INTO event_types (parent_id, slug, name, icon, audience, is_seed, sort_order)
SELECT id, leaf.slug, leaf.name, 'Sun', 'all', true, leaf.sort_order
FROM event_types, (VALUES
    ('double-vision',  'Double vision',  10),
    ('blurry-vision',  'Blurry vision',  20),
    ('itchy-eyes',     'Itchy eyes',     30),
    ('bloodshot-eyes', 'Bloodshot eyes', 40)
) AS leaf(slug, name, sort_order)
WHERE event_types.slug = 'eyes';

-- ENT → leaves
INSERT INTO event_types (parent_id, slug, name, icon, audience, is_seed, sort_order)
SELECT id, leaf.slug, leaf.name, 'Mood', 'all', true, leaf.sort_order
FROM event_types, (VALUES
    ('sore-throat', 'Sore throat', 10),
    ('congestion',  'Congestion',  20),
    ('earache',     'Earache',     30),
    ('cough',       'Cough',       40)
) AS leaf(slug, name, sort_order)
WHERE event_types.slug = 'ent';

-- Digestive → leaves
INSERT INTO event_types (parent_id, slug, name, icon, audience, is_seed, sort_order)
SELECT id, leaf.slug, leaf.name, 'Food', 'all', true, leaf.sort_order
FROM event_types, (VALUES
    ('stomach-pain',     'Stomach pain',     10),
    ('gas',              'Gas',              20),
    ('nausea',           'Nausea',           30),
    ('heartburn',        'Heartburn',        40),
    ('bowel-movement',   'Bowel movement',   50)
) AS leaf(slug, name, sort_order)
WHERE event_types.slug = 'digestive';

-- General body → leaves
INSERT INTO event_types (parent_id, slug, name, icon, audience, is_seed, sort_order)
SELECT id, leaf.slug, leaf.name, 'Workout', 'all', true, leaf.sort_order
FROM event_types, (VALUES
    ('fatigue',     'Fatigue',     10),
    ('dizziness',   'Dizziness',   20),
    ('body-pain',   'Body pain',   30),
    ('fever',       'Fever',       40)
) AS leaf(slug, name, sort_order)
WHERE event_types.slug = 'general-body';

-- ==========================================================================
-- Medication → leaves
-- ==========================================================================
INSERT INTO event_types (parent_id, slug, name, icon, audience, is_seed, sort_order)
SELECT id, leaf.slug, leaf.name, 'Pill', 'all', true, leaf.sort_order
FROM event_types, (VALUES
    ('advil-200mg', 'Advil 200 mg', 10),
    ('advil-400mg', 'Advil 400 mg', 20),
    ('tylenol',     'Tylenol',      30),
    ('claritin',    'Claritin',     40),
    ('reactine',    'Reactine',     50)
) AS leaf(slug, name, sort_order)
WHERE event_types.slug = 'medication';

-- ==========================================================================
-- Food → leaves
-- ==========================================================================
INSERT INTO event_types (parent_id, slug, name, icon, audience, is_seed, sort_order)
SELECT id, leaf.slug, leaf.name, leaf.icon, 'all', true, leaf.sort_order
FROM event_types, (VALUES
    ('water',       'Water',       'Water',  10),
    ('coffee',      'Coffee',      'Coffee', 20),
    ('tea',         'Tea',         'Coffee', 30),
    ('meal',        'Meal',        'Food',   40),
    ('gluten',      'Gluten',      'Food',   50),
    ('sugar-treat', 'Sugar treat', 'Food',   60)
) AS leaf(slug, name, icon, sort_order)
WHERE event_types.slug = 'food';

-- ==========================================================================
-- Recreational → leaves
-- ==========================================================================
INSERT INTO event_types (parent_id, slug, name, icon, audience, is_seed, sort_order)
SELECT id, leaf.slug, leaf.name, leaf.icon, 'all', true, leaf.sort_order
FROM event_types, (VALUES
    ('alcohol',   'Alcohol',   'Coffee',  10),
    ('cannabis',  'Cannabis',  'Sparkle', 20),
    ('cigarette', 'Cigarette', 'Sparkle', 30)
) AS leaf(slug, name, icon, sort_order)
WHERE event_types.slug = 'recreational';

-- ==========================================================================
-- Activity → leaves
-- ==========================================================================
INSERT INTO event_types (parent_id, slug, name, icon, audience, is_seed, sort_order)
SELECT id, leaf.slug, leaf.name, leaf.icon, 'all', true, leaf.sort_order
FROM event_types, (VALUES
    ('steps',    'Steps',    'Steps',      10),
    ('workout',  'Workout',  'Workout',    20),
    ('meditate', 'Meditate', 'Meditation', 30),
    ('sleep',    'Sleep',    'Sleep',      40)
) AS leaf(slug, name, icon, sort_order)
WHERE event_types.slug = 'activity';

-- ==========================================================================
-- Lady stuff → leaves (audience inherited explicitly so per-leaf overrides are possible later)
-- ==========================================================================
INSERT INTO event_types (parent_id, slug, name, icon, audience, is_seed, sort_order)
SELECT id, leaf.slug, leaf.name, 'Heart', 'female', true, leaf.sort_order
FROM event_types, (VALUES
    ('period',         'Period',        10),
    ('spotting',       'Spotting',      20),
    ('cramps',         'Cramps',        30),
    ('pms-symptoms',   'PMS symptoms',  40)
) AS leaf(slug, name, sort_order)
WHERE event_types.slug = 'lady-stuff';

-- ==========================================================================
-- event_properties — one INSERT per (event_type, preset) pair.
-- Helper: lookups by slug keep the migration self-documenting.
-- ==========================================================================

-- Headache: Severity (required), Location (multi)
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order)
VALUES
    ((SELECT id FROM event_types WHERE slug='headache'), 'Severity',
     (SELECT id FROM property_presets WHERE slug='severity-1-5'), true, 1),
    ((SELECT id FROM event_types WHERE slug='headache'), 'Location',
     (SELECT id FROM property_presets WHERE slug='headache-location-multi'), false, 2);

-- Mood: How (face, required)
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order) VALUES
    ((SELECT id FROM event_types WHERE slug='mood'), 'How',
     (SELECT id FROM property_presets WHERE slug='face-mood-1-5'), true, 1);

-- Eyes leaves: Severity + Side
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order)
SELECT et.id, 'Severity', (SELECT id FROM property_presets WHERE slug='severity-1-5'), true, 1
FROM event_types et
WHERE et.slug IN ('double-vision', 'blurry-vision', 'itchy-eyes', 'bloodshot-eyes');

INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order)
SELECT et.id, 'Side', (SELECT id FROM property_presets WHERE slug='side-single'), false, 2
FROM event_types et
WHERE et.slug IN ('double-vision', 'blurry-vision', 'itchy-eyes', 'bloodshot-eyes');

-- ENT: Severity for all
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order)
SELECT et.id, 'Severity', (SELECT id FROM property_presets WHERE slug='severity-1-5'), true, 1
FROM event_types et
WHERE et.slug IN ('sore-throat', 'congestion', 'earache', 'cough');

-- Earache: Side
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order) VALUES
    ((SELECT id FROM event_types WHERE slug='earache'), 'Side',
     (SELECT id FROM property_presets WHERE slug='side-single'), false, 2);

-- Cough: Type
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order) VALUES
    ((SELECT id FROM event_types WHERE slug='cough'), 'Type',
     (SELECT id FROM property_presets WHERE slug='cough-type-single'), false, 2);

-- Digestive: Severity for all
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order)
SELECT et.id, 'Severity', (SELECT id FROM property_presets WHERE slug='severity-1-5'), true, 1
FROM event_types et
WHERE et.slug IN ('stomach-pain', 'gas', 'nausea', 'heartburn');

-- Stomach pain: Location (body)
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order) VALUES
    ((SELECT id FROM event_types WHERE slug='stomach-pain'), 'Location',
     (SELECT id FROM property_presets WHERE slug='body-location-multi'), false, 2);

-- Bowel movement: Bristol scale (required, replaces severity)
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order) VALUES
    ((SELECT id FROM event_types WHERE slug='bowel-movement'), 'Bristol scale',
     (SELECT id FROM property_presets WHERE slug='bristol-1-7'), true, 1);

-- General body: Severity for fatigue, dizziness, body-pain
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order)
SELECT et.id, 'Severity', (SELECT id FROM property_presets WHERE slug='severity-1-5'), true, 1
FROM event_types et
WHERE et.slug IN ('fatigue', 'dizziness', 'body-pain');

-- Body pain: Location
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order) VALUES
    ((SELECT id FROM event_types WHERE slug='body-pain'), 'Location',
     (SELECT id FROM property_presets WHERE slug='body-location-multi'), false, 2);

-- Fever: Temperature (required)
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order) VALUES
    ((SELECT id FROM event_types WHERE slug='fever'), 'Temperature',
     (SELECT id FROM property_presets WHERE slug='temperature-c'), true, 1);

-- Medication leaves: Dose + With
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order)
SELECT et.id, 'Dose', (SELECT id FROM property_presets WHERE slug='dose-tablets'), true, 1
FROM event_types et
WHERE et.slug IN ('advil-200mg', 'advil-400mg', 'tylenol', 'claritin', 'reactine');

INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order)
SELECT et.id, 'With', (SELECT id FROM property_presets WHERE slug='with-food-single'), false, 2
FROM event_types et
WHERE et.slug IN ('advil-200mg', 'advil-400mg', 'tylenol', 'claritin', 'reactine');

-- Food
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order) VALUES
    ((SELECT id FROM event_types WHERE slug='water'),  'Glasses',
     (SELECT id FROM property_presets WHERE slug='dose-glasses'), true, 1),
    ((SELECT id FROM event_types WHERE slug='coffee'), 'Cups',
     (SELECT id FROM property_presets WHERE slug='dose-cups'), true, 1),
    ((SELECT id FROM event_types WHERE slug='tea'),    'Cups',
     (SELECT id FROM property_presets WHERE slug='dose-cups'), true, 1),
    ((SELECT id FROM event_types WHERE slug='meal'),   'Type',
     (SELECT id FROM property_presets WHERE slug='meal-type-single'), true, 1),
    ((SELECT id FROM event_types WHERE slug='sugar-treat'), 'How much',
     (SELECT id FROM property_presets WHERE slug='severity-1-5'), true, 1);
-- 'gluten' has no properties (mere occurrence).

-- Recreational
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order) VALUES
    ((SELECT id FROM event_types WHERE slug='alcohol'), 'Drinks',
     (SELECT id FROM property_presets WHERE slug='count'), true, 1),
    ((SELECT id FROM event_types WHERE slug='alcohol'), 'Type',
     (SELECT id FROM property_presets WHERE slug='alcohol-type-single'), false, 2),
    ((SELECT id FROM event_types WHERE slug='cannabis'), 'Method',
     (SELECT id FROM property_presets WHERE slug='cannabis-method-single'), true, 1),
    ((SELECT id FROM event_types WHERE slug='cannabis'), 'Dose',
     (SELECT id FROM property_presets WHERE slug='count'), false, 2),
    ((SELECT id FROM event_types WHERE slug='cigarette'), 'Count',
     (SELECT id FROM property_presets WHERE slug='count'), true, 1);

-- Activity
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order) VALUES
    ((SELECT id FROM event_types WHERE slug='steps'), 'Count',
     (SELECT id FROM property_presets WHERE slug='count'), true, 1),
    ((SELECT id FROM event_types WHERE slug='workout'), 'Type',
     (SELECT id FROM property_presets WHERE slug='workout-type-single'), true, 1),
    ((SELECT id FROM event_types WHERE slug='workout'), 'Duration',
     (SELECT id FROM property_presets WHERE slug='duration-minutes'), false, 2),
    ((SELECT id FROM event_types WHERE slug='meditate'), 'Duration',
     (SELECT id FROM property_presets WHERE slug='duration-minutes'), true, 1),
    ((SELECT id FROM event_types WHERE slug='sleep'), 'Duration',
     (SELECT id FROM property_presets WHERE slug='duration-hours'), true, 1),
    ((SELECT id FROM event_types WHERE slug='sleep'), 'Quality',
     (SELECT id FROM property_presets WHERE slug='quality-1-5'), false, 2);

-- Lady stuff
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order) VALUES
    ((SELECT id FROM event_types WHERE slug='period'), 'Flow',
     (SELECT id FROM property_presets WHERE slug='flow-1-5'), true, 1),
    ((SELECT id FROM event_types WHERE slug='spotting'), 'Severity',
     (SELECT id FROM property_presets WHERE slug='severity-1-5'), false, 1),
    ((SELECT id FROM event_types WHERE slug='cramps'), 'Severity',
     (SELECT id FROM property_presets WHERE slug='severity-1-5'), true, 1),
    ((SELECT id FROM event_types WHERE slug='pms-symptoms'), 'Symptoms',
     (SELECT id FROM property_presets WHERE slug='pms-multi'), true, 1);

-- Journal
INSERT INTO event_properties (event_type_id, name, preset_id, required, sort_order) VALUES
    ((SELECT id FROM event_types WHERE slug='journal'), 'Note',
     (SELECT id FROM property_presets WHERE slug='text-long'), true, 1);
