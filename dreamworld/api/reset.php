<?php

require_once __DIR__ . '/shared.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    json_error(405, 'method_not_allowed');
}

require_admin();

[$fp, $data] = lock_and_read();
foreach ($data['songs'] as &$s) {
    $s['voters'] = [];
}
unset($s);
save_and_unlock($fp, $data);

json_ok(['ok' => true, 'count' => count($data['songs'])]);
