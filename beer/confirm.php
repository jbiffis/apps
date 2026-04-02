<?php
require __DIR__ . '/beers.php';

// Handle final save
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['action']) && $_POST['action'] === 'save') {
    if (!verify_csrf_token($_POST['csrf_token'] ?? '')) {
        http_response_code(403);
        die('Invalid CSRF token. Please go back and try again.');
    }

    $name = trim($_POST['name'] ?? '');
    $items = json_decode($_POST['items'] ?? '[]', true);

    $nameError = validate_name($name);
    if ($nameError || !is_array($items) || empty($items)) {
        header('Location: index.php');
        exit;
    }

    $orders = load_orders();
    $existingIndex = find_order_index($orders, $name);
    if ($existingIndex !== false) {
        array_splice($orders, $existingIndex, 1);
    }

    $orders[] = [
        'name' => $name,
        'items' => $items,
        'submitted' => date('Y-m-d H:i:s'),
    ];

    save_orders($orders);

    // Regenerate CSRF token after successful save
    unset($_SESSION['csrf_token']);

    header('Location: confirm.php?saved=1&name=' . urlencode($name));
    exit;
}

// Handle success page
if (isset($_GET['saved'])) {
    $savedName = htmlspecialchars($_GET['name'] ?? '');
    ?>
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Order Saved</title>
        <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f5f5f5; padding: 20px; display: flex; justify-content: center; align-items: center; min-height: 80vh; }
            .card { background: #fff; padding: 40px; border-radius: 12px; text-align: center; box-shadow: 0 2px 8px rgba(0,0,0,0.1); max-width: 500px; }
            .check { font-size: 3em; margin-bottom: 15px; }
            h1 { color: #27ae60; margin-bottom: 10px; }
            p { color: #666; margin-bottom: 20px; }
            a { display: inline-block; background: #3498db; color: #fff; padding: 12px 30px; border-radius: 6px; text-decoration: none; font-weight: 600; }
            a:hover { background: #2980b9; }
        </style>
    </head>
    <body>
        <div class="card">
            <div class="check">✅</div>
            <h1>Order Saved!</h1>
            <p>Thanks <?= $savedName ?>! Your order has been submitted.</p>
            <a href="index.php">Back to Order Form</a>
        </div>
    </body>
    </html>
    <?php
    exit;
}

// Show confirmation page
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Location: index.php');
    exit;
}

if (!verify_csrf_token($_POST['csrf_token'] ?? '')) {
    http_response_code(403);
    die('Invalid CSRF token. Please go back and try again.');
}

$name = trim($_POST['name'] ?? '');
$quantities = $_POST['qty'] ?? [];

$nameError = validate_name($name);
if ($nameError) {
    header('Location: index.php');
    exit;
}

// Build order items (only beers with qty > 0)
$orderItems = [];
$grandTotal = 0;
foreach ($beers as $beer) {
    $qty = intval($quantities[$beer['id']] ?? 0);
    if ($qty < 0) $qty = 0;
    if ($qty > 99) $qty = 99;
    if ($qty > 0) {
        $lineTotal = round($qty * $beer['total'], 2);
        $orderItems[] = [
            'beer_id' => $beer['id'],
            'name' => $beer['name'],
            'format' => $beer['format'],
            'price' => $beer['price'],
            'tax' => $beer['tax'],
            'deposit' => $beer['deposit'],
            'unit_total' => $beer['total'],
            'qty' => $qty,
            'line_total' => $lineTotal,
        ];
        $grandTotal += $lineTotal;
    }
}

if (empty($orderItems)) {
    header('Location: index.php');
    exit;
}

// Check for existing order
$orders = load_orders();
$existingIndex = find_order_index($orders, $name);
$hasExisting = $existingIndex !== false;
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Confirm Order</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f5f5f5; color: #333; padding: 20px; }
        .container { max-width: 800px; margin: 0 auto; }
        h1 { text-align: center; margin-bottom: 20px; }
        .warning { background: #fff3cd; border: 2px solid #ffc107; border-radius: 8px; padding: 15px 20px; margin-bottom: 20px; }
        .warning strong { color: #856404; }
        table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 20px; }
        th { background: #2c3e50; color: #fff; padding: 12px 10px; text-align: left; font-size: 0.85em; }
        th.right, td.right { text-align: right; }
        td { padding: 10px; border-bottom: 1px solid #eee; }
        .total-row td { font-weight: 700; font-size: 1.1em; background: #eafaf1; }
        .name-display { background: #fff; padding: 15px 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        .actions { display: flex; gap: 15px; justify-content: center; margin-top: 20px; }
        .btn { padding: 14px 35px; font-size: 1em; border-radius: 8px; border: none; cursor: pointer; font-weight: 600; text-decoration: none; display: inline-block; }
        .btn-confirm { background: #27ae60; color: #fff; }
        .btn-confirm:hover { background: #219a52; }
        .btn-back { background: #95a5a6; color: #fff; }
        .btn-back:hover { background: #7f8c8d; }
    </style>
</head>
<body>
<div class="container">
    <h1>Confirm Your Order</h1>

    <?php if ($hasExisting): ?>
    <div class="warning">
        <strong>⚠️ You already have an order on file, <?= htmlspecialchars($name) ?>.</strong><br>
        Submitting this order will <strong>replace</strong> your previous order. Continue?
    </div>
    <?php endif; ?>

    <div class="name-display">
        <strong>Name:</strong> <?= htmlspecialchars($name) ?>
    </div>

    <table>
        <thead>
            <tr>
                <th>Beer</th>
                <th>Format</th>
                <th class="right">Unit Total</th>
                <th class="right">Qty</th>
                <th class="right">Line Total</th>
            </tr>
        </thead>
        <tbody>
            <?php foreach ($orderItems as $item): ?>
            <tr>
                <td><?= htmlspecialchars($item['name']) ?></td>
                <td><?= $item['format'] ?></td>
                <td class="right">$<?= number_format($item['unit_total'], 2) ?></td>
                <td class="right"><?= $item['qty'] ?></td>
                <td class="right">$<?= number_format($item['line_total'], 2) ?></td>
            </tr>
            <?php endforeach; ?>
            <tr class="total-row">
                <td colspan="4">Order Total</td>
                <td class="right">$<?= number_format($grandTotal, 2) ?></td>
            </tr>
        </tbody>
    </table>

    <div class="actions">
        <a href="index.php" class="btn btn-back">← Go Back</a>
        <form method="POST" action="confirm.php" style="display:inline;">
            <input type="hidden" name="action" value="save">
            <input type="hidden" name="csrf_token" value="<?= htmlspecialchars(generate_csrf_token()) ?>">
            <input type="hidden" name="name" value="<?= htmlspecialchars($name) ?>">
            <input type="hidden" name="items" value="<?= htmlspecialchars(json_encode($orderItems)) ?>">
            <button type="submit" class="btn btn-confirm">
                <?= $hasExisting ? 'Replace & Submit Order' : 'Submit Order' ?>
            </button>
        </form>
    </div>
</div>
</body>
</html>
