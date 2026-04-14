<?php
declare(strict_types=1);

/**
 * Calculate points for a round given an array of net scores (keyed by player_id).
 * Net score = gross - handicap. Lower is better.
 * Absent players (null net score) share the bottom positions.
 *
 * Returns array keyed by player_id => points (float)
 */
function calculatePoints(array $netScores, int $totalPlayers = 8): array
{
    $numPlayers = count($netScores);
    if ($numPlayers === 0) return [];

    // Separate present and absent
    $present = [];
    $absent = [];
    foreach ($netScores as $playerId => $net) {
        if ($net === null) {
            $absent[] = $playerId;
        } else {
            $present[$playerId] = $net;
        }
    }

    // Sort present players by net score ascending (lower = better)
    asort($present);

    // Build ranking with tied positions
    // Available points: 8 down to 1 for up to 8 players
    // With more/fewer players, the points are still 8 down to 1 across totalPlayers slots
    // Absent players fill the worst positions
    $points = [];
    $rank = 1; // 1 = best

    $presentIds = array_keys($present);
    $scores = array_values($present);
    $i = 0;
    while ($i < count($presentIds)) {
        // Find tied group
        $j = $i;
        while ($j < count($scores) - 1 && $scores[$j] === $scores[$j + 1]) {
            $j++;
        }
        // Players $i..$j are tied at this score
        $tiedCount = $j - $i + 1;
        // Points for positions rank .. rank+tiedCount-1 (rank=1 is best=8 pts)
        $tiedPoints = 0;
        for ($k = 0; $k < $tiedCount; $k++) {
            $pos = $rank + $k; // position 1=best
            $tiedPoints += max(1, $totalPlayers - $pos + 1);
        }
        $avgPoints = $tiedPoints / $tiedCount;
        for ($k = 0; $k < $tiedCount; $k++) {
            $points[$presentIds[$i + $k]] = $avgPoints;
        }
        $rank += $tiedCount;
        $i = $j + 1;
    }

    // Absent players share remaining worst positions
    if (count($absent) > 0) {
        $absentPoints = 0;
        for ($k = 0; $k < count($absent); $k++) {
            $pos = $rank + $k;
            $absentPoints += max(1, $totalPlayers - $pos + 1);
        }
        $avgAbsent = $absentPoints / count($absent);
        foreach ($absent as $playerId) {
            $points[$playerId] = $avgAbsent;
        }
    }

    return $points;
}

/**
 * Calculate handicap from practice rounds.
 * Handicap = sum of gross scores over practice rounds - total par.
 */
function calculateHandicap(PDO $db, int $playerId, int $seasonId): float
{
    // Get practice round IDs for this season
    $stmt = $db->prepare("
        SELECT r.id, r.course_id, r.nine
        FROM rounds r
        WHERE r.season_id = ? AND r.is_practice = TRUE
        ORDER BY r.round_number
    ");
    $stmt->execute([$seasonId]);
    $practiceRounds = $stmt->fetchAll();

    if (empty($practiceRounds)) return 0.0;

    $totalGross = 0;
    $totalPar = 0;
    $roundsPlayed = 0;

    foreach ($practiceRounds as $round) {
        // Get player's scores for this round
        $stmt2 = $db->prepare("
            SELECT s.strokes FROM scores s
            WHERE s.round_id = ? AND s.player_id = ? AND s.strokes IS NOT NULL
        ");
        $stmt2->execute([$round['id'], $playerId]);
        $holeScores = $stmt2->fetchAll(PDO::FETCH_COLUMN);

        if (count($holeScores) < 9) continue; // Player absent

        $gross = array_sum($holeScores);
        $totalGross += $gross;
        $roundsPlayed++;

        // Get par for this course
        $stmt3 = $db->prepare("SELECT SUM(par) FROM holes WHERE course_id = ?");
        $stmt3->execute([$round['course_id']]);
        $par = (int)$stmt3->fetchColumn();
        $totalPar += $par;
    }

    if ($roundsPlayed === 0) return 0.0;

    return max(0, $totalGross - $totalPar);
}

/**
 * Get round scorecard data with net scores and points for all players.
 */
function getRoundScorecard(PDO $db, int $roundId): array
{
    // Get round info
    $stmt = $db->prepare("
        SELECT r.*, c.name as course_name
        FROM rounds r
        JOIN courses c ON c.id = r.course_id
        WHERE r.id = ?
    ");
    $stmt->execute([$roundId]);
    $round = $stmt->fetch();
    if (!$round) return [];

    // Get holes for this course (only the nine being played)
    $nineFilter = ($round['nine'] === 'back')
        ? 'AND hole_number BETWEEN 10 AND 18'
        : 'AND hole_number BETWEEN 1 AND 9';
    $stmt = $db->prepare("
        SELECT hole_number, par, yardage
        FROM holes
        WHERE course_id = ? $nineFilter
        ORDER BY hole_number
    ");
    $stmt->execute([$round['course_id']]);
    $holes = $stmt->fetchAll();

    // Get tournament_id for handicap lookup
    $tournamentId = $round['tournament_id'];
    $seasonId = $round['season_id'];

    // Get players for this season (only those with scores or handicaps in this season)
    $stmt = $db->prepare("
        SELECT p.id, p.name,
               h.value as handicap
        FROM players p
        LEFT JOIN handicaps h ON h.player_id = p.id
            AND h.season_id = ?
            AND h.tournament_number = (
                SELECT t.number FROM tournaments t WHERE t.id = ?
            )
        WHERE p.id IN (
            SELECT DISTINCT s.player_id FROM scores s
            JOIN rounds r ON r.id = s.round_id
            WHERE r.season_id = ?
        )
        ORDER BY p.name
    ");
    $stmt->execute([$seasonId, $tournamentId, $seasonId]);
    $players = $stmt->fetchAll();

    // Get all scores for this round
    $stmt = $db->prepare("
        SELECT player_id, hole_number, strokes
        FROM scores
        WHERE round_id = ?
    ");
    $stmt->execute([$roundId]);
    $rawScores = $stmt->fetchAll();

    // Index scores by player_id => hole_number => strokes
    $scoreIndex = [];
    foreach ($rawScores as $row) {
        $scoreIndex[$row['player_id']][$row['hole_number']] = $row['strokes'];
    }

    // Build player rows with gross totals and net scores
    $netScores = [];
    $playerRows = [];
    foreach ($players as $player) {
        $pid = $player['id'];
        $holeStrokes = $scoreIndex[$pid] ?? [];
        $gross = null;
        $absent = false;

        if (count($holeStrokes) < 9) {
            $absent = true;
        } else {
            $hasNull = in_array(null, $holeStrokes, true);
            $gross = $hasNull ? null : array_sum($holeStrokes);
            $absent = $gross === null;
        }

        $handicap = $player['handicap'] !== null ? (float)$player['handicap'] : 0.0;
        $net = $absent ? null : ($gross - $handicap);
        $netScores[$pid] = $net;

        $playerRows[$pid] = [
            'player_id'  => $pid,
            'name'       => $player['name'],
            'handicap'   => $handicap,
            'holes'      => $holeStrokes,
            'gross'      => $gross,
            'net'        => $net,
            'absent'     => $absent,
        ];
    }

    // Round net scores to nearest half stroke for ranking
    $rankingScores = [];
    foreach ($netScores as $pid => $n) {
        if ($n === null) { $rankingScores[$pid] = null; continue; }
        $rankingScores[$pid] = round((float)$n * 2) / 2;
    }
    $points = calculatePoints($rankingScores, count($players));
    foreach ($points as $pid => $pts) {
        $playerRows[$pid]['points'] = $pts;
    }

    return [
        'round'   => $round,
        'holes'   => $holes,
        'players' => array_values($playerRows),
    ];
}
