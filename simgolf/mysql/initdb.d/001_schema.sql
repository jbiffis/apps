-- Sim Golf League Database Schema

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Seasons
CREATE TABLE seasons (
    id INT AUTO_INCREMENT PRIMARY KEY,
    year INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Players
CREATE TABLE players (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Courses
CREATE TABLE courses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Holes (9 per nine, can reuse for front/back)
CREATE TABLE holes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_id INT NOT NULL,
    hole_number TINYINT NOT NULL COMMENT '1-9',
    par TINYINT NOT NULL,
    yardage SMALLINT NOT NULL,
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    UNIQUE KEY uk_course_hole (course_id, hole_number)
) ENGINE=InnoDB;

-- Tournaments (3 per season)
CREATE TABLE tournaments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    season_id INT NOT NULL,
    number TINYINT NOT NULL COMMENT '1, 2, or 3',
    name VARCHAR(100) NOT NULL,
    FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE,
    UNIQUE KEY uk_season_number (season_id, number)
) ENGINE=InnoDB;

-- Rounds (6 per tournament + 2 practice rounds per season)
CREATE TABLE rounds (
    id INT AUTO_INCREMENT PRIMARY KEY,
    tournament_id INT NULL COMMENT 'NULL for practice rounds',
    season_id INT NOT NULL COMMENT 'For practice rounds',
    round_number TINYINT NOT NULL COMMENT 'Week number within tournament (1-6) or practice (1-2)',
    course_id INT NOT NULL,
    nine ENUM('front', 'back') NOT NULL,
    played_date DATE NOT NULL,
    is_practice TINYINT(1) NOT NULL DEFAULT 0,
    ctp_hole TINYINT NULL COMMENT 'Closest to pin hole number',
    ctp_yardage SMALLINT NULL COMMENT 'Yardage of CTP hole',
    ctp_prize_amount DECIMAL(8,2) NOT NULL DEFAULT 20.00 COMMENT 'Prize for CTP this round',
    chip_in_pot DECIMAL(8,2) NOT NULL DEFAULT 0.00 COMMENT 'Current chip-in pot before this round',
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE SET NULL,
    FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE,
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- Scores (one row per player per hole per round; NULL strokes = absent)
CREATE TABLE scores (
    id INT AUTO_INCREMENT PRIMARY KEY,
    round_id INT NOT NULL,
    player_id INT NOT NULL,
    hole_number TINYINT NOT NULL COMMENT '1-9',
    strokes TINYINT NULL COMMENT 'NULL means player was absent',
    FOREIGN KEY (round_id) REFERENCES rounds(id) ON DELETE CASCADE,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    UNIQUE KEY uk_round_player_hole (round_id, player_id, hole_number)
) ENGINE=InnoDB;

-- Handicaps (one per player per tournament)
CREATE TABLE handicaps (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    tournament_id INT NULL COMMENT 'NULL = practice/initial handicap calc not applicable',
    season_id INT NOT NULL,
    tournament_number TINYINT NOT NULL COMMENT '1, 2, or 3 — which tournament this handicap applies to',
    value DECIMAL(5,2) NOT NULL,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE SET NULL,
    FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE,
    UNIQUE KEY uk_player_season_tournament (player_id, season_id, tournament_number)
) ENGINE=InnoDB;

-- Prize winnings
CREATE TABLE prize_winnings (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    round_id INT NULL,
    tournament_id INT NULL,
    season_id INT NOT NULL,
    type ENUM('ctp', 'chip_in', 't1st', 't2nd') NOT NULL,
    amount DECIMAL(8,2) NOT NULL,
    description VARCHAR(255) NULL COMMENT 'Human-readable label',
    awarded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    FOREIGN KEY (round_id) REFERENCES rounds(id) ON DELETE SET NULL,
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE SET NULL,
    FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Closest to pin entries per round
CREATE TABLE closest_to_pin (
    id INT AUTO_INCREMENT PRIMARY KEY,
    round_id INT NOT NULL,
    player_id INT NOT NULL,
    distance_feet DECIMAL(5,1) NOT NULL,
    won TINYINT(1) NOT NULL DEFAULT 0,
    FOREIGN KEY (round_id) REFERENCES rounds(id) ON DELETE CASCADE,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Score edit history
CREATE TABLE score_edits (
    id INT AUTO_INCREMENT PRIMARY KEY,
    round_id INT NOT NULL,
    player_id INT NOT NULL,
    hole_number TINYINT NOT NULL,
    old_strokes TINYINT NULL,
    new_strokes TINYINT NULL,
    edited_by VARCHAR(100) NULL,
    edited_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (round_id) REFERENCES rounds(id) ON DELETE CASCADE,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
) ENGINE=InnoDB;
