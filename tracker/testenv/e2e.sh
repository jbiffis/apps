#!/usr/bin/env bash
# Headless E2E for the tracker frontend. Drives the official Playwright Docker
# image (browsers preinstalled — nothing installed on the host) against the
# running test env. Bring the env up to current code first: ./run.sh
#
#   ./e2e.sh                       # run all specs
#   ./e2e.sh e2e/auth.spec.js      # run one spec (args pass through to playwright)
#   E2E_BASE_URL=http://host:18080/tracker/ ./e2e.sh
set -euo pipefail
cd "$(dirname "$0")/../frontend"

IMG="mcr.microsoft.com/playwright:v1.49.1-jammy"
# Join the test env's compose network and hit the web service by name —
# container-to-container, avoiding host-loopback/docker-proxy/netns issues that
# break Chromium navigation under --network host.
NET="${E2E_NETWORK:-tracker-test_default}"
BASE="${E2E_BASE_URL:-http://tracker-test-web/tracker/}"

# @playwright/test must be installed locally (npm install) so it's present in
# the mounted node_modules; the image supplies the matching browsers.
if [ ! -d node_modules/@playwright ]; then
  echo "@playwright/test not installed — run 'npm install' in tracker/frontend first." >&2
  exit 1
fi

exec docker run --rm --network "$NET" \
  -v "$PWD":/work -w /work \
  -e E2E_BASE_URL="$BASE" -e CI=1 \
  "$IMG" npx playwright test "$@"
