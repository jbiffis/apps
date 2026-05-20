#!/usr/bin/env bash
# Bring up the isolated tracker test stack and seed comprehensive data.
#   ./run.sh           # build (if needed), start, seed
#   ./run.sh --fresh   # tear down volumes first for a clean slate
set -euo pipefail
cd "$(dirname "$0")"

COMPOSE="docker compose -f compose.test.yaml"
SEED_HASH='$2b$10$TprcmORkBZ4brRj8t5IBM.KPwpMQk4KgiE.p/5mjfKdzjcCWl6.kS' # bcrypt("changeme-on-first-login")

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

echo "==> Adding extra test users + wiping logged data (idempotent)"
$COMPOSE exec -T tracker-test-db psql -U tracker -d tracker >/dev/null <<SQL
INSERT INTO users (username, display_name, password_hash, gender) VALUES
  ('morgan','Morgan','${SEED_HASH}','female'),
  ('dave','Dave','${SEED_HASH}','male')
ON CONFLICT (username) DO NOTHING;
TRUNCATE logged_event_options, logged_events;
SQL

echo "==> Seeding"
node seed.mjs

TOTAL=$($COMPOSE exec -T tracker-test-db psql -U tracker -d tracker -tA -c 'SELECT count(*) FROM logged_events;')
echo
echo "================================================================"
echo " Test env ready:  http://$(hostname -I | awk '{print $1}'):18080/tracker/"
echo " Users: carley / jeremy / morgan / dave     Password: changeme-on-first-login"
echo " Logged events in DB: ${TOTAL}"
echo "================================================================"
