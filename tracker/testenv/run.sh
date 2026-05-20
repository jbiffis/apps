#!/usr/bin/env bash
# Bring up the isolated tracker test stack and seed comprehensive data.
#   ./run.sh           # build (if needed), start, seed
#   ./run.sh --fresh   # tear down volumes first for a clean slate
set -euo pipefail
cd "$(dirname "$0")"

COMPOSE="docker compose -f compose.test.yaml"
TEST_HASH='$2y$10$AQkLfP2cqt2YVVgUKxpDlOqaVqihYH0PXj7w6ZwIO3hExE2wTo9Zy' # bcrypt("test123") — test-only

if [[ "${1:-}" == "--fresh" ]]; then
  echo "==> Tearing down (including volumes)"
  $COMPOSE down -v
fi

echo "==> Building frontend"
( cd ../frontend && npm install --silent && npm run build )

echo "==> Starting stack"
$COMPOSE up -d --build

echo "==> Waiting for API health"
for i in $(seq 1 60); do
  if curl -sf http://localhost:18080/tracker/api/health >/dev/null; then break; fi
  sleep 2
  [[ $i -eq 60 ]] && { echo "API never came healthy"; $COMPOSE logs --tail=40 tracker-test-backend; exit 1; }
done
echo "    healthy."

echo "==> Adding extra test users + setting all passwords to test123 + wiping logged data (idempotent)"
$COMPOSE exec -T tracker-test-db psql -U tracker -d tracker >/dev/null <<SQL
INSERT INTO users (username, display_name, password_hash, gender) VALUES
  ('morgan','Morgan','${TEST_HASH}','female'),
  ('dave','Dave','${TEST_HASH}','male')
ON CONFLICT (username) DO NOTHING;
-- Test env only: every user logs in with test123 (prod keeps its own hashes).
UPDATE users SET password_hash='${TEST_HASH}';
TRUNCATE logged_event_options, logged_events;
SQL

echo "==> Seeding"
node seed.mjs

TOTAL=$($COMPOSE exec -T tracker-test-db psql -U tracker -d tracker -tA -c 'SELECT count(*) FROM logged_events;')
echo
echo "================================================================"
echo " Test env ready:  http://$(hostname -I | awk '{print $1}'):18080/tracker/"
echo " Users: carley / jeremy / morgan / dave     Password: test123"
echo " Logged events in DB: ${TOTAL}"
echo "================================================================"
