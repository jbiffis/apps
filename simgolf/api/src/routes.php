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
            $stmt = $db->prepare("SELECT id FROM rounds WHERE tournament_id = ? AND is_practice = 0");
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
            ON DUPLICATE KEY UPDATE strokes = VALUES(strokes)
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
            WHERE r.season_id = ? AND r.is_practice = 1
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

            $tData = ['tournament' => $tournament, 'rounds' => [], 'total' => 0];
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

        $stmt = $db->prepare("INSERT INTO closest_to_pin (round_id, player_id, distance_feet) VALUES (?, ?, ?)");
        $stmt->execute([$roundId, $playerId, $distance]);
        return ['id' => $db->lastInsertId()];
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
                WHERE (tournament_id = ? OR (is_practice = 1 AND season_id = ?))
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
        ");
        $desc = "Round {$round['round_number']} - Chip In";
        $stmt->execute([$playerId, $roundId, $round['tournament_id'], $round['season_id'], $amount, $desc]);

        return ['success' => true, 'id' => $db->lastInsertId()];
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
            ON DUPLICATE KEY UPDATE strokes = VALUES(strokes)
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
                    ON DUPLICATE KEY UPDATE value = VALUES(value)
                ");
                $stmt2->execute([$pid, $t1['id'], $seasonId, $h1]);
            }

            $updated[] = ['player_id' => $pid, 'name' => $player['name'], 't1_handicap' => $h1];
        }

        return ['updated' => $updated];
    },

];
