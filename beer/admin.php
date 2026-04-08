<?php
require __DIR__ . '/beers.php';

// Admin authentication
$adminPassword = getenv('ADMIN_PASSWORD');
if (!$adminPassword) {
    http_response_code(503);
    die('Admin access is not configured.');
}

if (!isset($_SESSION['admin_authenticated'])) {
    if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['password'])) {
        if (!verify_csrf_token($_POST['csrf_token'] ?? '')) {
            http_response_code(403);
            die('Invalid CSRF token.');
        }
        if (hash_equals($adminPassword, $_POST['password'])) {
            $_SESSION['admin_authenticated'] = true;
        } else {
            $loginError = 'Incorrect password.';
        }
    }

    if (!isset($_SESSION['admin_authenticated'])) {
        ?>
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Admin Login</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f5f5f5; padding: 20px; display: flex; justify-content: center; align-items: center; min-height: 80vh; }
                .card { background: #fff; padding: 40px; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); max-width: 400px; width: 100%; }
                h1 { text-align: center; margin-bottom: 20px; color: #2c3e50; }
                label { font-weight: 600; }
                input[type="password"] { width: 100%; padding: 10px; margin: 8px 0 15px; border: 2px solid #ddd; border-radius: 6px; font-size: 1em; }
                input[type="password"]:focus { outline: none; border-color: #3498db; }
                button { width: 100%; background: #2c3e50; color: #fff; border: none; padding: 12px; border-radius: 6px; font-size: 1em; font-weight: 600; cursor: pointer; }
                button:hover { background: #34495e; }
                .error { color: #e74c3c; margin-bottom: 15px; text-align: center; }
                .back-link { display: block; text-align: center; margin-top: 15px; color: #3498db; text-decoration: none; }
            </style>
        </head>
        <body>
            <div class="card">
                <h1>Admin Login</h1>
                <?php if (isset($loginError)): ?>
                    <p class="error"><?= htmlspecialchars($loginError) ?></p>
                <?php endif; ?>
                <form method="POST">
                    <input type="hidden" name="csrf_token" value="<?= htmlspecialchars(generate_csrf_token()) ?>">
                    <label for="password">Password</label>
                    <input type="password" id="password" name="password" required autofocus>
                    <button type="submit">Log In</button>
                </form>
                <a href="index.php" class="back-link">← Back to Order Form</a>
            </div>
        </body>
        </html>
        <?php
        exit;
    }
}

// Handle logout
if (isset($_GET['logout'])) {
    unset($_SESSION['admin_authenticated']);
    header('Location: admin.php');
    exit;
}

// Handle POST actions
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['action'])) {
    if (!verify_csrf_token($_POST['csrf_token'] ?? '')) {
        http_response_code(403);
        die('Invalid CSRF token.');
    }

    $orders = load_orders();

    switch ($_POST['action']) {
        case 'delete':
            $deleteIndex = intval($_POST['order_index'] ?? -1);
            if (isset($orders[$deleteIndex])) {
                array_splice($orders, $deleteIndex, 1);
                save_orders($orders);
            }
            break;

        case 'toggle_paid':
            $toggleIndex = intval($_POST['order_index'] ?? -1);
            if (isset($orders[$toggleIndex])) {
                $orders[$toggleIndex]['paid'] = !($orders[$toggleIndex]['paid'] ?? false);
                save_orders($orders);
            }
            break;

        case 'reset_all':
            save_orders([]);
            break;
    }

    header('Location: admin.php');
    exit;
}

$orders = load_orders();

// Build summary: total qty of each beer across all orders
$summary = [];
foreach ($beers as $beer) {
    $key = $beer['id'];
    $summary[$key] = [
        'name' => $beer['name'],
        'format' => $beer['format'],
        'total_qty' => 0,
        'unit_total' => $beer['total'],
    ];
}
foreach ($orders as $order) {
    foreach ($order['items'] as $item) {
        $id = $item['beer_id'];
        if (isset($summary[$id])) {
            $summary[$id]['total_qty'] += $item['qty'];
        }
    }
}

// Filter summary to only beers that have been ordered
$orderedSummary = array_filter($summary, fn($s) => $s['total_qty'] > 0);

// Calculate paid/unpaid stats
$totalRevenue = 0;
$totalUnits = 0;
$paidTotal = 0;
$unpaidTotal = 0;
$paidCount = 0;
$unpaidCount = 0;
foreach ($orders as $order) {
    $orderSum = 0;
    foreach ($order['items'] as $item) {
        $orderSum += $item['line_total'];
        $totalUnits += $item['qty'];
    }
    $totalRevenue += $orderSum;
    if ($order['paid'] ?? false) {
        $paidTotal += $orderSum;
        $paidCount++;
    } else {
        $unpaidTotal += $orderSum;
        $unpaidCount++;
    }
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Admin — All Orders</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f5f5f5; color: #333; padding: 20px; }
        .container { max-width: 1000px; margin: 0 auto; }
        h1 { margin-bottom: 5px; }
        .subtitle { color: #666; margin-bottom: 25px; }
        h2 { margin-top: 30px; margin-bottom: 10px; color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 5px; }
        h3 { margin-top: 20px; margin-bottom: 8px; color: #34495e; }
        table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 20px; }
        th { background: #2c3e50; color: #fff; padding: 10px 8px; text-align: left; font-size: 0.85em; }
        th.right, td.right { text-align: right; }
        td { padding: 8px; border-bottom: 1px solid #eee; font-size: 0.9em; }
        .total-row td { font-weight: 700; background: #eafaf1; }
        .summary-row td { font-size: 0.95em; }
        .order-meta { color: #888; font-size: 0.85em; font-weight: normal; }
        .no-orders { text-align: center; padding: 40px; color: #999; font-size: 1.2em; }
        .top-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
        .back-link { color: #3498db; text-decoration: none; font-weight: 600; }
        .back-link:hover { text-decoration: underline; }
        .logout-link { color: #e74c3c; text-decoration: none; font-weight: 600; font-size: 0.9em; }
        .logout-link:hover { text-decoration: underline; }
        .grand-summary { background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 20px; }
        .stat { display: inline-block; margin-right: 30px; margin-bottom: 8px; }
        .stat-num { font-size: 1.5em; font-weight: 700; color: #27ae60; }
        .stat-num.paid { color: #27ae60; }
        .stat-num.unpaid { color: #e74c3c; }
        .stat-label { font-size: 0.85em; color: #666; }
        .order-header { display: flex; justify-content: space-between; align-items: center; margin-top: 20px; margin-bottom: 8px; }
        .order-header h3 { margin: 0; }
        .order-actions { display: flex; gap: 8px; align-items: center; }
        .btn-delete { background: #e74c3c; color: #fff; border: none; padding: 6px 14px; border-radius: 4px; cursor: pointer; font-size: 0.85em; font-weight: 600; }
        .btn-delete:hover { background: #c0392b; }
        .btn-paid { background: #27ae60; color: #fff; border: none; padding: 6px 14px; border-radius: 4px; cursor: pointer; font-size: 0.85em; font-weight: 600; }
        .btn-paid:hover { background: #219a52; }
        .btn-unpaid { background: #f39c12; color: #fff; border: none; padding: 6px 14px; border-radius: 4px; cursor: pointer; font-size: 0.85em; font-weight: 600; }
        .btn-unpaid:hover { background: #d68910; }
        .paid-badge { display: inline-block; background: #27ae60; color: #fff; font-size: 0.7em; padding: 2px 8px; border-radius: 3px; vertical-align: middle; margin-left: 8px; }
        .unpaid-badge { display: inline-block; background: #e74c3c; color: #fff; font-size: 0.7em; padding: 2px 8px; border-radius: 3px; vertical-align: middle; margin-left: 8px; }
        .btn-reset { background: #e74c3c; color: #fff; border: none; padding: 10px 25px; border-radius: 6px; cursor: pointer; font-size: 0.95em; font-weight: 600; }
        .btn-reset:hover { background: #c0392b; }
        .reset-section { margin-top: 40px; padding-top: 20px; border-top: 2px solid #e74c3c; text-align: center; }
        .reset-section p { color: #666; margin-bottom: 15px; }
    </style>
</head>
<body>
<div class="container">
    <div class="top-bar">
        <a href="index.php" class="back-link">← Back to Order Form</a>
        <a href="admin.php?logout=1" class="logout-link">Logout</a>
    </div>
    <h1>Admin — All Orders</h1>
    <p class="subtitle"><?= count($orders) ?> order(s) submitted</p>

    <?php if (empty($orders)): ?>
        <div class="no-orders">No orders yet.</div>
    <?php else: ?>

        <!-- Grand summary stats -->
        <div class="grand-summary">
            <span class="stat">
                <span class="stat-num"><?= count($orders) ?></span>
                <span class="stat-label">Orders</span>
            </span>
            <span class="stat">
                <span class="stat-num"><?= $totalUnits ?></span>
                <span class="stat-label">Total Cases/Packs</span>
            </span>
            <span class="stat">
                <span class="stat-num">$<?= number_format($totalRevenue, 2) ?></span>
                <span class="stat-label">Total Value</span>
            </span>
            <span class="stat">
                <span class="stat-num paid">$<?= number_format($paidTotal, 2) ?></span>
                <span class="stat-label">Paid (<?= $paidCount ?>)</span>
            </span>
            <span class="stat">
                <span class="stat-num unpaid">$<?= number_format($unpaidTotal, 2) ?></span>
                <span class="stat-label">Unpaid (<?= $unpaidCount ?>)</span>
            </span>
        </div>

        <!-- Beer summary -->
        <h2>Beer Summary</h2>
        <table>
            <thead>
                <tr>
                    <th>Beer</th>
                    <th>Format</th>
                    <th class="right">Qty Ordered</th>
                    <th class="right">Subtotal</th>
                </tr>
            </thead>
            <tbody>
                <?php
                $summaryTotal = 0;
                foreach ($orderedSummary as $s):
                    $subtotal = $s['total_qty'] * $s['unit_total'];
                    $summaryTotal += $subtotal;
                ?>
                <tr class="summary-row">
                    <td><?= htmlspecialchars($s['name']) ?></td>
                    <td><?= $s['format'] ?></td>
                    <td class="right"><?= $s['total_qty'] ?></td>
                    <td class="right">$<?= number_format($subtotal, 2) ?></td>
                </tr>
                <?php endforeach; ?>
                <tr class="total-row">
                    <td colspan="3">Total</td>
                    <td class="right">$<?= number_format($summaryTotal, 2) ?></td>
                </tr>
            </tbody>
        </table>

        <!-- Individual orders -->
        <h2>Orders</h2>
        <?php foreach ($orders as $orderIndex => $order):
            $isPaid = $order['paid'] ?? false;
        ?>
            <div class="order-header">
                <h3>
                    <?= htmlspecialchars($order['name']) ?>
                    <span class="order-meta">— <?= htmlspecialchars($order['submitted']) ?></span>
                    <?php if ($isPaid): ?>
                        <span class="paid-badge">PAID</span>
                    <?php else: ?>
                        <span class="unpaid-badge">UNPAID</span>
                    <?php endif; ?>
                </h3>
                <div class="order-actions">
                    <form method="POST" action="admin.php" style="display:inline;">
                        <input type="hidden" name="action" value="toggle_paid">
                        <input type="hidden" name="csrf_token" value="<?= htmlspecialchars(generate_csrf_token()) ?>">
                        <input type="hidden" name="order_index" value="<?= $orderIndex ?>">
                        <button type="submit" class="<?= $isPaid ? 'btn-unpaid' : 'btn-paid' ?>">
                            <?= $isPaid ? 'Mark Unpaid' : 'Mark Paid' ?>
                        </button>
                    </form>
                    <form method="POST" action="admin.php" style="display:inline;" onsubmit="return confirm('Delete order for <?= htmlspecialchars(addslashes($order['name'])) ?>?');">
                        <input type="hidden" name="action" value="delete">
                        <input type="hidden" name="csrf_token" value="<?= htmlspecialchars(generate_csrf_token()) ?>">
                        <input type="hidden" name="order_index" value="<?= $orderIndex ?>">
                        <button type="submit" class="btn-delete">Delete</button>
                    </form>
                </div>
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
                    <?php
                    $orderTotal = 0;
                    foreach ($order['items'] as $item):
                        $orderTotal += $item['line_total'];
                    ?>
                    <tr>
                        <td><?= htmlspecialchars($item['name']) ?></td>
                        <td><?= htmlspecialchars($item['format']) ?></td>
                        <td class="right">$<?= number_format($item['unit_total'], 2) ?></td>
                        <td class="right"><?= $item['qty'] ?></td>
                        <td class="right">$<?= number_format($item['line_total'], 2) ?></td>
                    </tr>
                    <?php endforeach; ?>
                    <tr class="total-row">
                        <td colspan="4">Order Total</td>
                        <td class="right">$<?= number_format($orderTotal, 2) ?></td>
                    </tr>
                </tbody>
            </table>
        <?php endforeach; ?>

        <!-- Reset all orders -->
        <div class="reset-section">
            <p>Start a new ordering round by clearing all existing orders.</p>
            <form method="POST" action="admin.php" onsubmit="return confirm('This will permanently delete ALL orders. Are you sure?');">
                <input type="hidden" name="action" value="reset_all">
                <input type="hidden" name="csrf_token" value="<?= htmlspecialchars(generate_csrf_token()) ?>">
                <button type="submit" class="btn-reset">Reset All Orders</button>
            </form>
        </div>

    <?php endif; ?>
</div>
</body>
</html>
