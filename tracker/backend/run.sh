#!/usr/bin/env bash
# Convenience runner for local development.
# Spins the Postgres container (via the root compose.yaml) and runs the app on the host JVM.
set -euo pipefail
cd "$(dirname "$0")"

# Load env vars from the repo-root .env if present so DB creds are discoverable.
if [ -f "../../.env" ]; then
    set -a
    # shellcheck disable=SC1091
    source ../../.env
    set +a
fi

export DB_HOST=${DB_HOST:-localhost}
export DB_PORT=${DB_PORT:-5432}
export DB_NAME=${DB_NAME:-tracker}
export DB_USERNAME=${DB_USERNAME:-tracker}
export DB_PASSWORD=${DB_PASSWORD:-tracker}
export JWT_SECRET=${JWT_SECRET:-dev-secret-change-me-at-least-32chars}
export APP_PORT=${APP_PORT:-8080}

./mvnw spring-boot:run "$@"
