<?php
declare(strict_types=1);

require_once __DIR__ . '/scoring.php';

return [

    // ── Seasons ──────────────────────────────────────────────
    'GET /seasons' => function (PDO $db, array $p): array {
        return $db->query("SELECT * FROM seasons ORDER BY year DESC")->fetchAll();
    },

    // ── Players ──────────────────────────────────────────────
    'GET /players' => function (PDO $db, array $p): array {
        return $db->query("SELECT * FROM players ORDER BY name")->fetchAll();
    },

    // ── Season summary ────────────────────────────────────────
    'GET /seasons/{seasonId}/summary' => function (PDO $db, array $p): array {
        $seasonId = (int)$p['seasonId'];

        // Get tournaments for this season
        $stmt = $db->prepare("SELECT * FROM tournaments WHERE season_id = ? ORDER BY number");
        $stmt->execute([$seasonId]);
        $tournaments = $stmt->fetchAll();

        // Get all players
        $players = $db->query("SELECT * FROM players ORDER BY name")->fetchAll();

        // For each tournament, sum points for each player
        $playerTotals = [];
        $tournamentTotals = [];

        foreach ($players as $player) {
            $pid = $player['id'];
            $playerTotals[$pid] = [
                'player_id'   => $pid,
                'name'        => $player['name'],
                'tournaments' => [],
                'overall'     => 0,
            ];
        }

        foreach ($tournaments as $tournament) {
            $tid = $tournament['id'];
            $tournamentTotals[$tid] = ['tournament' => $tournament, 'player_points' => []];

            // Get all rounds for this tournament
            $stmt = $db->prepare("SELECT id FROM rounds WHERE tournament_id = ? AND is_practice = FALSE");
            $stmt->execute([$tid]);
            $roundIds = $stmt->fetchAll(PDO::FETCH_COLUMN);

            // Sum points across rounds
            $tPoints = [];
            foreach ($players as $player) {
                $tPoints[$player['id']] = 0;
            }

            foreach ($roundIds as $rid) {
                $scorecard = getRoundScorecard($db, (int)$rid);
                foreach ($scorecard['players'] as $row) {
                    $tPoints[$row['player_id']] += $row['points'] ?? 0;
                }
            }

            foreach ($players as $player) {
                $pid = $player['id'];
                $pts = $tPoints[$pid];
                $playerTotals[$pid]['tournaments'][$tournament['number']] = $pts;
                $playerTotals[$pid]['overall'] += $pts;
                $tournamentTotals[$tid]['player_points'][$pid] = $pts;
            }
        }

        // Sort by overall desc
        usort($playerTotals, fn($a, $b) => $b['overall'] <=> $a['overall']);

        // Add positions
        $pos = 1;
        $prev = null;
        $prevPos = 1;
        foreach ($playerTotals as &$row) {
            if ($prev !== null && $row['overall'] === $prev) {
                $row['position'] = $prevPos;
            } else {
                $row['position'] = $pos;
                $prevPos = $pos;
            }
            $prev = $row['overall'];
            $pos++;
        }

        $stmt = $db->prepare("SELECT * FROM seasons WHERE id = ?");
        $stmt->execute([$seasonId]);
        $season = $stmt->fetch();

        return [
            'season'      => $season,
            'tournaments' => $tournaments,
            'players'     => $playerTotals,
        ];
    },

    // ── Tournament detail ─────────────────────────────────────
    'GET /tournaments/{tournamentId}' => function (PDO $db, array $p): array {
        $tid = (int)$p['tournamentId'];
        $stmt = $db->prepare("SELECT t.*, s.year, s.name as season_name FROM tournaments t JOIN seasons s ON s.id = t.season_id WHERE t.id = ?");
        $stmt->execute([$tid]);
        $tournament = $stmt->fetch();
        if (!$tournament) { http_response_code(404); return ['error' => 'Not found']; }

        $stmt = $db->prepare("SELECT r.*, c.name as course_name FROM rounds r JOIN courses c ON c.id = r.course_id WHERE r.tournament_id = ? ORDER BY r.round_number");
        $stmt->execute([$tid]);
        $rounds = $stmt->fetchAll();

        $players = $db->query("SELECT * FROM players ORDER BY name")->fetchAll();

        // Build per-round points
        $playerData = [];
        foreach ($players as $player) {
            $pid = $player['id'];
            $playerData[$pid] = ['player_id' => $pid, 'name' => $player['name'], 'rounds' => [], 'total' => 0];
        }

        foreach ($rounds as $round) {
            $scorecard = getRoundScorecard($db, (int)$round['id']);
            foreach ($scorecard['players'] as $row) {
                $pid = $row['player_id'];
                $playerData[$pid]['rounds'][$round['id']] = [
                    'net'    => $row['net'],
                    'points' => $row['points'] ?? 0,
                    'absent' => $row['absent'],
                ];
                $playerData[$pid]['total'] += $row['points'] ?? 0;
            }
        }

        usort($playerData, fn($a, $b) => $b['total'] <=> $a['total']);

        return [
            'tournament' => $tournament,
            'rounds'     => $rounds,
            'players'    => array_values($playerData),
        ];
    },

    // ── Round scorecard ───────────────────────────────────────
    'GET /rounds/{roundId}' => function (PDO $db, array $p): array {
        $scorecard = getRoundScorecard($db, (int)$p['roundId']);
        if (empty($scorecard)) { http_response_code(404); return ['error' => 'Not found']; }

        // Add CTP entries
        $stmt = $db->prepare("
            SELECT ctp.*, pl.name as player_name
            FROM closest_to_pin ctp
            JOIN players pl ON pl.id = ctp.player_id
            WHERE ctp.round_id = ?
            ORDER BY ctp.distance_feet ASC
        ");
        $stmt->execute([$p['roundId']]);
        $scorecard['ctp_entries'] = $stmt->fetchAll();

        // Add edit history
        $stmt = $db->prepare("
            SELECT se.*, pl.name as player_name
            FROM score_edits se
            JOIN players pl ON pl.id = se.player_id
            WHERE se.round_id = ?
            ORDER BY se.edited_at DESC
        ");
        $stmt->execute([$p['roundId']]);
        $scorecard['edits'] = $stmt->fetchAll();

        return $scorecard;
    },

    // ── Edit a score ──────────────────────────────────────────
    'PUT /rounds/{roundId}/scores' => function (PDO $db, array $p): array {
        $roundId = (int)$p['roundId'];
        $body = json_decode(file_get_contents('php://input'), true);
        $playerId   = (int)($body['player_id'] ?? 0);
        $holeNumber = (int)($body['hole_number'] ?? 0);
        $newStrokes = isset($body['strokes']) ? (int)$body['strokes'] : null;
        $editedBy   = trim($body['edited_by'] ?? '');

        if (!$playerId || !$holeNumber) {
            http_response_code(400);
            return ['error' => 'player_id and hole_number required'];
        }

        // Get old score
        $stmt = $db->prepare("SELECT strokes FROM scores WHERE round_id = ? AND player_id = ? AND hole_number = ?");
        $stmt->execute([$roundId, $playerId, $holeNumber]);
        $old = $stmt->fetch();
        $oldStrokes = $old ? $old['strokes'] : null;

        // Upsert score
        $stmt = $db->prepare("
            INSERT INTO scores (round_id, player_id, hole_number, strokes)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (round_id, player_id, hole_number) DO UPDATE SET strokes = EXCLUDED.strokes
        ");
        $stmt->execute([$roundId, $playerId, $holeNumber, $newStrokes]);

        // Log edit
        $stmt = $db->prepare("
            INSERT INTO score_edits (round_id, player_id, hole_number, old_strokes, new_strokes, edited_by)
            VALUES (?, ?, ?, ?, ?, ?)
        ");
        $stmt->execute([$roundId, $playerId, $holeNumber, $oldStrokes, $newStrokes, $editedBy ?: null]);

        return ['success' => true];
    },

    // ── Player detail ─────────────────────────────────────────
    'GET /players/{playerId}/seasons/{seasonId}' => function (PDO $db, array $p): array {
        $playerId = (int)$p['playerId'];
        $seasonId = (int)$p['seasonId'];

        $stmt = $db->prepare("SELECT * FROM players WHERE id = ?");
        $stmt->execute([$playerId]);
        $player = $stmt->fetch();
        if (!$player) { http_response_code(404); return ['error' => 'Not found']; }

        // Practice rounds
        $stmt = $db->prepare("
            SELECT r.*, c.name as course_name
            FROM rounds r JOIN courses c ON c.id = r.course_id
            WHERE r.season_id = ? AND r.is_practice = TRUE
            ORDER BY r.round_number
        ");
        $stmt->execute([$seasonId]);
        $practiceRounds = $stmt->fetchAll();

        // Tournaments
        $stmt = $db->prepare("SELECT * FROM tournaments WHERE season_id = ? ORDER BY number");
        $stmt->execute([$seasonId]);
        $tournaments = $stmt->fetchAll();

        $result = [
            'player'          => $player,
            'practice_rounds' => [],
            'tournaments'     => [],
            'prize_winnings'  => [],
            'overall_points'  => 0,
        ];

        foreach ($practiceRounds as $round) {
            $scorecard = getRoundScorecard($db, (int)$round['id']);
            $playerRow = array_values(array_filter($scorecard['players'], fn($r) => $r['player_id'] === $playerId))[0] ?? null;
            $result['practice_rounds'][] = [
                'round'  => $round,
                'scores' => $playerRow,
            ];
        }

        foreach ($tournaments as $tournament) {
            $stmt = $db->prepare("
                SELECT r.*, c.name as course_name
                FROM rounds r JOIN courses c ON c.id = r.course_id
                WHERE r.tournament_id = ? ORDER BY r.round_number
            ");
            $stmt->execute([$tournament['id']]);
            $rounds = $stmt->fetchAll();

            // Handicap used entering this tournament
            $stmt = $db->prepare("
                SELECT value FROM handicaps
                WHERE player_id = ? AND season_id = ? AND tournament_number = ?
            ");
            $stmt->execute([$playerId, $seasonId, $tournament['number']]);
            $hRow = $stmt->fetchColumn();
            $handicap = $hRow !== false ? (float)$hRow : null;

            // Handicap for the next tournament (new handicap after this tournament)
            $stmt->execute([$playerId, $seasonId, $tournament['number'] + 1]);
            $hNext = $stmt->fetchColumn();
            $nextHandicap = $hNext !== false ? (float)$hNext : null;

            $tData = ['tournament' => $tournament, 'rounds' => [], 'total' => 0,
                      'handicap' => $handicap, 'next_handicap' => $nextHandicap];
            foreach ($rounds as $round) {
                $scorecard = getRoundScorecard($db, (int)$round['id']);
                $playerRow = array_values(array_filter($scorecard['players'], fn($r) => $r['player_id'] === $playerId))[0] ?? null;
                $pts = $playerRow['points'] ?? 0;
                $tData['rounds'][] = ['round' => $round, 'scores' => $playerRow, 'points' => $pts];
                $tData['total'] += $pts;
            }
            $result['tournaments'][] = $tData;
            $result['overall_points'] += $tData['total'];
        }

        // Prize winnings
        $stmt = $db->prepare("
            SELECT pw.*, r.round_number, r.played_date, t.number as tournament_number, t.name as tournament_name
            FROM prize_winnings pw
            LEFT JOIN rounds r ON r.id = pw.round_id
            LEFT JOIN tournaments t ON t.id = pw.tournament_id
            WHERE pw.player_id = ? AND pw.season_id = ?
            ORDER BY pw.awarded_at
        ");
        $stmt->execute([$playerId, $seasonId]);
        $result['prize_winnings'] = $stmt->fetchAll();

        return $result;
    },

    // ── Handicaps ─────────────────────────────────────────────
    'GET /seasons/{seasonId}/handicaps' => function (PDO $db, array $p): array {
        $seasonId = (int)$p['seasonId'];
        $stmt = $db->prepare("SELECT * FROM tournaments WHERE season_id = ? ORDER BY number");
        $stmt->execute([$seasonId]);
        $tournaments = $stmt->fetchAll();

        $players = $db->query("SELECT * FROM players ORDER BY name")->fetchAll();

        $result = [];
        foreach ($tournaments as $tournament) {
            $rows = [];
            foreach ($players as $player) {
                $stmt = $db->prepare("
                    SELECT value FROM handicaps
                    WHERE player_id = ? AND season_id = ? AND tournament_number = ?
                ");
                $stmt->execute([$player['id'], $seasonId, $tournament['number']]);
                $h = $stmt->fetchColumn();
                $rows[] = ['player' => $player, 'handicap' => $h !== false ? (float)$h : null];
            }
            $result[] = ['tournament' => $tournament, 'handicaps' => $rows];
        }
        return $result;
    },

    // ── Prizes summary ────────────────────────────────────────
    'GET /seasons/{seasonId}/prizes' => function (PDO $db, array $p): array {
        $seasonId = (int)$p['seasonId'];
        $stmt = $db->prepare("
            SELECT p.id, p.name, COALESCE(SUM(pw.amount), 0) as total
            FROM players p
            LEFT JOIN prize_winnings pw ON pw.player_id = p.id AND pw.season_id = ?
            GROUP BY p.id, p.name
            ORDER BY total DESC
        ");
        $stmt->execute([$seasonId]);
        return $stmt->fetchAll();
    },

    // ── In-round: CTP entry ───────────────────────────────────
    'POST /rounds/{roundId}/ctp' => function (PDO $db, array $p): array {
        $roundId = (int)$p['roundId'];
        $body = json_decode(file_get_contents('php://input'), true);
        $playerId = (int)($body['player_id'] ?? 0);
        $distance = (float)($body['distance_feet'] ?? 0);

        if (!$playerId || $distance <= 0) {
            http_response_code(400);
            return ['error' => 'player_id and distance_feet required'];
        }

        $stmt = $db->prepare("INSERT INTO closest_to_pin (round_id, player_id, distance_feet) VALUES (?, ?, ?) RETURNING id");
        $stmt->execute([$roundId, $playerId, $distance]);
        return ['id' => $stmt->fetchColumn()];
    },

    'POST /rounds/{roundId}/ctp/award' => function (PDO $db, array $p): array {
        $roundId = (int)$p['roundId'];

        // Get round for prize amount
        $stmt = $db->prepare("SELECT * FROM rounds WHERE id = ?");
        $stmt->execute([$roundId]);
        $round = $stmt->fetch();
        if (!$round) { http_response_code(404); return ['error' => 'Round not found']; }

        // Find winner (shortest distance)
        $stmt = $db->prepare("SELECT * FROM closest_to_pin WHERE round_id = ? ORDER BY distance_feet ASC LIMIT 1");
        $stmt->execute([$roundId]);
        $winner = $stmt->fetch();

        if (!$winner) {
            // No one made the green — roll prize to next round
            // Find next round
            $stmt = $db->prepare("
                SELECT id FROM rounds
                WHERE (tournament_id = ? OR (is_practice = TRUE AND season_id = ?))
                  AND round_number > ?
                ORDER BY round_number ASC LIMIT 1
            ");
            $stmt->execute([$round['tournament_id'], $round['season_id'], $round['round_number']]);
            $nextRound = $stmt->fetch();
            if ($nextRound) {
                $stmt = $db->prepare("UPDATE rounds SET ctp_prize_amount = ctp_prize_amount + 20 WHERE id = ?");
                $stmt->execute([$nextRound['id']]);
            }
            return ['winner' => null, 'rolled_to' => $nextRound['id'] ?? null];
        }

        // Mark winner
        $stmt = $db->prepare("UPDATE closest_to_pin SET won = 1 WHERE id = ?");
        $stmt->execute([$winner['id']]);

        // Record prize winning
        $stmt = $db->prepare("
            INSERT INTO prize_winnings (player_id, round_id, tournament_id, season_id, type, amount, description)
            VALUES (?, ?, ?, ?, 'ctp', ?, ?)
        ");
        $desc = "Round {$round['round_number']} - Closest to the Pin";
        $stmt->execute([$winner['player_id'], $roundId, $round['tournament_id'], $round['season_id'], $round['ctp_prize_amount'], $desc]);

        return ['winner' => $winner, 'amount' => $round['ctp_prize_amount']];
    },

    // ── In-round: chip-in ─────────────────────────────────────
    'POST /rounds/{roundId}/chipin' => function (PDO $db, array $p): array {
        $roundId = (int)$p['roundId'];
        $body = json_decode(file_get_contents('php://input'), true);
        $playerId = (int)($body['player_id'] ?? 0);
        $amount   = (float)($body['amount'] ?? 0);

        if (!$playerId || $amount <= 0) {
            http_response_code(400);
            return ['error' => 'player_id and amount required'];
        }

        $stmt = $db->prepare("SELECT * FROM rounds WHERE id = ?");
        $stmt->execute([$roundId]);
        $round = $stmt->fetch();
        if (!$round) { http_response_code(404); return ['error' => 'Round not found']; }

        $stmt = $db->prepare("
            INSERT INTO prize_winnings (player_id, round_id, tournament_id, season_id, type, amount, description)
            VALUES (?, ?, ?, ?, 'chip_in', ?, ?)
            RETURNING id
        ");
        $desc = "Round {$round['round_number']} - Chip In";
        $stmt->execute([$playerId, $roundId, $round['tournament_id'], $round['season_id'], $amount, $desc]);

        return ['success' => true, 'id' => $stmt->fetchColumn()];
    },

    // ── Score entry (bulk for a round) ────────────────────────
    'POST /rounds/{roundId}/scores' => function (PDO $db, array $p): array {
        $roundId = (int)$p['roundId'];
        $body = json_decode(file_get_contents('php://input'), true);
        // Expect: { player_id: int, holes: { "1": 4, "2": 5, ... } }
        $playerId = (int)($body['player_id'] ?? 0);
        $holes    = $body['holes'] ?? [];

        if (!$playerId || empty($holes)) {
            http_response_code(400);
            return ['error' => 'player_id and holes required'];
        }

        $stmt = $db->prepare("
            INSERT INTO scores (round_id, player_id, hole_number, strokes)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (round_id, player_id, hole_number) DO UPDATE SET strokes = EXCLUDED.strokes
        ");
        foreach ($holes as $holeNum => $strokes) {
            $stmt->execute([$roundId, $playerId, (int)$holeNum, $strokes !== null ? (int)$strokes : null]);
        }

        return ['success' => true];
    },

    // ── Handicap recalculation ────────────────────────────────
    'POST /seasons/{seasonId}/recalculate-handicaps' => function (PDO $db, array $p): array {
        $seasonId = (int)$p['seasonId'];
        $players = $db->query("SELECT * FROM players")->fetchAll();
        $stmt = $db->prepare("SELECT * FROM tournaments WHERE season_id = ? ORDER BY number");
        $stmt->execute([$seasonId]);
        $tournaments = $stmt->fetchAll();

        $updated = [];
        foreach ($players as $player) {
            $pid = $player['id'];
            // Tournament 1 handicap: from practice rounds
            $h1 = calculateHandicap($db, $pid, $seasonId);

            // Upsert tournament 1 handicap
            if (!empty($tournaments)) {
                $t1 = $tournaments[0];
                $stmt2 = $db->prepare("
                    INSERT INTO handicaps (player_id, tournament_id, season_id, tournament_number, value)
                    VALUES (?, ?, ?, 1, ?)
                    ON CONFLICT (player_id, season_id, tournament_number) DO UPDATE SET value = EXCLUDED.value
                ");
                $stmt2->execute([$pid, $t1['id'], $seasonId, $h1]);
            }

            $updated[] = ['player_id' => $pid, 'name' => $player['name'], 't1_handicap' => $h1];
        }

        return ['updated' => $updated];
    },

    // ══════════════════════════════════════════════════════════
    // ADMIN / DATA ENTRY ENDPOINTS
    // ══════════════════════════════════════════════════════════

    // ── Create season ─────────────────────────────────────────
    'POST /seasons' => function (PDO $db, array $p): array {
        $body = json_decode(file_get_contents('php://input'), true);
        $year = (int)($body['year'] ?? 0);
        $name = trim($body['name'] ?? '');
        if (!$year || !$name) { http_response_code(400); return ['error' => 'year and name required']; }

        $stmt = $db->prepare("INSERT INTO seasons (year, name) VALUES (?, ?) RETURNING id");
        $stmt->execute([$year, $name]);
        $id = $stmt->fetchColumn();

        // Auto-create 3 tournaments
        $tournamentNames = ["Tournament 1", "Tournament 2", "Tournament 3"];
        foreach ([1, 2, 3] as $num) {
            $s2 = $db->prepare("INSERT INTO tournaments (season_id, number, name) VALUES (?, ?, ?)");
            $s2->execute([$id, $num, $tournamentNames[$num - 1]]);
        }

        $stmt = $db->prepare("SELECT * FROM seasons WHERE id = ?");
        $stmt->execute([$id]);
        return $stmt->fetch();
    },

    // ── Create player ─────────────────────────────────────────
    'POST /players' => function (PDO $db, array $p): array {
        $body = json_decode(file_get_contents('php://input'), true);
        $name = trim($body['name'] ?? '');
        if (!$name) { http_response_code(400); return ['error' => 'name required']; }

        $stmt = $db->prepare("INSERT INTO players (name) VALUES (?) RETURNING id");
        $stmt->execute([$name]);
        $id = $stmt->fetchColumn();

        $stmt = $db->prepare("SELECT * FROM players WHERE id = ?");
        $stmt->execute([$id]);
        return $stmt->fetch();
    },

    // ── Update player ─────────────────────────────────────────
    'PUT /players/{playerId}' => function (PDO $db, array $p): array {
        $body = json_decode(file_get_contents('php://input'), true);
        $name = trim($body['name'] ?? '');
        if (!$name) { http_response_code(400); return ['error' => 'name required']; }

        $stmt = $db->prepare("UPDATE players SET name = ? WHERE id = ? RETURNING id, name");
        $stmt->execute([$name, (int)$p['playerId']]);
        return $stmt->fetch() ?: (http_response_code(404) && ['error' => 'Not found']);
    },

    // ── Create course ─────────────────────────────────────────
    'POST /courses' => function (PDO $db, array $p): array {
        $body = json_decode(file_get_contents('php://input'), true);
        $name = trim($body['name'] ?? '');
        $holes = $body['holes'] ?? []; // [{hole_number, par, yardage}, ...]
        if (!$name) { http_response_code(400); return ['error' => 'name required']; }

        $stmt = $db->prepare("INSERT INTO courses (name) VALUES (?) RETURNING id");
        $stmt->execute([$name]);
        $courseId = $stmt->fetchColumn();

        foreach ($holes as $h) {
            $stmt = $db->prepare("INSERT INTO holes (course_id, hole_number, par, yardage) VALUES (?, ?, ?, ?)");
            $stmt->execute([$courseId, (int)$h['hole_number'], (int)$h['par'], (int)$h['yardage']]);
        }

        $stmt = $db->prepare("SELECT * FROM courses WHERE id = ?");
        $stmt->execute([$courseId]);
        $course = $stmt->fetch();
        $stmt = $db->prepare("SELECT * FROM holes WHERE course_id = ? ORDER BY hole_number");
        $stmt->execute([$courseId]);
        return ['course' => $course, 'holes' => $stmt->fetchAll()];
    },

    // ── Get courses ───────────────────────────────────────────
    'GET /courses' => function (PDO $db, array $p): array {
        $courses = $db->query("SELECT * FROM courses ORDER BY name")->fetchAll();
        foreach ($courses as &$c) {
            $stmt = $db->prepare("SELECT * FROM holes WHERE course_id = ? ORDER BY hole_number");
            $stmt->execute([$c['id']]);
            $c['holes'] = $stmt->fetchAll();
        }
        return $courses;
    },

    // ── Update course ────────────────────────────────────────
    'PUT /courses/{courseId}' => function (PDO $db, array $p): array {
        $body = json_decode(file_get_contents('php://input'), true);
        $courseId = (int)$p['courseId'];
        $name = trim($body['name'] ?? '');
        $holes = $body['holes'] ?? [];
        if (!$name) { http_response_code(400); return ['error' => 'name required']; }

        $stmt = $db->prepare("UPDATE courses SET name = ? WHERE id = ? RETURNING *");
        $stmt->execute([$name, $courseId]);
        $course = $stmt->fetch();
        if (!$course) { http_response_code(404); return ['error' => 'Not found']; }

        foreach ($holes as $h) {
            $stmt = $db->prepare(
                "UPDATE holes SET par = ?, yardage = ? WHERE course_id = ? AND hole_number = ?"
            );
            $stmt->execute([(int)$h['par'], (int)$h['yardage'], $courseId, (int)$h['hole_number']]);
        }

        $stmt = $db->prepare("SELECT * FROM holes WHERE course_id = ? ORDER BY hole_number");
        $stmt->execute([$courseId]);
        return ['course' => $course, 'holes' => $stmt->fetchAll()];
    },

    // ── Update tournament name ────────────────────────────────
    'PUT /tournaments/{tournamentId}' => function (PDO $db, array $p): array {
        $body = json_decode(file_get_contents('php://input'), true);
        $name = trim($body['name'] ?? '');
        if (!$name) { http_response_code(400); return ['error' => 'name required']; }

        $stmt = $db->prepare("UPDATE tournaments SET name = ? WHERE id = ? RETURNING *");
        $stmt->execute([$name, (int)$p['tournamentId']]);
        return $stmt->fetch() ?: (http_response_code(404) && ['error' => 'Not found']);
    },

    // ── Create round ──────────────────────────────────────────
    'POST /rounds' => function (PDO $db, array $p): array {
        $body = json_decode(file_get_contents('php://input'), true);
        $seasonId      = (int)($body['season_id'] ?? 0);
        $tournamentId  = isset($body['tournament_id']) ? (int)$body['tournament_id'] : null;
        $courseId      = (int)($body['course_id'] ?? 0);
        $nine          = $body['nine'] ?? '';
        $playedDate    = $body['played_date'] ?? '';
        $isPractice    = (bool)($body['is_practice'] ?? false);
        $ctpHole       = isset($body['ctp_hole']) ? (int)$body['ctp_hole'] : null;
        $ctpYardage    = isset($body['ctp_yardage']) ? (int)$body['ctp_yardage'] : null;
        $ctpPrize      = (float)($body['ctp_prize_amount'] ?? 20.00);
        $chipInPot     = (float)($body['chip_in_pot'] ?? 0.00);

        if (!$seasonId || !$courseId || !in_array($nine, ['front', 'back']) || !$playedDate) {
            http_response_code(400);
            return ['error' => 'season_id, course_id, nine, played_date required'];
        }

        // Auto-calculate round number if not provided
        $roundNumber = (int)($body['round_number'] ?? 0);
        if (!$roundNumber) {
            $stmt = $db->prepare("SELECT COALESCE(MAX(round_number), 0) + 1 FROM rounds WHERE season_id = ?");
            $stmt->execute([$seasonId]);
            $roundNumber = (int)$stmt->fetchColumn();
        }

        $stmt = $db->prepare("
            INSERT INTO rounds (season_id, tournament_id, round_number, course_id, nine, played_date,
                                is_practice, ctp_hole, ctp_yardage, ctp_prize_amount, chip_in_pot)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
        ");
        $stmt->execute([$seasonId, $tournamentId, $roundNumber, $courseId, $nine, $playedDate,
                        $isPractice, $ctpHole, $ctpYardage, $ctpPrize, $chipInPot]);
        $id = $stmt->fetchColumn();

        $stmt = $db->prepare("
            SELECT r.*, c.name as course_name
            FROM rounds r JOIN courses c ON c.id = r.course_id
            WHERE r.id = ?
        ");
        $stmt->execute([$id]);
        return $stmt->fetch();
    },

    // ── Update round (CTP hole, date, prize) ──────────────────
    'PUT /rounds/{roundId}' => function (PDO $db, array $p): array {
        $roundId = (int)$p['roundId'];
        $body = json_decode(file_get_contents('php://input'), true);

        $allowed = ['ctp_hole', 'ctp_yardage', 'ctp_prize_amount', 'chip_in_pot', 'played_date'];
        $sets = [];
        $vals = [];
        foreach ($allowed as $field) {
            if (array_key_exists($field, $body)) {
                $sets[] = "$field = ?";
                $vals[] = $body[$field];
            }
        }
        if (empty($sets)) { http_response_code(400); return ['error' => 'Nothing to update']; }
        $vals[] = $roundId;

        $stmt = $db->prepare("UPDATE rounds SET " . implode(', ', $sets) . " WHERE id = ? RETURNING *");
        $stmt->execute($vals);
        return $stmt->fetch() ?: (http_response_code(404) && ['error' => 'Not found']);
    },

    // ── Get rounds for a season (all, including practice) ─────
    'GET /seasons/{seasonId}/rounds' => function (PDO $db, array $p): array {
        $stmt = $db->prepare("
            SELECT r.*, c.name as course_name, t.number as tournament_number, t.name as tournament_name
            FROM rounds r
            JOIN courses c ON c.id = r.course_id
            LEFT JOIN tournaments t ON t.id = r.tournament_id
            WHERE r.season_id = ?
            ORDER BY r.played_date, r.id
        ");
        $stmt->execute([(int)$p['seasonId']]);
        return $stmt->fetchAll();
    },

    // ── Batch score entry for a round ────────────────────────
    // Body: { scores: [{ player_id, holes: { "1": 4, "2": 5, ... } }, ...] }
    'POST /rounds/{roundId}/scores/batch' => function (PDO $db, array $p): array {
        $roundId = (int)$p['roundId'];
        $body = json_decode(file_get_contents('php://input'), true);
        $scoresets = $body['scores'] ?? [];

        if (empty($scoresets)) { http_response_code(400); return ['error' => 'scores array required']; }

        $stmt = $db->prepare("
            INSERT INTO scores (round_id, player_id, hole_number, strokes)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (round_id, player_id, hole_number) DO UPDATE SET strokes = EXCLUDED.strokes
        ");

        $db->beginTransaction();
        try {
            foreach ($scoresets as $set) {
                $playerId = (int)($set['player_id'] ?? 0);
                if (!$playerId) continue;
                foreach ($set['holes'] as $holeData) {
                    // Accept both array-of-objects [{hole_number:N, strokes:N}] and keyed [N => strokes]
                    if (is_array($holeData)) {
                        $holeNum = (int)$holeData['hole_number'];
                        $strokes = $holeData['strokes'] !== null ? (int)$holeData['strokes'] : null;
                    } else {
                        continue; // skip malformed
                    }
                    if (!$holeNum) continue;
                    $stmt->execute([$roundId, $playerId, $holeNum, $strokes]);
                }
            }
            $db->commit();
        } catch (Throwable $e) {
            $db->rollBack();
            http_response_code(500);
            return ['error' => $e->getMessage()];
        }

        return ['success' => true, 'rounds_affected' => count($scoresets)];
    },

    // ── Get all scores for a round (raw, for score entry UI) ─
    'GET /rounds/{roundId}/scores' => function (PDO $db, array $p): array {
        $stmt = $db->prepare("
            SELECT s.player_id, p.name as player_name, s.hole_number, s.strokes
            FROM scores s
            JOIN players p ON p.id = s.player_id
            WHERE s.round_id = ?
            ORDER BY p.name, s.hole_number
        ");
        $stmt->execute([(int)$p['roundId']]);
        $rows = $stmt->fetchAll();

        // Group by player
        $byPlayer = [];
        foreach ($rows as $row) {
            $pid = $row['player_id'];
            if (!isset($byPlayer[$pid])) {
                $byPlayer[$pid] = ['player_id' => $pid, 'player_name' => $row['player_name'], 'holes' => []];
            }
            $byPlayer[$pid]['holes'][$row['hole_number']] = $row['strokes'];
        }
        return array_values($byPlayer);
    },

    // ── Set handicap manually ─────────────────────────────────
    'PUT /handicaps' => function (PDO $db, array $p): array {
        $body = json_decode(file_get_contents('php://input'), true);
        $playerId          = (int)($body['player_id'] ?? 0);
        $seasonId          = (int)($body['season_id'] ?? 0);
        $tournamentId      = isset($body['tournament_id']) ? (int)$body['tournament_id'] : null;
        $tournamentNumber  = (int)($body['tournament_number'] ?? 0);
        $value             = (float)($body['value'] ?? 0);

        if (!$playerId || !$seasonId || !$tournamentNumber) {
            http_response_code(400);
            return ['error' => 'player_id, season_id, tournament_number required'];
        }

        $stmt = $db->prepare("
            INSERT INTO handicaps (player_id, tournament_id, season_id, tournament_number, value)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (player_id, season_id, tournament_number) DO UPDATE SET value = EXCLUDED.value
        ");
        $stmt->execute([$playerId, $tournamentId, $seasonId, $tournamentNumber, $value]);
        return ['success' => true];
    },

    // ── Recalculate handicaps for T2 and T3 ──────────────────
    // T2 handicap = avg gross over T1 rounds − avg par
    // T3 handicap = avg gross over T1+T2 rounds − avg par
    'POST /seasons/{seasonId}/recalculate-handicaps/{tournamentNumber}' => function (PDO $db, array $p): array {
        $seasonId = (int)$p['seasonId'];
        $tNum     = (int)$p['tournamentNumber']; // 1, 2, or 3

        // For T1: use practice rounds (existing logic)
        // For T2: use T1 rounds
        // For T3: use T1+T2 rounds
        if ($tNum === 1) {
            // Delegate to existing T1 logic via the existing endpoint logic
            $players = $db->query("SELECT * FROM players")->fetchAll();
            $stmt = $db->prepare("SELECT * FROM tournaments WHERE season_id = ? ORDER BY number");
            $stmt->execute([$seasonId]);
            $tournaments = $stmt->fetchAll();
            $t = array_values(array_filter($tournaments, fn($t) => $t['number'] === 1))[0] ?? null;
            $updated = [];
            foreach ($players as $player) {
                $h = calculateHandicap($db, $player['id'], $seasonId);
                if ($t) {
                    $stmt2 = $db->prepare("
                        INSERT INTO handicaps (player_id, tournament_id, season_id, tournament_number, value)
                        VALUES (?, ?, ?, 1, ?)
                        ON CONFLICT (player_id, season_id, tournament_number) DO UPDATE SET value = EXCLUDED.value
                    ");
                    $stmt2->execute([$player['id'], $t['id'], $seasonId, $h]);
                }
                $updated[] = ['player_id' => $player['id'], 'name' => $player['name'], 'handicap' => $h];
            }
            return ['tournament_number' => 1, 'updated' => $updated];
        }

        // For T2/T3: avg gross across prior tournament rounds minus avg par
        $priorTNums = $tNum === 2 ? [1] : [1, 2];

        $stmt = $db->prepare("SELECT * FROM tournaments WHERE season_id = ? ORDER BY number");
        $stmt->execute([$seasonId]);
        $allTournaments = $stmt->fetchAll();

        $priorTournaments = array_values(array_filter($allTournaments, fn($t) => in_array($t['number'], $priorTNums)));
        $targetTournament = array_values(array_filter($allTournaments, fn($t) => $t['number'] === $tNum))[0] ?? null;

        if (!$targetTournament) { http_response_code(404); return ['error' => "Tournament $tNum not found"]; }

        $players = $db->query("SELECT * FROM players")->fetchAll();
        $updated = [];

        foreach ($players as $player) {
            $pid = $player['id'];
            $totalGross = 0;
            $totalPar = 0;
            $roundsPlayed = 0;

            foreach ($priorTournaments as $pt) {
                $stmt = $db->prepare("SELECT id, course_id FROM rounds WHERE tournament_id = ? AND is_practice = FALSE");
                $stmt->execute([$pt['id']]);
                $rounds = $stmt->fetchAll();

                foreach ($rounds as $round) {
                    $stmt2 = $db->prepare("
                        SELECT strokes FROM scores
                        WHERE round_id = ? AND player_id = ? AND strokes IS NOT NULL
                    ");
                    $stmt2->execute([$round['id'], $pid]);
                    $holeScores = $stmt2->fetchAll(PDO::FETCH_COLUMN);

                    if (count($holeScores) < 9) continue;

                    $totalGross += array_sum($holeScores);
                    $roundsPlayed++;

                    $stmt3 = $db->prepare("SELECT SUM(par) FROM holes WHERE course_id = ?");
                    $stmt3->execute([$round['course_id']]);
                    $totalPar += (int)$stmt3->fetchColumn();
                }
            }

            $handicap = $roundsPlayed > 0 ? max(0, $totalGross - $totalPar) : 0;

            $stmt = $db->prepare("
                INSERT INTO handicaps (player_id, tournament_id, season_id, tournament_number, value)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (player_id, season_id, tournament_number) DO UPDATE SET value = EXCLUDED.value
            ");
            $stmt->execute([$pid, $targetTournament['id'], $seasonId, $tNum, $handicap]);
            $updated[] = ['player_id' => $pid, 'name' => $player['name'], 'handicap' => $handicap];
        }

        return ['tournament_number' => $tNum, 'updated' => $updated];
    },

    // ── Award tournament prizes ───────────────────────────────
    'POST /tournaments/{tournamentId}/award-prizes' => function (PDO $db, array $p): array {
        $tid = (int)$p['tournamentId'];

        $stmt = $db->prepare("SELECT t.*, s.id as season_id FROM tournaments t JOIN seasons s ON s.id = t.season_id WHERE t.id = ?");
        $stmt->execute([$tid]);
        $tournament = $stmt->fetch();
        if (!$tournament) { http_response_code(404); return ['error' => 'Not found']; }

        // Get all rounds for this tournament and sum points per player
        $stmt = $db->prepare("SELECT id FROM rounds WHERE tournament_id = ? AND is_practice = FALSE");
        $stmt->execute([$tid]);
        $roundIds = $stmt->fetchAll(PDO::FETCH_COLUMN);

        $playerPoints = [];
        $players = $db->query("SELECT * FROM players")->fetchAll();
        foreach ($players as $p2) { $playerPoints[$p2['id']] = ['name' => $p2['name'], 'points' => 0]; }

        foreach ($roundIds as $rid) {
            $scorecard = getRoundScorecard($db, (int)$rid);
            foreach ($scorecard['players'] as $row) {
                $playerPoints[$row['player_id']]['points'] += $row['points'] ?? 0;
            }
        }

        // Sort by points desc
        uasort($playerPoints, fn($a, $b) => $b['points'] <=> $a['points']);
        $ranked = array_keys($playerPoints);

        $prizes = [];

        // 1st place: $200
        if (isset($ranked[0])) {
            $stmt = $db->prepare("
                INSERT INTO prize_winnings (player_id, tournament_id, season_id, type, amount, description)
                VALUES (?, ?, ?, 't1st', 200, ?)
            ");
            $desc = "Tournament {$tournament['number']} - 1st Place";
            $stmt->execute([$ranked[0], $tid, $tournament['season_id'], $desc]);
            $prizes[] = ['player_id' => $ranked[0], 'type' => '1st', 'amount' => 200];
        }

        // 2nd place: $50
        if (isset($ranked[1])) {
            $stmt = $db->prepare("
                INSERT INTO prize_winnings (player_id, tournament_id, season_id, type, amount, description)
                VALUES (?, ?, ?, 't2nd', 50, ?)
            ");
            $desc = "Tournament {$tournament['number']} - 2nd Place";
            $stmt->execute([$ranked[1], $tid, $tournament['season_id'], $desc]);
            $prizes[] = ['player_id' => $ranked[1], 'type' => '2nd', 'amount' => 50];
        }

        return ['tournament' => $tournament, 'prizes_awarded' => $prizes];
    },

    // ── Delete prize winning ──────────────────────────────────
    'DELETE /prize-winnings/{id}' => function (PDO $db, array $p): array {
        $stmt = $db->prepare("DELETE FROM prize_winnings WHERE id = ? RETURNING id");
        $stmt->execute([(int)$p['id']]);
        $deleted = $stmt->fetchColumn();
        if (!$deleted) { http_response_code(404); return ['error' => 'Not found']; }
        return ['success' => true, 'deleted_id' => $deleted];
    },

];
