<?php

require_once __DIR__ . '/shared.php';

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

if ($method === 'OPTIONS') {
    header('Allow: GET, POST, DELETE');
    exit;
}

if ($method === 'GET') {
    [$fp, $data] = lock_and_read();
    $voterUuid = $_GET['voter'] ?? '';
    $songs = array_map(
        fn($s) => shape_for_voter($s, $voterUuid),
        $data['songs'],
    );
    // Order: votes desc, then addedAt asc (older first within ties)
    usort($songs, function ($a, $b) {
        if ($a['votes'] !== $b['votes']) {
            return $b['votes'] <=> $a['votes'];
        }
        return $a['addedAt'] <=> $b['addedAt'];
    });
    unlock($fp);
    json_ok(['songs' => $songs]);
}

if ($method === 'POST') {
    require_admin();
    $body = read_json_body();
    $title = trim((string)($body['title'] ?? ''));
    $artist = trim((string)($body['artist'] ?? ''));
    if ($title === '' || $artist === '') {
        json_error(400, 'missing_fields', 'title and artist are required');
    }
    if (mb_strlen($title) > 200 || mb_strlen($artist) > 200) {
        json_error(400, 'too_long');
    }

    [$fp, $data] = lock_and_read();
    $key = normalize_key($title, $artist);
    foreach ($data['songs'] as $s) {
        if (normalize_key($s['title'], $s['artist']) === $key) {
            unlock($fp);
            json_error(409, 'duplicate', 'That song is already in the setlist.');
        }
    }
    $song = [
        'id' => song_id(),
        'title' => $title,
        'artist' => $artist,
        'addedAt' => (int)round(microtime(true) * 1000),
        'voters' => [],
    ];
    $data['songs'][] = $song;
    save_and_unlock($fp, $data);
    json_ok(['song' => shape_song($song)]);
}

if ($method === 'DELETE') {
    require_admin();
    $id = $_GET['id'] ?? '';
    if ($id === '') {
        json_error(400, 'missing_id');
    }
    [$fp, $data] = lock_and_read();
    $before = count($data['songs']);
    $data['songs'] = array_values(array_filter(
        $data['songs'],
        fn($s) => ($s['id'] ?? '') !== $id,
    ));
    if (count($data['songs']) === $before) {
        unlock($fp);
        json_error(404, 'not_found');
    }
    save_and_unlock($fp, $data);
    json_ok(['ok' => true]);
}

json_error(405, 'method_not_allowed');
