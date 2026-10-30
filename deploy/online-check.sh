#!/usr/bin/env bash
# Checks the public site from this machine: the web client, the portal and the sign-in page answer,
# and the backend and the object store report UP.
#
#   ./online-check.sh
#
# Prints OK or FAIL per check and exits 1 when any check fails. Run it from cron, for example:
#   0 */12 * * * cd /path/to/deploy && ./online-check.sh >> online-check.log 2>&1
#
# Settings:
#   PUBLIC_URL                  address to check, default GATEWAY_PUBLIC_URL of deploy/.env
#   MATTERMOST_WEBHOOK_URL_ADMIN, MATTERMOST_WEBHOOK_URL (deploy/.env, either one)
#                               when set, a message is posted when the site goes down and when it is back
#   STATE_FILE                  remembers the last result, default /tmp/dxos-online-check.state
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

env_value() { [[ -f .env ]] && grep -E "^$1=" .env | tail -n1 | cut -d= -f2- || true; }

BASE_URL="${PUBLIC_URL:-$(env_value GATEWAY_PUBLIC_URL)}"
BASE_URL="${BASE_URL%/}"
STATE_FILE="${STATE_FILE:-/tmp/dxos-online-check.state}"
WEBHOOK="$(env_value MATTERMOST_WEBHOOK_URL_ADMIN)"
WEBHOOK="${WEBHOOK:-$(env_value MATTERMOST_WEBHOOK_URL)}"

[[ -n "$BASE_URL" ]] || { echo "Set PUBLIC_URL or GATEWAY_PUBLIC_URL in deploy/.env." >&2; exit 1; }

FAILED=()
fetch() { curl --fail --silent --show-error --max-time 20 --retry 3 --retry-delay 10 --retry-all-errors "$@"; }

for path in / /portal/ /realms/dxos/.well-known/openid-configuration; do
  if err="$(fetch --output /dev/null "$BASE_URL$path" 2>&1)"; then
    printf '  [ OK ] %s\n' "$path"
  else
    printf '  [FAIL] %s: %s\n' "$path" "$err"
    FAILED+=("$path")
  fi
done

if status="$(fetch "$BASE_URL/api/v1/system/status" 2>&1)"; then
  # Prints every service, then the names of backend and storage that are not UP; exits 2 on an unreadable body.
  down="$(python3 -I -c '
import json, sys
try:
    services = {s["name"]: s["status"] for s in json.load(sys.stdin)["data"]["services"]}
except (ValueError, KeyError, TypeError):
    sys.exit(2)
for name, state in services.items():
    print("  [ OK ] %s %s" % (name, state), file=sys.stderr)
print(" ".join(n for n in ("backend", "storage") if services.get(n) != "UP"))
' <<<"$status")"
  case $? in
    0) [[ -z "$down" ]] || { printf '  [FAIL] not UP: %s\n' "$down"; FAILED+=("$down"); } ;;
    *) printf '  [FAIL] /api/v1/system/status: the body is not a status report\n'; FAILED+=("/api/v1/system/status") ;;
  esac
else
  printf '  [FAIL] /api/v1/system/status: %s\n' "$status"
  FAILED+=("/api/v1/system/status")
fi

result=up
(( ${#FAILED[@]} )) && result=down

# Post to Mattermost only when the result changes, so a long outage is one message.
previous="$(cat "$STATE_FILE" 2>/dev/null || echo up)"
echo "$result" > "$STATE_FILE"
if [[ -n "$WEBHOOK" && "$result" != "$previous" ]]; then
  if [[ "$result" == down ]]; then text="Online check: $BASE_URL is failing on ${FAILED[*]}"; else text="Online check: $BASE_URL is back"; fi
  curl --silent --max-time 20 --output /dev/null -H 'Content-Type: application/json' \
    -d "$(python3 -I -c 'import json, sys; print(json.dumps({"text": sys.argv[1]}))' "$text")" "$WEBHOOK" || true
fi

[[ "$result" == up ]]
