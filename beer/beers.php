<?php

define('TAX_RATE', 0.15);
define('DEPOSIT_PER_CONTAINER', 0.10);
define('ORDERS_FILE', '/var/www/data/beer/orders.json');

session_start();

$beers = [
    // No tax (store pays both taxes)
    ['name' => 'Bud Light',              'format' => '30 cans',    'price' => 39.93, 'taxable' => false, 'containers' => 30],
    ['name' => 'Miller Lite',            'format' => '30 cans',    'price' => 39.93, 'taxable' => false, 'containers' => 30],
    ['name' => 'Coors Light',            'format' => '30 cans',    'price' => 39.93, 'taxable' => false, 'containers' => 30],
    ['name' => 'Molson Ultra',           'format' => '30 cans',    'price' => 39.93, 'taxable' => false, 'containers' => 30],
    ['name' => 'Sleeman Clear 2.0',      'format' => '20 cans',    'price' => 26.62, 'taxable' => false, 'containers' => 20],
    ['name' => 'Sleeman Light',          'format' => '20 cans',    'price' => 26.62, 'taxable' => false, 'containers' => 20],
    ['name' => 'Molson Canadian',        'format' => '30 cans',    'price' => 43.75, 'taxable' => false, 'containers' => 30],
    ['name' => 'Heineken (36 cans)',     'format' => '36 cans',    'price' => 48.81, 'taxable' => false, 'containers' => 36],

    // Taxed (15%)
    ['name' => 'Heineken Silver',        'format' => '24 bottles',  'price' => 29.70, 'taxable' => true, 'containers' => 24],
    ['name' => 'Heineken Silver',        'format' => '24 cans',     'price' => 29.70, 'taxable' => true, 'containers' => 24],
    ['name' => 'Corona Extra',           'format' => '24 bottles',  'price' => 31.37, 'taxable' => true, 'containers' => 24],
    ['name' => 'Sol',                    'format' => '24 bottles',  'price' => 31.37, 'taxable' => true, 'containers' => 24],
    ['name' => 'Miller High Life',       'format' => '24 bottles',  'price' => 33.74, 'taxable' => true, 'containers' => 24],
    ['name' => 'Pabst Blue Ribbon',      'format' => '24 bottles',  'price' => 33.74, 'taxable' => true, 'containers' => 24],
    ['name' => 'Heineken (24 bottles)',  'format' => '24 bottles',  'price' => 32.54, 'taxable' => true, 'containers' => 24],
    ['name' => 'Stella Artois',          'format' => '24 bottles',  'price' => 32.54, 'taxable' => true, 'containers' => 24],
    ['name' => "Alexander Keith's",      'format' => '24 bottles',  'price' => 38.50, 'taxable' => true, 'containers' => 24],
    ['name' => 'Blue Moon',              'format' => '24 bottles',  'price' => 38.50, 'taxable' => true, 'containers' => 24],
    ['name' => 'Belle Gueule',           'format' => '24 bottles',  'price' => 35.00, 'taxable' => true, 'containers' => 24],
    ['name' => 'Steamwhistle',           'format' => '24 bottles',  'price' => 35.00, 'taxable' => true, 'containers' => 24],
    ['name' => 'Sapporo',               'format' => '24 cans',     'price' => 35.00, 'taxable' => true, 'containers' => 24],
    ['name' => 'Landshark',             'format' => '24 bottles',  'price' => 35.00, 'taxable' => true, 'containers' => 24],
    ['name' => 'Peroni',                'format' => '24 cans',     'price' => 32.54, 'taxable' => true, 'containers' => 24],
    ['name' => 'Kronenbourg 1664 Blanc','format' => '24 cans',     'price' => 35.00, 'taxable' => true, 'containers' => 24],
];

// Pre-calculate tax, deposit, and total for each beer
foreach ($beers as $i => &$beer) {
    $beer['id'] = $i;
    $beer['tax'] = $beer['taxable'] ? round($beer['price'] * TAX_RATE, 2) : 0;
    $beer['deposit'] = round($beer['containers'] * DEPOSIT_PER_CONTAINER, 2);
    $beer['total'] = round($beer['price'] + $beer['tax'] + $beer['deposit'], 2);
}
unset($beer);

function load_orders(): array {
    if (!file_exists(ORDERS_FILE)) {
        return [];
    }
    $data = json_decode(file_get_contents(ORDERS_FILE), true);
    return is_array($data) ? $data : [];
}

function save_orders(array $orders): void {
    file_put_contents(ORDERS_FILE, json_encode($orders, JSON_PRETTY_PRINT), LOCK_EX);
}

function find_order_index(array $orders, string $name): int|false {
    $normalized = strtolower(trim($name));
    foreach ($orders as $i => $order) {
        if (strtolower(trim($order['name'])) === $normalized) {
            return $i;
        }
    }
    return false;
}

function generate_csrf_token(): string {
    if (empty($_SESSION['csrf_token'])) {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    }
    return $_SESSION['csrf_token'];
}

function verify_csrf_token(string $token): bool {
    return isset($_SESSION['csrf_token']) && hash_equals($_SESSION['csrf_token'], $token);
}

function validate_name(string $name): ?string {
    $name = trim($name);
    if (strlen($name) === 0) {
        return 'Name is required.';
    }
    if (strlen($name) > 100) {
        return 'Name must be 100 characters or fewer.';
    }
    if (!preg_match('/^[\p{L}\p{N}\s\'-]+$/u', $name)) {
        return 'Name contains invalid characters.';
    }
    return null;
}
