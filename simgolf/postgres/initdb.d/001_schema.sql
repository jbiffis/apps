-- Sim Golf League Database Schema (PostgreSQL)

-- Seasons
CREATE TABLE seasons (
    id SERIAL PRIMARY KEY,
    year INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Players
CREATE TABLE players (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Courses
CREATE TABLE courses (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Holes (9 per nine)
CREATE TABLE holes (
    id SERIAL PRIMARY KEY,
    course_id INT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    hole_number SMALLINT NOT NULL CHECK (hole_number BETWEEN 1 AND 9),
    par SMALLINT NOT NULL,
    yardage SMALLINT NOT NULL,
    UNIQUE (course_id, hole_number)
);

-- Tournaments (3 per season)
CREATE TABLE tournaments (
    id SERIAL PRIMARY KEY,
    season_id INT NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    number SMALLINT NOT NULL CHECK (number BETWEEN 1 AND 3),
    name VARCHAR(100) NOT NULL,
    UNIQUE (season_id, number)
);

-- Rounds (6 per tournament + 2 practice rounds per season)
CREATE TABLE rounds (
    id SERIAL PRIMARY KEY,
    tournament_id INT REFERENCES tournaments(id) ON DELETE SET NULL,
    season_id INT NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    round_number SMALLINT NOT NULL,
    course_id INT NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    nine VARCHAR(5) NOT NULL CHECK (nine IN ('front', 'back')),
    played_date DATE NOT NULL,
    is_practice BOOLEAN NOT NULL DEFAULT FALSE,
    ctp_hole SMALLINT,
    ctp_yardage SMALLINT,
    ctp_prize_amount NUMERIC(8,2) NOT NULL DEFAULT 20.00,
    chip_in_pot NUMERIC(8,2) NOT NULL DEFAULT 0.00
);

-- Scores (one row per player per hole per round; NULL strokes = absent)
CREATE TABLE scores (
    id SERIAL PRIMARY KEY,
    round_id INT NOT NULL REFERENCES rounds(id) ON DELETE CASCADE,
    player_id INT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    hole_number SMALLINT NOT NULL,
    strokes SMALLINT,
    UNIQUE (round_id, player_id, hole_number)
);

-- Handicaps (one per player per tournament)
CREATE TABLE handicaps (
    id SERIAL PRIMARY KEY,
    player_id INT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    tournament_id INT REFERENCES tournaments(id) ON DELETE SET NULL,
    season_id INT NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    tournament_number SMALLINT NOT NULL,
    value NUMERIC(5,2) NOT NULL,
    UNIQUE (player_id, season_id, tournament_number)
);

-- Prize winnings
CREATE TABLE prize_winnings (
    id SERIAL PRIMARY KEY,
    player_id INT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    round_id INT REFERENCES rounds(id) ON DELETE SET NULL,
    tournament_id INT REFERENCES tournaments(id) ON DELETE SET NULL,
    season_id INT NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    type VARCHAR(10) NOT NULL CHECK (type IN ('ctp', 'chip_in', 't1st', 't2nd')),
    amount NUMERIC(8,2) NOT NULL,
    description VARCHAR(255),
    awarded_at TIMESTAMPTZ DEFAULT NOW()
);

-- Closest to pin entries per round
CREATE TABLE closest_to_pin (
    id SERIAL PRIMARY KEY,
    round_id INT NOT NULL REFERENCES rounds(id) ON DELETE CASCADE,
    player_id INT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    distance_feet NUMERIC(5,1) NOT NULL,
    won BOOLEAN NOT NULL DEFAULT FALSE
);

-- Per-round handicap overrides (one-time exceptions to tournament handicap)
CREATE TABLE IF NOT EXISTS handicap_overrides (
    round_id INT NOT NULL REFERENCES rounds(id) ON DELETE CASCADE,
    player_id INT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    value NUMERIC(5,2) NOT NULL,
    note TEXT,
    PRIMARY KEY (round_id, player_id)
);

-- Score edit history
CREATE TABLE score_edits (
    id SERIAL PRIMARY KEY,
    round_id INT NOT NULL REFERENCES rounds(id) ON DELETE CASCADE,
    player_id INT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    hole_number SMALLINT NOT NULL,
    old_strokes SMALLINT,
    new_strokes SMALLINT,
    edited_by VARCHAR(100),
    edited_at TIMESTAMPTZ DEFAULT NOW()
);
