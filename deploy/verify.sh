#!/usr/bin/env bash
set -euo pipefail

DOMAIN="${DOMAIN:-}"
QUERY="${QUERY:-Ulm}"
LIMIT="${LIMIT:-5}"
VERIFY_USER_EMAIL="${VERIFY_USER_EMAIL:-}"
VERIFY_USER_PASSWORD="${VERIFY_USER_PASSWORD:-}"

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

if [[ -n "$VERIFY_USER_EMAIL" || -n "$VERIFY_USER_PASSWORD" ]]; then
  if [[ -z "$VERIFY_USER_EMAIL" || -z "$VERIFY_USER_PASSWORD" ]]; then
    echo "Error: VERIFY_USER_EMAIL and VERIFY_USER_PASSWORD must both be set."
    exit 1
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    echo "Error: python3 is required for auth verification."
    exit 1
  fi

  echo "Verifying auth login and /auth/me..."
  login_payload="$(python3 - <<'PY'
import json
import os
print(json.dumps({
    "email": os.environ["VERIFY_USER_EMAIL"],
    "password": os.environ["VERIFY_USER_PASSWORD"],
}))
PY
)"

  login_json="$(curl -fsS -H "Content-Type: application/json" -d "$login_payload" "${BASE_URL}/auth/login")"
  access_token="$(python3 - <<'PY'
import json
import sys
data = json.load(sys.stdin)
print(data.get("accessToken", ""))
PY
  <<< "$login_json")"

  if [[ -z "$access_token" ]]; then
    echo "Error: login did not return an access token."
    exit 1
  fi

  me_email="$(curl -fsS -H "Authorization: Bearer ${access_token}" "${BASE_URL}/auth/me" | python3 - <<'PY'
import json
import sys
data = json.load(sys.stdin)
print(data.get("email", ""))
PY
)"

  if [[ "$me_email" != "$VERIFY_USER_EMAIL" ]]; then
    echo "Error: /auth/me returned unexpected user email."
    exit 1
  fi

  echo "Verifying class listing..."
  classes_json="$(curl -fsS -H "Authorization: Bearer ${access_token}" "${BASE_URL}/api/classes")"
  echo "$classes_json" | grep -q '^\[' || {
    echo "Error: /api/classes did not return a JSON array."
    exit 1
  }
fi

echo "Verification passed."
