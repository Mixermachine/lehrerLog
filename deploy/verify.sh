#!/usr/bin/env bash
set -euo pipefail

DOMAIN="${DOMAIN:-}"
QUERY="${QUERY:-Ulm}"
LIMIT="${LIMIT:-5}"

if [[ -z "$DOMAIN" ]]; then
  echo "Error: DOMAIN is required (e.g., staging.lehrerlog.9d4.de)."
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "Error: curl is required for verification."
  exit 1
fi

BASE_URL="https://${DOMAIN}"

echo "Verifying health endpoint..."
health="$(curl -fsS "${BASE_URL}/health")"
echo "$health"
echo "$health" | grep -q '"status"[[:space:]]*:[[:space:]]*"ok"' || {
  echo "Error: health check did not report status=ok."
  exit 1
}

echo "Verifying school search..."
schools="$(curl -fsS "${BASE_URL}/schools/search?query=${QUERY}&limit=${LIMIT}")"
echo "$schools"
echo "$schools" | grep -q '"code"' || {
  echo "Error: school search returned no results."
  exit 1
}

echo "Verification passed."
