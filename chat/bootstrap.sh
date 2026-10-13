#!/bin/bash
# Tạo admin, team và channel mặc định cho Mattermost qua REST API. Idempotent.
set -euo pipefail

BASE="${MATTERMOST_URL:-http://mattermost:8065}/api/v4"
ADMIN_USERNAME="${MATTERMOST_ADMIN_USERNAME:-admin}"
ADMIN_EMAIL="${MATTERMOST_ADMIN_EMAIL:-admin@dxos.local}"
ADMIN_PASSWORD="${MATTERMOST_ADMIN_PASSWORD:?MATTERMOST_ADMIN_PASSWORD is required}"
TEAM_NAME="dx-farm"
TEAM_DISPLAY="DX-Farm HTX"

# name|display name|type (O = public, P = private)|purpose
CHANNELS=(
  "thong-bao-chung|Thông báo chung|O|Thông báo chung toàn hợp tác xã"
  "ky-su-nong-nghiep|Kỹ sư nông nghiệp|O|Trao đổi chẩn đoán và khuyến nghị điều trị"
  "ban-quan-tri|Ban quản trị|P|Kênh riêng của Ban quản trị"
  "ky-thuat-vien|Kỹ thuật viên|O|Yêu cầu hỗ trợ mới và yêu cầu quá hạn của DX-Lab"
)

log() { echo "[mattermost-bootstrap] $*"; }

# Lấy "id" ở đầu object JSON; rỗng khi response là lỗi.
json_id() { sed -n 's/^{"id":"\([a-z0-9]*\)".*/\1/p' | head -n 1; }

for _ in $(seq 1 60); do
  curl -sf "$BASE/system/ping" >/dev/null && break
  sleep 5
done
curl -sf "$BASE/system/ping" >/dev/null || { log "server is not reachable"; exit 1; }

login() {
  curl -s -D - -o /dev/null -X POST "$BASE/users/login" \
    -H 'Content-Type: application/json' \
    -d "{\"login_id\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}" \
    | tr -d '\r' | sed -n 's/^[Tt]oken: //p'
}

TOKEN="$(login)"
if [ -z "$TOKEN" ]; then
  log "creating admin user '$ADMIN_USERNAME'"
  curl -sf -o /dev/null -X POST "$BASE/users" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}"
  TOKEN="$(login)"
fi
[ -n "$TOKEN" ] || { log "cannot log in as '$ADMIN_USERNAME'"; exit 1; }

api() { curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' "$@"; }

TEAM_ID="$(api "$BASE/teams/name/$TEAM_NAME" | json_id)"
if [ -z "$TEAM_ID" ]; then
  log "creating team '$TEAM_NAME'"
  TEAM_ID="$(api -X POST "$BASE/teams" \
    -d "{\"name\":\"$TEAM_NAME\",\"display_name\":\"$TEAM_DISPLAY\",\"type\":\"O\"}" | json_id)"
fi
[ -n "$TEAM_ID" ] || { log "cannot create team"; exit 1; }

for entry in "${CHANNELS[@]}"; do
  IFS='|' read -r name display type purpose <<<"$entry"
  if [ -n "$(api "$BASE/teams/$TEAM_ID/channels/name/$name" | json_id)" ]; then
    continue
  fi
  log "creating channel '$name'"
  [ -n "$(api -X POST "$BASE/channels" \
    -d "{\"team_id\":\"$TEAM_ID\",\"name\":\"$name\",\"display_name\":\"$display\",\"type\":\"$type\",\"purpose\":\"$purpose\"}" \
    | json_id)" ] || { log "cannot create channel '$name'"; exit 1; }
done

log "done"
