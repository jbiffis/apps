<?php
declare(strict_types=1);

// CORS headers
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');
header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

// Route the request
$requestUri = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$method = $_SERVER['REQUEST_METHOD'];

// Strip /api prefix
$path = preg_replace('#^/api#', '', $requestUri);
$path = rtrim($path, '/') ?: '/';

// Simple router
$routes = require __DIR__ . '/../src/routes.php';

foreach ($routes as $pattern => $handler) {
    [$routeMethod, $routePath] = explode(' ', $pattern, 2);
    if ($routeMethod !== $method) continue;

    $regex = '#^' . preg_replace('#\{(\w+)\}#', '(?P<$1>[^/]+)', $routePath) . '$#';
    if (preg_match($regex, $path, $matches)) {
        $params = array_filter($matches, 'is_string', ARRAY_FILTER_USE_KEY);
        try {
            $db = require __DIR__ . '/../src/db.php';
            echo json_encode($handler($db, $params));
        } catch (Throwable $e) {
            http_response_code(500);
            echo json_encode(['error' => $e->getMessage()]);
        }
        exit;
    }
}

http_response_code(404);
echo json_encode(['error' => 'Not found']);
