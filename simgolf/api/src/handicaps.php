<?php
declare(strict_types=1);

const LEGACY_HANDICAPS = 'legacy';
const BEST_ROUND_HANDICAPS = 'best_rounds_v1';

function seasonHandicapSystem(PDO $db, int $seasonId): ?string
{
    $stmt = $db->prepare('SELECT handicap_system FROM seasons WHERE id = ?');
    $stmt->execute([$seasonId]);
    $system = $stmt->fetchColumn();
    return $system === false ? null : $system;
}

function seasonHasTournamentScores(PDO $db, int $seasonId, ?int $number = null): bool
{
    $stmt = $db->prepare('SELECT EXISTS (
        SELECT 1 FROM scores s
        JOIN rounds r ON r.id = s.round_id
        LEFT JOIN tournaments t ON t.id = r.tournament_id
        WHERE r.season_id = ? AND r.is_practice = FALSE AND s.strokes IS NOT NULL
        AND (?::int IS NULL OR t.number = ?)
    )');
    $stmt->execute([$seasonId, $number, $number]);
    return (bool)$stmt->fetchColumn();
}

/** Scores are gross minus the par for that round, with no per-hole adjustment. */
function bestRoundHandicap(array $differentials, int $number): ?float
{
    $required = [1 => 2, 2 => 6, 3 => 10][$number] ?? 0;
    if (!$required || count($differentials) < $required) return null;
    sort($differentials, SORT_NUMERIC);
    $selected = array_slice($differentials, 0, $required);
    // T1 intentionally uses the combined two-practice-round total.
    $value = array_sum($selected) / ($number === 1 ? 1 : $required);
    return round($value, 1, PHP_ROUND_HALF_UP);
}

/**
 * Only this season's first two practice rounds and first six rounds in each
 * prior tournament are eligible. Incomplete/absent rounds never count as zero.
 */
function bestRoundHandicapCandidates(PDO $db, int $seasonId, int $number): array
{
    $stmt = $db->prepare('SELECT DISTINCT p.id, p.name FROM players p
        JOIN scores s ON s.player_id = p.id JOIN rounds r ON r.id = s.round_id
        WHERE r.season_id = ? ORDER BY p.name');
    $stmt->execute([$seasonId]);
    $players = $stmt->fetchAll();

    $stmt = $db->prepare("WITH ranked_rounds AS (
        SELECT r.*, ROW_NUMBER() OVER (
            PARTITION BY r.is_practice, CASE WHEN r.is_practice THEN 0 ELSE COALESCE(t.number, 0) END
            ORDER BY r.round_number, r.id
        ) AS slot
        FROM rounds r LEFT JOIN tournaments t ON t.id = r.tournament_id
        WHERE r.season_id = ? AND (r.is_practice = TRUE OR
            (r.is_practice = FALSE AND t.season_id = r.season_id AND t.number < ?))
    ), eligible_rounds AS (
        SELECT * FROM ranked_rounds WHERE slot <= CASE WHEN is_practice THEN 2 ELSE 6 END
    )
    SELECT s.player_id, r.id AS round_id, SUM(s.strokes) - SUM(h.par) AS differential
    FROM eligible_rounds r
    JOIN holes h ON h.course_id = r.course_id AND h.hole_number BETWEEN
        CASE WHEN r.nine = 'back' AND EXISTS (
            SELECT 1 FROM holes back_holes WHERE back_holes.course_id = r.course_id
            AND back_holes.hole_number BETWEEN 10 AND 18
        ) THEN 10 ELSE 1 END
        AND CASE WHEN r.nine = 'back' AND EXISTS (
            SELECT 1 FROM holes back_holes WHERE back_holes.course_id = r.course_id
            AND back_holes.hole_number BETWEEN 10 AND 18
        ) THEN 18 ELSE 9 END
    JOIN scores s ON s.round_id = r.id AND s.hole_number = h.hole_number
    GROUP BY s.player_id, r.id
    HAVING COUNT(h.id) = 9 AND COUNT(s.strokes) = 9
    ORDER BY s.player_id, differential, r.id");
    $stmt->execute([$seasonId, $number]);
    $differentials = [];
    foreach ($stmt->fetchAll() as $row) {
        $differentials[$row['player_id']][] = (float)$row['differential'];
    }
    $required = [1 => 2, 2 => 6, 3 => 10][$number];
    return array_map(function ($player) use ($differentials, $required, $number) {
        $scores = $differentials[$player['id']] ?? [];
        return [
            'player_id' => $player['id'], 'name' => $player['name'],
            'handicap' => bestRoundHandicap($scores, $number),
            'eligible_rounds' => count($scores), 'required_rounds' => $required,
        ];
    }, $players);
}

function recalculateBestRoundHandicaps(PDO $db, int $seasonId, int $number): array
{
    if (!in_array($number, [1, 2, 3], true)) {
        http_response_code(400); return ['error' => 'Tournament number must be 1, 2 or 3'];
    }
    $db->beginTransaction();
    // Serialize recalculations and system changes for the same season.
    $stmt = $db->prepare('SELECT handicap_system FROM seasons WHERE id = ? FOR UPDATE');
    $stmt->execute([$seasonId]);
    if ($stmt->fetchColumn() !== BEST_ROUND_HANDICAPS) {
        $db->rollBack(); http_response_code(409);
        return ['error' => 'The season handicap system changed. Reload and try again.'];
    }
    $stmt = $db->prepare('SELECT id FROM tournaments WHERE season_id = ? AND number = ?');
    $stmt->execute([$seasonId, $number]);
    $tournamentId = $stmt->fetchColumn();
    if (!$tournamentId) {
        $db->rollBack(); http_response_code(404); return ['error' => 'Tournament not found'];
    }
    if (seasonHasTournamentScores($db, $seasonId, $number)) {
        $db->rollBack(); http_response_code(409);
        return ['error' => 'Calculate handicaps before entering scores for this tournament'];
    }
    $updated = [];
    $skipped = [];
    foreach (bestRoundHandicapCandidates($db, $seasonId, $number) as $row) {
        if ($row['handicap'] === null) {
            $skipped[] = $row;
            continue;
        }
        $stmt = $db->prepare('INSERT INTO handicaps
            (player_id, tournament_id, season_id, tournament_number, value)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (player_id, season_id, tournament_number)
            DO UPDATE SET value = EXCLUDED.value, tournament_id = EXCLUDED.tournament_id');
        $stmt->execute([$row['player_id'], $tournamentId, $seasonId, $number, $row['handicap']]);
        $updated[] = $row;
    }
    $db->commit();
    return ['tournament_number' => $number, 'updated' => $updated, 'skipped' => $skipped];
}
