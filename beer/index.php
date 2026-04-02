<?php require __DIR__ . '/beers.php'; ?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Easter Beer Order</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f5f5f5; color: #333; padding: 20px; }
        .container { max-width: 1000px; margin: 0 auto; }
        h1 { text-align: center; margin-bottom: 5px; color: #c0392b; }
        .subtitle { text-align: center; color: #666; margin-bottom: 20px; }
        .name-section { background: #fff; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        .name-section label { font-weight: 600; font-size: 1.1em; }
        .name-section input { width: 100%; max-width: 400px; padding: 10px; margin-top: 8px; border: 2px solid #ddd; border-radius: 6px; font-size: 1em; }
        .name-section input:focus { outline: none; border-color: #3498db; }
        table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        th { background: #2c3e50; color: #fff; padding: 12px 8px; text-align: left; font-size: 0.85em; }
        th.right, td.right { text-align: right; }
        td { padding: 10px 8px; border-bottom: 1px solid #eee; font-size: 0.9em; }
        tr:hover { background: #f9f9f9; }
        tr.has-qty { background: #eafaf1; }
        tr.has-qty:hover { background: #d5f5e3; }
        .qty-input { width: 60px; padding: 6px; text-align: center; border: 2px solid #ddd; border-radius: 4px; font-size: 1em; }
        .qty-input:focus { outline: none; border-color: #3498db; }
        .row-total { font-weight: 600; }
        .format-label { color: #666; font-size: 0.85em; }
        .order-total { background: #fff; padding: 20px; border-radius: 8px; margin-top: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); display: flex; justify-content: space-between; align-items: center; }
        .order-total .total-amount { font-size: 1.8em; font-weight: 700; color: #27ae60; }
        .submit-btn { background: #27ae60; color: #fff; border: none; padding: 14px 40px; font-size: 1.1em; border-radius: 8px; cursor: pointer; font-weight: 600; }
        .submit-btn:hover { background: #219a52; }
        .submit-btn:disabled { background: #95a5a6; cursor: not-allowed; }
        .tax-badge { display: inline-block; background: #f39c12; color: #fff; font-size: 0.7em; padding: 2px 6px; border-radius: 3px; vertical-align: middle; margin-left: 4px; }
        .no-tax-badge { display: inline-block; background: #27ae60; color: #fff; font-size: 0.7em; padding: 2px 6px; border-radius: 3px; vertical-align: middle; margin-left: 4px; }
    </style>
</head>
<body>
<div class="container">
    <h1>🐣 Easter Beer Order</h1>
    <p class="subtitle">Dépanneur Rapido — Easter Specials</p>

    <form method="POST" action="confirm.php" id="orderForm">
        <div class="name-section">
            <label for="name">Your Name</label><br>
            <input type="text" id="name" name="name" required placeholder="Enter your name">
        </div>

        <table>
            <thead>
                <tr>
                    <th>Beer</th>
                    <th>Format</th>
                    <th class="right">Price</th>
                    <th class="right">Tax</th>
                    <th class="right">Deposit</th>
                    <th class="right">Unit Total</th>
                    <th class="right">Qty</th>
                    <th class="right">Line Total</th>
                </tr>
            </thead>
            <tbody>
                <?php foreach ($beers as $beer): ?>
                <tr data-total="<?= $beer['total'] ?>" id="row-<?= $beer['id'] ?>">
                    <td>
                        <?= htmlspecialchars($beer['name']) ?>
                        <?php if ($beer['taxable']): ?>
                            <span class="tax-badge">TAX</span>
                        <?php else: ?>
                            <span class="no-tax-badge">NO TAX</span>
                        <?php endif; ?>
                    </td>
                    <td class="format-label"><?= $beer['format'] ?></td>
                    <td class="right">$<?= number_format($beer['price'], 2) ?></td>
                    <td class="right">$<?= number_format($beer['tax'], 2) ?></td>
                    <td class="right">$<?= number_format($beer['deposit'], 2) ?></td>
                    <td class="right">$<?= number_format($beer['total'], 2) ?></td>
                    <td class="right">
                        <input type="number" class="qty-input" name="qty[<?= $beer['id'] ?>]" value="0" min="0" max="99" data-id="<?= $beer['id'] ?>">
                    </td>
                    <td class="right row-total" id="line-<?= $beer['id'] ?>">$0.00</td>
                </tr>
                <?php endforeach; ?>
            </tbody>
        </table>

        <div class="order-total">
            <div>
                <strong>Order Total:</strong>
                <span class="total-amount" id="grandTotal">$0.00</span>
            </div>
            <button type="submit" class="submit-btn" id="submitBtn" disabled>Review Order</button>
        </div>
    </form>
</div>

<script>
const totals = {};
<?php foreach ($beers as $beer): ?>
totals[<?= $beer['id'] ?>] = <?= $beer['total'] ?>;
<?php endforeach; ?>

function updateTotals() {
    let grand = 0;
    let hasItems = false;
    document.querySelectorAll('.qty-input').forEach(input => {
        const id = parseInt(input.dataset.id);
        const qty = parseInt(input.value) || 0;
        const lineTotal = qty * totals[id];
        const row = document.getElementById('row-' + id);
        document.getElementById('line-' + id).textContent = '$' + lineTotal.toFixed(2);

        if (qty > 0) {
            row.classList.add('has-qty');
            hasItems = true;
        } else {
            row.classList.remove('has-qty');
        }
        grand += lineTotal;
    });
    document.getElementById('grandTotal').textContent = '$' + grand.toFixed(2);

    const nameVal = document.getElementById('name').value.trim();
    document.getElementById('submitBtn').disabled = !hasItems || !nameVal;
}

document.querySelectorAll('.qty-input').forEach(input => {
    input.addEventListener('input', updateTotals);
});
document.getElementById('name').addEventListener('input', updateTotals);
</script>
</body>
</html>
