<?php
/** Delete all source files belonging to a round (by local date). */
require __DIR__ . '/golf.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST' || !golf_csrf_check($_POST['csrf'] ?? null)) {
    header('Location: index.php');
    exit;
}

$date = $_POST['date'] ?? '';
$n = 0;
foreach (load_sources() as $id => $src) {
    if (($src['round_date'] ?? null) === $date) {
        if (delete_source($id)) $n++;
    }
}
$_SESSION['golf_flash'] = ['type' => 'ok', 'msg' => $n ? 'Round deleted.' : 'Nothing to delete.'];
header('Location: index.php');
