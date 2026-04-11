-- Seed data for Sim Golf League

-- Season
INSERT INTO seasons (year, name) VALUES (2026, '2026 Season');

-- Players (8 players)
INSERT INTO players (name) VALUES
    ('Shea'),
    ('Sonat'),
    ('Mike'),
    ('Dave'),
    ('Chris'),
    ('Scott'),
    ('Ryan'),
    ('Pete');

-- Courses (3 courses for 3 tournaments)
INSERT INTO courses (name) VALUES
    ('Pebble Beach'),
    ('Augusta National'),
    ('Pinehurst No. 2');

-- Holes for Pebble Beach (front 9)
INSERT INTO holes (course_id, hole_number, par, yardage) VALUES
    (1, 1, 4, 354),
    (1, 2, 5, 502),
    (1, 3, 4, 390),
    (1, 4, 4, 327),
    (1, 5, 3, 166),
    (1, 6, 5, 513),
    (1, 7, 3, 100),
    (1, 8, 4, 431),
    (1, 9, 4, 464);

-- Holes for Augusta National (front 9)
INSERT INTO holes (course_id, hole_number, par, yardage) VALUES
    (2, 1, 4, 445),
    (2, 2, 5, 575),
    (2, 3, 4, 350),
    (2, 4, 3, 240),
    (2, 5, 4, 455),
    (2, 6, 3, 180),
    (2, 7, 4, 450),
    (2, 8, 5, 570),
    (2, 9, 4, 460);

-- Holes for Pinehurst No. 2 (front 9)
INSERT INTO holes (course_id, hole_number, par, yardage) VALUES
    (3, 1, 4, 411),
    (3, 2, 4, 452),
    (3, 3, 4, 340),
    (3, 4, 5, 558),
    (3, 5, 4, 482),
    (3, 6, 3, 218),
    (3, 7, 4, 401),
    (3, 8, 3, 183),
    (3, 9, 4, 393);

-- Tournaments
INSERT INTO tournaments (season_id, number, name) VALUES
    (1, 1, 'Tournament 1 — Pebble Beach'),
    (1, 2, 'Tournament 2 — Augusta National'),
    (1, 3, 'Tournament 3 — Pinehurst No. 2');

-- Practice rounds (2 practice rounds, no tournament)
INSERT INTO rounds (season_id, tournament_id, round_number, course_id, nine, played_date, is_practice, ctp_hole, ctp_yardage, ctp_prize_amount, chip_in_pot)
VALUES
    (1, NULL, 1, 1, 'front', '2026-01-10', TRUE, 5, 166, 20.00, 0.00),
    (1, NULL, 2, 1, 'back',  '2026-01-17', TRUE, NULL, NULL, 20.00, 0.00);

-- Tournament 1 rounds (6 rounds)
INSERT INTO rounds (season_id, tournament_id, round_number, course_id, nine, played_date, is_practice, ctp_hole, ctp_yardage, ctp_prize_amount, chip_in_pot)
VALUES
    (1, 1, 1, 1, 'front', '2026-01-24', FALSE, 5, 166, 20.00, 0.00),
    (1, 1, 2, 1, 'back',  '2026-01-31', FALSE, NULL, NULL, 20.00, 16.00),
    (1, 1, 3, 2, 'front', '2026-02-07', FALSE, 4, 240, 40.00, 0.00),
    (1, 1, 4, 2, 'back',  '2026-02-14', FALSE, 6, 180, 20.00, 0.00),
    (1, 1, 5, 3, 'front', '2026-02-21', FALSE, 8, 183, 20.00, 0.00),
    (1, 1, 6, 3, 'back',  '2026-02-28', FALSE, NULL, NULL, 20.00, 0.00);
