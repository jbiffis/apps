<?php
declare(strict_types=1);

static $pdo;
if ($pdo) return $pdo;

$host = $_ENV['DB_HOST'] ?? getenv('DB_HOST') ?: 'mysql';
$name = $_ENV['DB_NAME'] ?? getenv('DB_NAME') ?: 'simgolf';
$user = $_ENV['DB_USER'] ?? getenv('DB_USER') ?: 'simgolf';
$pass = $_ENV['DB_PASS'] ?? getenv('DB_PASS') ?: 'simgolf';

$pdo = new PDO(
    "mysql:host=$host;dbname=$name;charset=utf8mb4",
    $user,
    $pass,
    [
        PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES   => false,
    ]
);

return $pdo;
