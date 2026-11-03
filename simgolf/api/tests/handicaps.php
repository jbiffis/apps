<?php
declare(strict_types=1);

require_once __DIR__ . '/../src/scoring.php';

function check(bool $condition, string $description): void
{
    if (!$condition) throw new RuntimeException($description);
}

check(bestRoundHandicap([10, 12], 1) === 22.0, 'T1 combines both practice rounds');
check(bestRoundHandicap([8, 9, 10, 11, 12, 13, 99, 100], 2) === 10.5, 'T2 averages the lowest six');
check(bestRoundHandicap(range(1, 14), 3) === 5.5, 'T3 averages the lowest ten');
check(bestRoundHandicap([0, 0, 0, 0, 0, 1], 2) === 0.2, 'Round only the final handicap to one decimal');
check(bestRoundHandicap([-10, -8], 1) === -18.0, 'No floor on handicap');
check(bestRoundHandicap([100, 200, 300, 400, 500, 600], 2) === 350.0, 'No handicap cap');
check(bestRoundHandicap([10], 1) === null, 'T1 needs both practice rounds');
check(bestRoundHandicap(range(1, 5), 2) === null, 'T2 needs six complete rounds');
check(bestRoundHandicap(range(1, 9), 3) === null, 'T3 needs ten complete rounds');

$db = require __DIR__ . '/../src/db.php';
$db->beginTransaction();
try {
    $insert = function (string $sql, array $values) use ($db): int {
        $stmt = $db->prepare($sql . ' RETURNING id');
        $stmt->execute($values);
        return (int)$stmt->fetchColumn();
    };
    $season = $insert('INSERT INTO seasons (year, name, handicap_system) VALUES (?, ?, ?)', [2098, 'Handicap test', BEST_ROUND_HANDICAPS]);
    $legacy = $insert('INSERT INTO seasons (year, name) VALUES (?, ?)', [2097, 'Legacy test']);
    $players = [];
    foreach (['Handicap test A', 'Handicap test B'] as $name) {
        $players[] = $insert('INSERT INTO players (name) VALUES (?)', [$name]);
    }
    $courses = [];
    foreach ([4, 3] as $par) {
        $course = $insert('INSERT INTO courses (name) VALUES (?)', ['Handicap test course']);
        $courses[] = $course;
        for ($hole = 1; $hole <= 9; $hole++) {
            $insert('INSERT INTO holes (course_id, hole_number, par, yardage) VALUES (?, ?, ?, ?)', [$course, $hole, $par, 150]);
        }
    }
    $tournaments = [];
    for ($number = 1; $number <= 3; $number++) {
        $tournaments[$number] = $insert('INSERT INTO tournaments (season_id, number, name) VALUES (?, ?, ?)', [$season, $number, "Test T$number"]);
    }
    $addRound = function (int $sid, ?int $tid, int $number, int $course, int $par, int $diff, string $nine = 'front') use ($insert, $db, $players): int {
        $rid = $insert('INSERT INTO rounds (season_id, tournament_id, round_number, course_id, nine, played_date, is_practice)
            VALUES (?, ?, ?, ?, ?, ?, ?)', [$sid, $tid, $number, $course, $nine, '2098-01-01', $tid === null ? 'true' : 'false']);
        $stmt = $db->prepare('INSERT INTO scores (round_id, player_id, hole_number, strokes) VALUES (?, ?, ?, ?)');
        for ($hole = 1; $hole <= 9; $hole++) {
            $stmt->execute([$rid, $players[0], $hole, $par + ($hole === 1 ? $diff : 0)]);
        }
        return $rid;
    };
    $practice = $addRound($season, null, 1, $courses[0], 4, 8);
    $addRound($season, null, 2, $courses[1], 3, 9, 'back');
    for ($n = 1; $n <= 6; $n++) {
        $addRound($season, $tournaments[1], $n, $courses[0], 4, [10, 11, 12, 13, 80, 90][$n - 1]);
        $addRound($season, $tournaments[2], $n, $courses[0], 4, 13 + $n);
    }
    // Attractive extra/future/other-season scores must not enter the pools.
    $addRound($season, null, 3, $courses[0], 4, 0);
    $addRound($season, $tournaments[1], 7, $courses[0], 4, 0);
    $addRound($season, $tournaments[3], 1, $courses[0], 4, 0);
    $addRound($legacy, null, 1, $courses[0], 4, 0);
    $stmt = $db->prepare('INSERT INTO scores (round_id, player_id, hole_number, strokes) VALUES (?, ?, ?, ?)');
    for ($hole = 1; $hole <= 9; $hole++) {
        $stmt->execute([$practice, $players[1], $hole, $hole === 9 ? null : 4]);
    }
    foreach ([1 => [17.0, 2], 2 => [10.5, 8], 3 => [12.5, 14]] as $number => [$expected, $count]) {
        $rows = bestRoundHandicapCandidates($db, $season, $number);
        $indexed = array_column($rows, null, 'player_id');
        check($indexed[$players[0]]['handicap'] === $expected, "T$number: correct differential selection and averaging");
        check($indexed[$players[0]]['eligible_rounds'] === $count, "T$number: correct pool size");
        check($indexed[$players[1]]['handicap'] === null, 'Incomplete scores do not become zero');
    }
    check(seasonHandicapSystem($db, $legacy) === LEGACY_HANDICAPS, 'Existing/default seasons keep legacy rules');
    check(seasonHasTournamentScores($db, $season), 'Tournament scores lock system changes');

    $legacyTournament = $insert('INSERT INTO tournaments (season_id, number, name) VALUES (?, ?, ?)', [$legacy, 1, 'Legacy T1']);
    foreach ([[$season, $tournaments[3]], [$legacy, $legacyTournament]] as [$sid, $tid]) {
        $rid = $addRound($sid, $tid, 2, $courses[0], 4, 9);
        for ($hole = 1; $hole <= 9; $hole++) $stmt->execute([$rid, $players[1], $hole, 5]);
        foreach ([8.6, 8.9] as $i => $value) {
            $insert('INSERT INTO handicaps (player_id, season_id, tournament_id, tournament_number, value) VALUES (?, ?, ?, ?, ?)', [$players[$i], $sid, $tid, $sid === $season ? 3 : 1, $value]);
        }
        $scorecard = getRoundScorecard($db, $rid);
        $rows = array_column($scorecard['players'], null, 'player_id');
        if ($sid === $season) {
            check((float)$rows[$players[0]]['points'] === 1.0 && (float)$rows[$players[1]]['points'] === 2.0, 'New system ranks 36.1 ahead of 36.4');
        } else {
            check($rows[$players[0]]['points'] === 1.5 && $rows[$players[1]]['points'] === 1.5, 'Legacy whole-score ranking stays unchanged');
        }
    }
    echo "PASS: handicap formulas, pools, par comparison, incomplete rounds, uncapped scores, and legacy/new ranking isolation\n";
} finally {
    $db->rollBack();
}
