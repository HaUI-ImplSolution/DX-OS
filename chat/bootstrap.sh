#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Tạo admin, team, channel mặc định và một incoming webhook cho mỗi channel qua REST API. Idempotent.
# URL webhook ghi ra $WEBHOOKS_DIR/<khóa>.url để Backend đọc.
set -euo pipefail

BASE="${MATTERMOST_URL:-http://mattermost:8065}/api/v4"
ADMIN_USERNAME="${MATTERMOST_ADMIN_USERNAME:-admin}"
ADMIN_EMAIL="${MATTERMOST_ADMIN_EMAIL:-admin@dxos.local}"
ADMIN_PASSWORD="${MATTERMOST_ADMIN_PASSWORD:?MATTERMOST_ADMIN_PASSWORD is required}"
TEAM_NAME="dx-farm"
TEAM_DISPLAY="DX-Farm HTX"
WEBHOOKS_DIR="${WEBHOOKS_DIR:-}"
HOOK_BASE="${MATTERMOST_URL:-http://mattermost:8065}/hooks"

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

# khóa file|tên channel; khóa trùng với khóa kênh của Backend (default là kênh dự phòng)
WEBHOOK_CHANNELS=(
  "default|thong-bao-chung"
  "engineer|ky-su-nong-nghiep"
  "admin|ban-quan-tri"
  "technician|ky-thuat-vien"
)

if [ -n "$WEBHOOKS_DIR" ]; then
  mkdir -p "$WEBHOOKS_DIR"
  hooks="$(api "$BASE/hooks/incoming?team_id=$TEAM_ID&per_page=200" | sed 's/},{/}\n{/g')"
  for entry in "${WEBHOOK_CHANNELS[@]}"; do
    IFS='|' read -r key name <<<"$entry"
    channel_id="$(api "$BASE/teams/$TEAM_ID/channels/name/$name" | json_id)"
    [ -n "$channel_id" ] || { log "channel '$name' not found"; exit 1; }
    hook_name="DX-OS $key"
    hook_id="$(printf '%s\n' "$hooks" | grep "\"channel_id\":\"$channel_id\"" | grep "\"display_name\":\"$hook_name\"" \
      | sed -n 's/^[[]\{0,1\}{"id":"\([a-z0-9]*\)".*/\1/p' | head -n 1 || true)"
    if [ -z "$hook_id" ]; then
      log "creating webhook '$hook_name'"
      hook_id="$(api -X POST "$BASE/hooks/incoming" \
        -d "{\"channel_id\":\"$channel_id\",\"display_name\":\"$hook_name\",\"channel_locked\":true}" | json_id)"
    fi
    [ -n "$hook_id" ] || { log "cannot create webhook '$hook_name'"; exit 1; }
    printf '%s' "$HOOK_BASE/$hook_id" >"$WEBHOOKS_DIR/$key.url.tmp"
    mv "$WEBHOOKS_DIR/$key.url.tmp" "$WEBHOOKS_DIR/$key.url"
  done
fi

log "done"
