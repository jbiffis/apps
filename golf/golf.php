<?php
/**
 * Golf round interpretation, storage, and aggregation on top of the FIT
 * decoder (fit.php). Understands Garmin's golf messages (verified against
 * real Approach/handheld files):
 *
 *   msg 190 golf_course   : 0 id, 1 name, 3 start_time, 8 front_par,
 *                           9 back_par, 10 total_par, 11 tee, 12 slope,
 *                           14 length, 21 rating
 *   msg 193 golf_hole_def : 0 number, 1 length(cm), 2 par, 3 stroke_index,
 *                           4 pin_lat, 5 pin_lon
 *   msg 192 golf_hole_res : 1 number, 2 score, 3/5 putts (device-dependent)
 *   msg 191 golf_summary  : 0 player, 2 front_score, 3 back_score,
 *                           4 total_score, 5 total_putts
 *   msg 194 golf_shot     : 0 player, 1 hole, 2 start_lat, 3 start_lon,
 *                           4 end_lat, 5 end_lon
 *   msg 18  session (activity): time/distance/HR/ascent, msg 20 record = GPS.
 */

require_once __DIR__ . '/fit.php';

const GOLF_SCHEMA        = 1;
const GARMIN_EPOCH       = 631065600;          // 1989-12-31 UTC in unix secs
const GOLF_DATA_DIR      = '/var/www/data/golf';
const GOLF_MAX_UPLOAD    = 8 * 1024 * 1024;    // 8 MB per file
const GOLF_TRACK_POINTS  = 500;                // downsample target for storage
const METERS_TO_YARDS    = 1.0936133;

if (session_status() === PHP_SESSION_NONE) {
    session_start();
}

/* ----------------------------------------------------------------- utils */

function semicircles_to_deg(?int $v): ?float {
    return $v === null ? null : $v * (180 / (2 ** 31));
}

function garmin_to_unix(?int $v): ?int {
    return $v === null ? null : $v + GARMIN_EPOCH;
}

/** Derive UTC offset (seconds) by comparing a local filename stamp to a UTC ref. */
function derive_tz_offset(string $filename, ?int $utcRef): ?int {
    if ($utcRef === null) return null;
    if (!preg_match('/(\d{8})_(\d{6})/', $filename, $m)) return null;
    $local = strtotime($m[1] . 'T' . substr($m[2],0,2) . ':' . substr($m[2],2,2) . ':' . substr($m[2],4,2) . 'Z');
    if ($local === false) return null;
    // round to nearest 15 minutes
    return (int) round(($local - $utcRef) / 900) * 900;
}

function fmt_hms(?float $s): string {
    if ($s === null) return '—';
    $s = (int)$s;
    return sprintf('%d:%02d:%02d', intdiv($s,3600), intdiv($s%3600,60), $s%60);
}

/* -------------------------------------------------------------- parsing */

/**
 * Parse raw FIT bytes into a normalized round-source array.
 * @throws FitException on malformed input.
 */
function parse_round(string $bytes, string $filename): array {
    $fit = FitFile::parse($bytes);

    $by = [];
    foreach ($fit->messages as $msg) {
        $by[$msg['global']][] = $msg['fields'];
    }
    $counts = [];
    foreach ($by as $g => $rows) $counts[$g] = count($rows);
    ksort($counts);

    $fileId = $by[0][0] ?? [];
    $timeCreated = garmin_to_unix($fileId[4] ?? null);

    // latest timestamp in the file ≈ when it was written (matches the local
    // save-time encoded in SCORE_* filenames)
    $latest = $timeCreated;
    foreach ($by as $rows) {
        foreach ($rows as $f) {
            if (isset($f[253])) {
                $u = garmin_to_unix($f[253]);
                if ($u !== null && ($latest === null || $u > $latest)) $latest = $u;
            }
        }
    }

    // timezone: prefer activity(34).local_timestamp (reliable), else derive
    // from the filename's local stamp vs the file's write time.
    $tz = null; $tzReliable = false;
    if (isset($by[34][0][5], $by[34][0][253])) {
        $tz = (int) round(($by[34][0][5] - $by[34][0][253]) / 900) * 900;
        $tzReliable = true;
    }

    $activity  = parse_activity($by);
    $scorecard = parse_scorecard($by);

    // determine round start
    $startUnix = $activity['start_unix']
        ?? ($scorecard['course']['start_unix'] ?? $timeCreated);
    if ($tz === null) $tz = derive_tz_offset($filename, $latest ?? $startUnix);
    $tz ??= 0;

    $kind = 'other';
    if ($activity && $scorecard)      $kind = 'both';
    elseif ($scorecard)               $kind = 'scorecard';
    elseif ($activity)                $kind = 'activity';

    $roundDate = $startUnix !== null ? gmdate('Y-m-d', $startUnix + $tz) : 'unknown';

    return [
        'schema'       => GOLF_SCHEMA,
        'filename'     => $filename,
        'kind'         => $kind,
        'device'       => [
            'manufacturer' => $fileId[1] ?? null,
            'product'      => $fileId[2] ?? null,
            'serial'       => $fileId[3] ?? null,
        ],
        'time_created' => $timeCreated,
        'tz_offset'    => $tz,
        'tz_reliable'  => $tzReliable,
        'start_unix'   => $startUnix,
        'round_date'   => $roundDate,
        'activity'     => $activity,
        'scorecard'    => $scorecard,
        'msg_counts'   => $counts,
    ];
}

function parse_activity(array $by): ?array {
    $s = $by[18][0] ?? null;
    if ($s === null) return null;

    $out = [
        'sport'       => $s[110] ?? null,
        'start_unix'  => garmin_to_unix($s[2] ?? null),
        'end_unix'    => garmin_to_unix($s[253] ?? null),
        'elapsed_s'   => isset($s[7]) ? $s[7] / 1000 : null,
        'moving_s'    => isset($s[8]) ? $s[8] / 1000 : null,
        'distance_m'  => isset($s[9]) ? $s[9] / 100 : null,
        'calories'    => $s[11] ?? null,
        'avg_hr'      => $s[16] ?? null,
        'max_hr'      => $s[17] ?? null,
        'ascent_m'    => $s[22] ?? null,
        'descent_m'   => $s[23] ?? null,
        'start_lat'   => semicircles_to_deg($s[3] ?? null),
        'start_lon'   => semicircles_to_deg($s[4] ?? null),
    ];

    // GPS + HR track (downsampled)
    $records = $by[20] ?? [];
    $n = count($records);
    $step = $n > GOLF_TRACK_POINTS ? (int) ceil($n / GOLF_TRACK_POINTS) : 1;
    $track = [];
    for ($i = 0; $i < $n; $i += $step) {
        $r = $records[$i];
        $lat = semicircles_to_deg($r[0] ?? null);
        $lon = semicircles_to_deg($r[1] ?? null);
        $hr  = $r[3] ?? null;
        $alt = isset($r[78]) ? ($r[78] / 5) - 500 : (isset($r[2]) ? ($r[2] / 5) - 500 : null);
        $t   = garmin_to_unix($r[253] ?? null);
        if ($lat === null && $hr === null) continue;
        $track[] = [
            $lat !== null ? round($lat, 6) : null,
            $lon !== null ? round($lon, 6) : null,
            $hr,
            $alt !== null ? round($alt, 1) : null,
            $t,
        ];
    }
    $out['track']       = $track;
    $out['track_total'] = $n;
    return $out;
}

function parse_scorecard(array $by): ?array {
    if (!isset($by[190]) && !isset($by[192])) return null;

    $c = $by[190][0] ?? [];
    $course = [
        'id'         => $c[0] ?? null,
        'name'       => $c[1] ?? null,
        'start_unix' => garmin_to_unix($c[3] ?? null),
        'front_par'  => $c[8] ?? null,
        'back_par'   => $c[9] ?? null,
        'total_par'  => $c[10] ?? null,
        'tee'        => $c[11] ?? null,
        'slope'      => $c[12] ?? null,
        'rating'     => $c[21] ?? null,
        'length'     => $c[14] ?? null,
    ];

    // hole definitions
    $defs = [];
    foreach ($by[193] ?? [] as $h) {
        $num = $h[0] ?? null;
        if ($num === null) continue;
        $defs[$num] = [
            'par'      => $h[2] ?? null,
            'length_yd'=> isset($h[1]) ? round(($h[1] / 100) * METERS_TO_YARDS) : null,
            'si'       => $h[3] ?? null,
            'pin_lat'  => semicircles_to_deg($h[4] ?? null),
            'pin_lon'  => semicircles_to_deg($h[5] ?? null),
        ];
    }

    // hole results — auto-detect the putts field (device-dependent: 3 or 5)
    $results = $by[192] ?? [];
    $summary = $by[191][0] ?? [];
    $totalPutts = $summary[5] ?? null;
    $puttField = detect_putts_field($results, $totalPutts);

    $holes = [];
    foreach ($results as $h) {
        $num = $h[1] ?? null;
        if ($num === null) continue;
        $score = $h[2] ?? null;
        $putts = $puttField !== null ? ($h[$puttField] ?? null) : null;
        if ($putts !== null && $putts < 0) $putts = null;
        $d = $defs[$num] ?? [];
        $holes[$num] = [
            'number'    => $num,
            'par'       => $d['par'] ?? null,
            'length_yd' => $d['length_yd'] ?? null,
            'si'        => $d['si'] ?? null,
            'score'     => $score,
            'putts'     => $putts,
            'pin_lat'   => $d['pin_lat'] ?? null,
            'pin_lon'   => $d['pin_lon'] ?? null,
        ];
    }
    // include holes that have a def but no result
    foreach ($defs as $num => $d) {
        if (!isset($holes[$num])) {
            $holes[$num] = [
                'number' => $num, 'par' => $d['par'], 'length_yd' => $d['length_yd'],
                'si' => $d['si'], 'score' => null, 'putts' => null,
                'pin_lat' => $d['pin_lat'], 'pin_lon' => $d['pin_lon'],
            ];
        }
    }
    ksort($holes);

    // shots (msg 194)
    $shots = [];
    foreach ($by[194] ?? [] as $sh) {
        $slat = semicircles_to_deg($sh[2] ?? null);
        $slon = semicircles_to_deg($sh[3] ?? null);
        $elat = semicircles_to_deg($sh[4] ?? null);
        $elon = semicircles_to_deg($sh[5] ?? null);
        if ($slat === null && $elat === null) continue;
        $shots[] = [
            'hole' => $sh[1] ?? null,
            'slat' => $slat !== null ? round($slat, 6) : null,
            'slon' => $slon !== null ? round($slon, 6) : null,
            'elat' => $elat !== null ? round($elat, 6) : null,
            'elon' => $elon !== null ? round($elon, 6) : null,
        ];
    }

    // totals — prefer the device summary, fall back to computed sums
    $sumScore = 0; $sumPar = 0; $sumPutts = 0; $haveScore = false; $havePutts = false;
    foreach ($holes as $h) {
        if ($h['par']   !== null) $sumPar   += $h['par'];
        if ($h['score'] !== null) { $sumScore += $h['score']; $haveScore = true; }
        if ($h['putts'] !== null) { $sumPutts += $h['putts']; $havePutts = true; }
    }
    $totals = [
        'par'         => $course['total_par'] ?? ($sumPar ?: null),
        'front_score' => $summary[2] ?? null,
        'back_score'  => $summary[3] ?? null,
        'score'       => $summary[4] ?? ($haveScore ? $sumScore : null),
        'putts'       => $summary[5] ?? ($havePutts ? $sumPutts : null),
        'holes_played'=> count(array_filter($holes, fn($h) => $h['score'] !== null)),
    ];

    return [
        'course'  => $course,
        'holes'   => array_values($holes),
        'shots'   => $shots,
        'player'  => $summary[0] ?? null,
        'totals'  => $totals,
    ];
}

/** Pick the 192 field whose per-hole sum matches the summary putt total. */
function detect_putts_field(array $results, ?int $totalPutts): ?int {
    $candidates = [3, 5];
    $sums = [];
    foreach ($candidates as $f) {
        $sum = 0; $any = false;
        foreach ($results as $h) {
            $v = $h[$f] ?? null;
            if ($v !== null && $v >= 0) { $sum += $v; $any = true; }
        }
        $sums[$f] = $any ? $sum : null;
    }
    if ($totalPutts !== null) {
        foreach ($candidates as $f) {
            if ($sums[$f] === $totalPutts) return $f;
        }
    }
    // else prefer the field that looks like putts (all small, non-negative)
    foreach ($candidates as $f) {
        if ($sums[$f] !== null && $sums[$f] > 0) return $f;
    }
    return null;
}

/* -------------------------------------------------------------- storage */

function golf_rounds_dir(): string { return GOLF_DATA_DIR . '/rounds'; }

function golf_storage_writable(): bool {
    $dir = golf_rounds_dir();
    if (is_dir($dir)) return is_writable($dir);
    // try to create it
    if (@mkdir($dir, 0775, true)) return true;
    return is_dir(GOLF_DATA_DIR) && is_writable(GOLF_DATA_DIR);
}

function source_id(array $rec): string {
    $seed = ($rec['time_created'] ?? 0) . '|' . ($rec['kind'] ?? '') . '|' . ($rec['filename'] ?? '');
    return substr(hash('sha256', $seed), 0, 16);
}

/** Persist a parsed source; returns its id. Falls back to session if disk RO. */
function save_source(array $rec): string {
    $id = source_id($rec);
    $rec['source_id']   = $id;
    $rec['uploaded_at'] = time();

    if (golf_storage_writable()) {
        @mkdir(golf_rounds_dir(), 0775, true);
        $path = golf_rounds_dir() . '/' . $id . '.json';
        file_put_contents($path, json_encode($rec), LOCK_EX);
    } else {
        $_SESSION['golf_ephemeral'][$id] = $rec;
    }
    return $id;
}

function load_sources(): array {
    $out = [];
    foreach (glob(golf_rounds_dir() . '/*.json') ?: [] as $f) {
        $d = json_decode(file_get_contents($f), true);
        if (is_array($d) && isset($d['source_id'])) $out[$d['source_id']] = $d;
    }
    foreach ($_SESSION['golf_ephemeral'] ?? [] as $id => $d) {
        $out[$id] ??= $d;
    }
    return $out;
}

function load_source(string $id): ?array {
    $id = preg_replace('/[^a-f0-9]/', '', $id);
    $path = golf_rounds_dir() . '/' . $id . '.json';
    if (is_file($path)) {
        $d = json_decode(file_get_contents($path), true);
        if (is_array($d)) return $d;
    }
    return $_SESSION['golf_ephemeral'][$id] ?? null;
}

function delete_source(string $id): bool {
    $id = preg_replace('/[^a-f0-9]/', '', $id);
    $path = golf_rounds_dir() . '/' . $id . '.json';
    $ok = false;
    if (is_file($path)) $ok = @unlink($path);
    if (isset($_SESSION['golf_ephemeral'][$id])) { unset($_SESSION['golf_ephemeral'][$id]); $ok = true; }
    return $ok;
}

/* --------------------------------------------------- rounds & aggregates */

/** Group sources into rounds keyed by local date; merge activity + scorecard. */
function build_rounds(array $sources): array {
    $rounds = [];
    foreach ($sources as $src) {
        $key = $src['round_date'] ?? 'unknown';
        if (!isset($rounds[$key])) {
            $rounds[$key] = [
                'date' => $key, 'start_unix' => $src['start_unix'] ?? null,
                'tz_offset' => $src['tz_offset'] ?? 0,
                'activity' => null, 'scorecard' => null,
                'sources' => [], 'source_ids' => [],
            ];
        }
        $r =& $rounds[$key];
        $r['sources'][]    = $src['kind'];
        $r['source_ids'][$src['kind']] = $src['source_id'] ?? source_id($src);
        if (!empty($src['activity']))  $r['activity']  = $src['activity'];
        if (!empty($src['scorecard'])) $r['scorecard'] = $src['scorecard'];
        if (($src['start_unix'] ?? null) && !$r['start_unix']) $r['start_unix'] = $src['start_unix'];
        // prefer a reliable (activity-derived) timezone offset
        if (!empty($src['tz_reliable']) || !isset($r['tz_set'])) {
            $r['tz_offset'] = $src['tz_offset'] ?? 0;
            if (!empty($src['tz_reliable'])) $r['tz_set'] = true;
        }
        unset($r);
    }
    // newest first
    uasort($rounds, fn($a, $b) => ($b['start_unix'] ?? 0) <=> ($a['start_unix'] ?? 0));
    return $rounds;
}

/** Aggregate scoring/fitness stats across rounds. */
function aggregate_stats(array $rounds): array {
    $scored = [];
    $totalDist = 0; $activityRounds = 0;
    foreach ($rounds as $r) {
        if (!empty($r['scorecard']['totals']['score']) && !empty($r['scorecard']['totals']['par'])) {
            $t = $r['scorecard']['totals'];
            $scored[] = [
                'date'  => $r['date'],
                'score' => $t['score'],
                'par'   => $t['par'],
                'diff'  => $t['score'] - $t['par'],
                'putts' => $t['putts'] ?? null,
            ];
        }
        if (!empty($r['activity']['distance_m'])) { $totalDist += $r['activity']['distance_m']; $activityRounds++; }
    }
    $stats = [
        'rounds'          => count($rounds),
        'scored_rounds'   => count($scored),
        'activity_rounds' => $activityRounds,
        'total_distance_m'=> $totalDist,
    ];
    if ($scored) {
        $diffs  = array_column($scored, 'diff');
        $scores = array_column($scored, 'score');
        $putts  = array_filter(array_column($scored, 'putts'), fn($p) => $p !== null);
        $best = null;
        foreach ($scored as $s) if ($best === null || $s['diff'] < $best['diff']) $best = $s;
        $stats += [
            'avg_score'   => array_sum($scores) / count($scores),
            'avg_to_par'  => array_sum($diffs) / count($diffs),
            'best_diff'   => $best['diff'],
            'best_score'  => $best['score'],
            'best_date'   => $best['date'],
            'avg_putts'   => $putts ? array_sum($putts) / count($putts) : null,
            'history'     => array_reverse($scored), // oldest→newest for trend
        ];
    }
    return $stats;
}

/** Scoring class for a hole (relative to par), used for CSS badges. */
function score_class(?int $score, ?int $par): string {
    if ($score === null || $par === null) return 'none';
    $d = $score - $par;
    if ($score === 1)       return 'ace';
    if ($d <= -2)           return 'eagle';
    if ($d === -1)          return 'birdie';
    if ($d === 0)           return 'par';
    if ($d === 1)           return 'bogey';
    if ($d === 2)           return 'dbogey';
    return 'worse';
}

function score_label(?int $score, ?int $par): string {
    if ($score === null || $par === null) return '';
    $d = $score - $par;
    if ($score === 1)  return 'Hole-in-one';
    return match (true) {
        $d <= -3 => 'Albatross',
        $d === -2 => 'Eagle',
        $d === -1 => 'Birdie',
        $d === 0  => 'Par',
        $d === 1  => 'Bogey',
        $d === 2  => 'Double bogey',
        default   => ($d) . ' over',
    };
}

/* ------------------------------------------------------------------ csrf */

function golf_csrf_token(): string {
    if (empty($_SESSION['golf_csrf'])) {
        $_SESSION['golf_csrf'] = bin2hex(random_bytes(32));
    }
    return $_SESSION['golf_csrf'];
}

function golf_csrf_check(?string $token): bool {
    return !empty($_SESSION['golf_csrf']) && is_string($token)
        && hash_equals($_SESSION['golf_csrf'], $token);
}

function h(?string $s): string { return htmlspecialchars((string)$s, ENT_QUOTES, 'UTF-8'); }
