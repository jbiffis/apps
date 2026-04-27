<?php

require_once __DIR__ . '/shared.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    json_error(405, 'method_not_allowed');
}

$body = read_json_body();
$songId = (string)($body['songId'] ?? '');
$voter = trim((string)($body['voter'] ?? ''));

if ($songId === '' || $voter === '') {
    json_error(400, 'missing_fields');
}
if (strlen($voter) > 64 || !preg_match('/^[a-zA-Z0-9_-]+$/', $voter)) {
    json_error(400, 'invalid_voter');
}

[$fp, $data] = lock_and_read();

$found = false;
foreach ($data['songs'] as &$s) {
    if (($s['id'] ?? '') !== $songId) continue;
    $found = true;
    if (!isset($s['voters']) || !is_array($s['voters'])) {
        $s['voters'] = [];
    }
    $pos = array_search($voter, $s['voters'], true);
    if ($pos === false) {
        $s['voters'][] = $voter;
    } else {
        array_splice($s['voters'], $pos, 1);
    }
    save_and_unlock($fp, $data);
    json_ok(['song' => shape_for_voter($s, $voter)]);
}
unset($s);

if (!$found) {
    unlock($fp);
    json_error(404, 'not_found');
}
