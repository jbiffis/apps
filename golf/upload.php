<?php
/** Handle .FIT upload(s): validate, parse, persist, then redirect back. */
require __DIR__ . '/golf.php';

function flash(string $type, string $msg): void {
    $_SESSION['golf_flash'] = ['type' => $type, 'msg' => $msg];
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Location: index.php');
    exit;
}
if (!golf_csrf_check($_POST['csrf'] ?? null)) {
    flash('err', 'Security token expired — please try again.');
    header('Location: index.php');
    exit;
}
if (empty($_FILES['fit']) || !is_array($_FILES['fit']['name'])) {
    flash('err', 'No file selected.');
    header('Location: index.php');
    exit;
}

$names = $_FILES['fit']['name'];
$tmps  = $_FILES['fit']['tmp_name'];
$errs  = $_FILES['fit']['error'];
$sizes = $_FILES['fit']['size'];

$ok = 0; $fail = 0; $msgs = []; $lastDate = null;

for ($i = 0; $i < count($names); $i++) {
    $name = $names[$i] ?: "file$i";
    if ($errs[$i] !== UPLOAD_ERR_OK) { $fail++; $msgs[] = h($name) . ': upload error'; continue; }
    if ($sizes[$i] > GOLF_MAX_UPLOAD) { $fail++; $msgs[] = h($name) . ': too large'; continue; }
    if (!is_uploaded_file($tmps[$i])) { $fail++; continue; }

    $bytes = file_get_contents($tmps[$i]);
    // quick signature gate: ".FIT" at header bytes 8..11
    if (strlen($bytes) < 14 || substr($bytes, 8, 4) !== '.FIT') {
        $fail++; $msgs[] = h($name) . ': not a FIT file'; continue;
    }
    try {
        $rec = parse_round($bytes, basename($name));
        if ($rec['kind'] === 'other') {
            $fail++; $msgs[] = h($name) . ': no golf or activity data found'; continue;
        }
        save_source($rec);
        $ok++; $lastDate = $rec['round_date'];
    } catch (FitException $e) {
        $fail++; $msgs[] = h($name) . ': ' . h($e->getMessage());
    } catch (Throwable $e) {
        $fail++; $msgs[] = h($name) . ': could not parse';
    }
}

$storageNote = golf_storage_writable() ? '' :
    ' (Note: server storage is read-only, so this round is kept only for your current session.)';

if ($ok && !$fail) {
    flash('ok', "Imported $ok file" . ($ok > 1 ? 's' : '') . '.' . $storageNote);
} elseif ($ok && $fail) {
    flash('ok', "Imported $ok, skipped $fail — " . implode('; ', $msgs) . $storageNote);
} else {
    flash('err', 'Nothing imported. ' . implode('; ', $msgs));
}

// go straight to the round if a single one landed
if ($ok === 1 && $lastDate) {
    header('Location: round.php?date=' . urlencode($lastDate));
} else {
    header('Location: index.php');
}
