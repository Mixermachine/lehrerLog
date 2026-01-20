#!/usr/bin/env bash
set -euo pipefail

DOMAIN="${DOMAIN:-}"
QUERY="${QUERY:-Ulm}"
LIMIT="${LIMIT:-5}"
VERIFY_USER_EMAIL="${VERIFY_USER_EMAIL:-}"
VERIFY_USER_PASSWORD="${VERIFY_USER_PASSWORD:-}"
RETRY_COUNT="${RETRY_COUNT:-12}"
RETRY_DELAY="${RETRY_DELAY:-5}"

if [[ -z "$DOMAIN" ]]; then
  echo "Error: DOMAIN is required (e.g., staging.lehrerlog.9d4.de)."
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "Error: curl is required for verification."
  exit 1
fi

BASE_URL="https://${DOMAIN}"

retry_curl() {
  local url="$1"
  local attempt=1
  while true; do
    if response="$(curl -fsS "$url")"; then
      printf "%s" "$response"
      return 0
    fi
    if [[ "$attempt" -ge "$RETRY_COUNT" ]]; then
      return 1
    fi
    sleep "$RETRY_DELAY"
    attempt=$((attempt + 1))
  done
}

echo "Verifying health endpoint..."
health="$(retry_curl "${BASE_URL}/health")" || {
  echo "Error: health check failed after retries."
  exit 1
}
echo "$health"
echo "$health" | grep -q '"status"[[:space:]]*:[[:space:]]*"ok"' || {
  echo "Error: health check did not report status=ok."
  exit 1
}

echo "Verifying school search..."
schools="$(retry_curl "${BASE_URL}/schools/search?query=${QUERY}&limit=${LIMIT}")" || {
  echo "Error: school search failed after retries."
  exit 1
}
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

  login_attempt=1
  while true; do
    login_body="$(mktemp)"
    login_code="$(curl -sS -o "$login_body" -w "%{http_code}" \
      -H "Content-Type: application/json" \
      -H "Accept-Encoding: identity" \
      -d "$login_payload" \
      "${BASE_URL}/auth/login" || true)"
    if [[ "$login_code" == "200" ]]; then
      login_json="$(cat "$login_body")"
      rm -f "$login_body"
      if [[ -n "$login_json" ]]; then
        break
      fi
    else
      rm -f "$login_body"
    fi
    if [[ "$login_attempt" -ge "$RETRY_COUNT" ]]; then
      echo "Error: login failed after retries (last status: $login_code)."
      exit 1
    fi
    sleep "$RETRY_DELAY"
    login_attempt=$((login_attempt + 1))
  done
  echo "$login_json" | grep -q '"accessToken"' || {
    echo "Error: login response missing accessToken."
    exit 1
  }

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

  me_email="$(curl -fsS -H "Authorization: Bearer ${access_token}" -H "Accept-Encoding: identity" "${BASE_URL}/auth/me" | python3 - <<'PY'
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
  classes_json="$(curl -fsS -H "Authorization: Bearer ${access_token}" -H "Accept-Encoding: identity" "${BASE_URL}/api/classes")"
  echo "$classes_json" | grep -q '^\[' || {
    echo "Error: /api/classes did not return a JSON array."
    exit 1
  }
fi

echo "Verification passed."
