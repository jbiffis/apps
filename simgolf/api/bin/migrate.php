<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

$db = require __DIR__ . '/../src/db.php';
$db->beginTransaction();
try {
    // Migrations are idempotent and safe for existing and newly initialized DBs.
    foreach (glob(__DIR__ . '/../migrations/*.sql') as $migration) {
        $db->exec(file_get_contents($migration));
    }
    $db->commit();
    echo "Database migrations applied. Existing season results were preserved.\n";
} catch (Throwable $error) {
    $db->rollBack();
    throw $error;
}
