<?php

/**
 * Dreamworld — shared helpers for the song-request JSON store.
 *
 * Single JSON file at DATA_FILE, protected by an exclusive flock during
 * any read-modify-write cycle. Songs are persistent; voters[] is what
 * resets between gigs.
 */

const DATA_FILE = '/var/www/data/dreamworld/songs.json';

// Never let PHP leak HTML warnings into the response body — clients parse
// these as JSON and the resulting "Unexpected token '<'" error hides the
// real problem.
ini_set('display_errors', '0');
ini_set('log_errors', '1');
error_reporting(E_ALL);

set_exception_handler(function ($e) {
    if (!headers_sent()) {
        http_response_code(500);
        header('Content-Type: application/json');
    }
    echo json_encode([
        'error' => 'server_exception',
        'message' => $e->getMessage(),
    ]);
    exit;
});

set_error_handler(function ($severity, $message, $file, $line) {
    if (!(error_reporting() & $severity)) return false;
    throw new ErrorException($message, 0, $severity, $file, $line);
});

function ensure_data_file(): void {
    $dir = dirname(DATA_FILE);
    if (!is_dir($dir)) {
        if (!@mkdir($dir, 0775, true) && !is_dir($dir)) {
            json_error(500, 'storage_mkdir_failed', "Cannot create $dir");
        }
    }
    if (!is_writable($dir)) {
        json_error(500, 'storage_not_writable', "$dir is not writable by " . get_current_user());
    }
    if (!file_exists(DATA_FILE)) {
        $ok = @file_put_contents(DATA_FILE, json_encode([
            'songs' => [],
            'createdAt' => time() * 1000,
        ], JSON_PRETTY_PRINT));
        if ($ok === false) {
            json_error(500, 'storage_init_failed', 'Could not create songs.json');
        }
        @chmod(DATA_FILE, 0664);
    }
}

/**
 * Open the data file with an exclusive lock and return [handle, data].
 * Caller must call save_and_unlock() or unlock() to release.
 */
function lock_and_read(): array {
    ensure_data_file();
    $fp = fopen(DATA_FILE, 'c+');
    if ($fp === false) {
        json_error(500, 'storage_open_failed');
    }
    if (!flock($fp, LOCK_EX)) {
        fclose($fp);
        json_error(500, 'storage_lock_failed');
    }
    $raw = stream_get_contents($fp);
    $data = $raw === '' ? null : json_decode($raw, true);
    if (!is_array($data)) {
        $data = ['songs' => [], 'createdAt' => time() * 1000];
    }
    if (!isset($data['songs']) || !is_array($data['songs'])) {
        $data['songs'] = [];
    }
    return [$fp, $data];
}

function save_and_unlock($fp, array $data): void {
    $json = json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);
    rewind($fp);
    ftruncate($fp, 0);
    fwrite($fp, $json);
    fflush($fp);
    flock($fp, LOCK_UN);
    fclose($fp);
}

function unlock($fp): void {
    flock($fp, LOCK_UN);
    fclose($fp);
}

function json_error(int $status, string $code, string $message = ''): void {
    http_response_code($status);
    header('Content-Type: application/json');
    echo json_encode(['error' => $code, 'message' => $message]);
    exit;
}

function json_ok($payload): void {
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($payload, JSON_UNESCAPED_UNICODE);
    exit;
}

function read_json_body(): array {
    $raw = file_get_contents('php://input');
    if ($raw === '' || $raw === false) return [];
    $body = json_decode($raw, true);
    return is_array($body) ? $body : [];
}

function require_admin(): void {
    $expected = getenv('ADMIN_PASSWORD');
    if (!$expected) {
        json_error(500, 'admin_not_configured');
    }
    $header = $_SERVER['HTTP_X_ADMIN_PASSWORD'] ?? '';
    if (!hash_equals($expected, $header)) {
        json_error(401, 'unauthorized');
    }
}

function normalize_key(string $title, string $artist): string {
    $t = preg_replace('/\s+/', ' ', strtolower(trim($title)));
    $a = preg_replace('/\s+/', ' ', strtolower(trim($artist)));
    return $t . '|' . $a;
}

function song_id(): string {
    return bin2hex(random_bytes(8));
}

function shape_song(array $s): array {
    return [
        'id' => $s['id'] ?? '',
        'title' => $s['title'] ?? '',
        'artist' => $s['artist'] ?? '',
        'addedAt' => $s['addedAt'] ?? 0,
        'votes' => isset($s['voters']) && is_array($s['voters']) ? count($s['voters']) : 0,
    ];
}

function shape_for_voter(array $s, string $voterUuid): array {
    $shaped = shape_song($s);
    $voters = isset($s['voters']) && is_array($s['voters']) ? $s['voters'] : [];
    $shaped['voted'] = $voterUuid !== '' && in_array($voterUuid, $voters, true);
    return $shaped;
}
