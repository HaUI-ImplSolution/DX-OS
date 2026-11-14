#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Accounts and sample records for the people who try DX-OS (see dxos demo).
#
#   ./demo.sh [--password PASSWORD]   apply the student one-stop model when it is not running, create the
#                                     accounts judge_student, judge_officer (DAO_TAO), judge_officer2
#                                     (CTSV) and judge_supervisor, and file about 40 sample requests
#   ./demo.sh remove                  delete the four accounts and nothing else
#
# One password serves the four accounts: PASSWORD, or a random one. It is printed once, and running
# the command again sets and prints a new one. The records go through the same API as the web
# client, each step as the person who takes it, so they pass the rules and show up in the history.
# They are filed once: a run that finds them leaves them alone. A request filed now becomes overdue
# on its own when its deadline passes, which is how the overdue ones appear later.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DEPLOY_DIR"
[[ -f .env ]] || { echo "demo: $DEPLOY_DIR/.env is missing; install DX-OS with install.sh first" >&2; exit 1; }
MODEL="onestop"
PASSWORD_ARG=""
SEED_CLIENT="dxos-demo-seed"
USERS="judge_student judge_officer judge_officer2 judge_supervisor"

die() { echo "demo: $*" >&2; exit 1; }
env_value() { grep -E "^$1=" "$DEPLOY_DIR/.env" | tail -n1 | cut -d= -f2- || true; }
compose() { docker compose "$@"; }
json_field() { sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\).*/\1/p" <<<"$2" | head -n1; }

# The address browsers use; GATEWAY_PUBLIC_URL unless it still refers to other variables.
public_url() {
  local url host port
  url="$(env_value GATEWAY_PUBLIC_URL)"
  # shellcheck disable=SC2016 # a literal "${" marks a value Compose interpolates
  if [[ -z "$url" || "$url" == *'${'* ]]; then
    host="$(env_value PUBLIC_HOST)"
    port="$(env_value APISIX_HOST_PORT)"
    url="http://${host:-localhost}:${port##*:}"
  fi
  echo "${url%/}"
}

WORK="$(mktemp -d)"
chmod 700 "$WORK"
SEED_CLIENT_MADE=false
cleanup() {
  if $SEED_CLIENT_MADE; then keycloak_admin delete-seed-client >/dev/null 2>&1 || true; fi
  rm -rf "$WORK"
}
trap cleanup EXIT

# Runs kcadm.sh in the Keycloak container: keycloak_admin <step> [VARIABLE=value...]. The steps are below.
keycloak_admin() {
  local step="$1" env=() pair
  shift
  # A name without a value is passed from the environment, so a password never reaches a command line.
  for pair in "$@"; do env+=(-e "$pair"); done
  compose exec -T -e STEP="$step" -e MODEL="$MODEL" -e SEED_CLIENT="$SEED_CLIENT" -e USERS="$USERS" \
    ${env[@]+"${env[@]}"} keycloak bash -s <<'EOF'
set -euo pipefail
kc() { /opt/keycloak/bin/kcadm.sh "$@" --config /tmp/dxos-kcadm.config; }
trap 'rm -f /tmp/dxos-kcadm.config' EXIT
kc config credentials --server http://localhost:8080 --realm master \
  --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null 2>&1 \
  || { echo "cannot sign in to Keycloak as $KC_BOOTSTRAP_ADMIN_USERNAME" >&2; exit 3; }
id_of() { kc get "$1" -r dxos -q "$2" --fields id --format csv --noquotes | head -n1; }
# The search of Keycloak matches parts of a name, so an account is looked up with exact=true.
uid_of() { kc get users -r dxos -q exact=true -q "username=$1" --fields id --format csv --noquotes | head -n1; }
case "$STEP" in
  accounts)
    # name:role:unit of the four accounts; the role is a role of the model.
    for spec in judge_student:STUDENT: judge_officer:OFFICER:DAO_TAO judge_officer2:OFFICER:CTSV judge_supervisor:SUPERVISOR:; do
      IFS=: read -r user role unit <<<"$spec"
      if [[ -z "$(uid_of "$user")" ]]; then
        kc create users -r dxos -s "username=$user" -s enabled=true -s emailVerified=true \
          -s "email=$user@example.org" -s firstName=Judge -s "lastName=${user#judge_}" >/dev/null 2>&1
      fi
      kc set-password -r dxos --username "$user" --new-password "$DEMO_PASSWORD"
      kc add-roles -r dxos --uusername "$user" --cclientid "dxos-m-$MODEL" --rolename "$role"
      if [[ -n "$unit" ]]; then
        uid="$(uid_of "$user")"
        gid="$(kc get groups -r dxos -q "search=$unit" -q exact=true --fields id --format csv --noquotes | head -n1)"
        [[ -n "$gid" ]] || { echo "Keycloak has no group $unit" >&2; exit 4; }
        kc update "users/$uid/groups/$gid" -r dxos -s realm=dxos -s "userId=$uid" -s "groupId=$gid" -n
      fi
    done ;;
  remove)
    for user in $USERS; do
      uid="$(uid_of "$user")"
      [[ -z "$uid" ]] || kc delete "users/$uid" -r dxos
    done ;;
  make-seed-client)
    # A client that signs a person in with the password, only while the sample records are filed.
    [[ -n "$(id_of clients "clientId=$SEED_CLIENT")" ]] \
      || kc create clients -r dxos -s "clientId=$SEED_CLIENT" -s publicClient=true -s enabled=true \
        -s directAccessGrantsEnabled=true -s standardFlowEnabled=false \
        -s 'protocolMappers=[{"name":"dxos-api-audience","protocol":"openid-connect","protocolMapper":"oidc-audience-mapper","config":{"included.custom.audience":"dxos-api","access.token.claim":"true","id.token.claim":"false"}},{"name":"groups","protocol":"openid-connect","protocolMapper":"oidc-group-membership-mapper","config":{"full.path":"false","access.token.claim":"true","id.token.claim":"false","claim.name":"groups","userinfo.token.claim":"false"}}]' >/dev/null 2>&1 ;;
  delete-seed-client)
    cid="$(id_of clients "clientId=$SEED_CLIENT")"
    [[ -z "$cid" ]] || kc delete "clients/$cid" -r dxos ;;
esac
EOF
}

# --- API calls ---------------------------------------------------------------------------------

BASE="$(public_url)"
API_STATUS=""
API_BODY=""

# Signs in and prints the access token: token client_credentials <client> <secret> or
# token password <user> <password>. Secrets go through files, never the command line.
token() {
  local kind="$1" a="$2" b="$3" out
  out="$WORK/token.json"
  if [[ "$kind" == client_credentials ]]; then
    printf '%s' "$b" >"$WORK/secret"
    curl -sS -o "$out" --data-urlencode grant_type=client_credentials --data-urlencode "client_id=$a" \
      --data-urlencode "client_secret@$WORK/secret" "$BASE/realms/dxos/protocol/openid-connect/token" || true
  else
    printf '%s' "$b" >"$WORK/secret"
    curl -sS -o "$out" --data-urlencode grant_type=password --data-urlencode "client_id=$SEED_CLIENT" \
      --data-urlencode "username=$a" --data-urlencode "password@$WORK/secret" \
      "$BASE/realms/dxos/protocol/openid-connect/token" || true
  fi
  sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p' "$out"
}

# api <token> <METHOD> <path> [content-type [data-file]]; the status goes to API_STATUS, the body to API_BODY.
api() {
  local bearer="$1" method="$2" path="$3" type="${4:-}" file="${5:-}" args=()
  printf 'Authorization: Bearer %s\n' "$bearer" >"$WORK/header"
  [[ -n "$type" ]] && args+=(-H "Content-Type: $type")
  [[ -n "$file" ]] && args+=(--data-binary "@$file")
  API_STATUS="$(curl -sS -o "$WORK/body" -w '%{http_code}' -X "$method" -H "@$WORK/header" \
    ${args[@]+"${args[@]}"} "$BASE/api/v1$path")" || die "the API did not answer at $BASE"
  API_BODY="$(cat "$WORK/body")"
}

# api_json <token> <METHOD> <path> <json>
api_json() {
  printf '%s' "$4" >"$WORK/json"
  api "$1" "$2" "$3" application/json "$WORK/json"
}

# --- Sample records ----------------------------------------------------------------------------

# One request per line: unit|kind|reason|steps. The steps run in order: file (an attachment from the
# student), note=TEXT (a note from the student), approve, reject=TEXT, return=TEXT (an officer of the
# unit), resubmit, withdraw (the student). The first reason marks a run that has filed them.
RECORDS='DAO_TAO|Giấy xác nhận|Xin giấy xác nhận sinh viên để làm hồ sơ vay vốn học tập|file
CTSV|Giấy xác nhận|Xin giấy xác nhận để đăng ký tạm trú tại ký túc xá|
DAO_TAO|Phúc khảo|Phúc khảo môn Cơ sở dữ liệu, điểm cuối kỳ thấp hơn dự kiến|file,note=Em đã nộp kèm ảnh bài thi
DAO_TAO|Hoãn thi|Xin hoãn thi môn Mạng máy tính vì trùng lịch thực tập|
CTSV|Giấy xác nhận|Xin giấy xác nhận sinh viên để làm thẻ xe buýt|
DAO_TAO|Phúc khảo|Phúc khảo môn Giải tích 2, nghi ngờ chấm thiếu một câu|
CTSV|Hoãn thi|Xin hoãn thi vì đang điều trị tại bệnh viện|file
DAO_TAO|Giấy xác nhận|Xin giấy xác nhận điểm để nộp học bổng doanh nghiệp|
CTSV|Giấy xác nhận|Xin giấy xác nhận sinh viên để xin việc làm thêm|
DAO_TAO|Hoãn thi|Xin hoãn thi môn Hệ điều hành do trùng lịch thi lại|
CTSV|Phúc khảo|Phúc khảo điểm rèn luyện học kỳ vừa qua|note=Em xin bổ sung minh chứng hoạt động tình nguyện
DAO_TAO|Giấy xác nhận|Xin giấy xác nhận hoàn thành chương trình để làm thủ tục tốt nghiệp|
DAO_TAO|Phúc khảo|Phúc khảo môn Lập trình hướng đối tượng|return=Vui lòng đính kèm bài thi để đối chiếu
CTSV|Giấy xác nhận|Xin giấy xác nhận hoàn cảnh để xét miễn giảm học phí|return=Cần bổ sung giấy xác nhận của địa phương
DAO_TAO|Hoãn thi|Xin hoãn thi môn Xác suất thống kê vì lý do gia đình|return=Cần giấy tờ chứng minh lý do hoãn thi
CTSV|Hoãn thi|Xin hoãn thi vì tham gia đội tuyển thi đấu cấp trường|return=Đề nghị có xác nhận của huấn luyện viên
DAO_TAO|Phúc khảo|Phúc khảo môn Kiến trúc máy tính|return=Hãy ghi rõ câu hỏi cần xem lại,resubmit
CTSV|Giấy xác nhận|Xin giấy xác nhận sinh viên để gia hạn visa du học trao đổi|return=Thiếu bản sao hộ chiếu,file,resubmit
DAO_TAO|Giấy xác nhận|Xin giấy xác nhận bảng điểm tiếng Anh|return=Vui lòng nêu mục đích sử dụng,resubmit
DAO_TAO|Giấy xác nhận|Xin giấy xác nhận sinh viên để mở tài khoản ngân hàng|approve
CTSV|Giấy xác nhận|Xin giấy xác nhận sinh viên để xin hỗ trợ chi phí học tập|file,approve
DAO_TAO|Phúc khảo|Phúc khảo môn Cấu trúc dữ liệu và giải thuật|approve
CTSV|Hoãn thi|Xin hoãn thi vì tham gia hiến máu tình nguyện|approve
DAO_TAO|Hoãn thi|Xin hoãn thi môn Toán rời rạc do ốm|file,approve
CTSV|Giấy xác nhận|Xin giấy xác nhận sinh viên để làm thủ tục ký túc xá|approve
DAO_TAO|Giấy xác nhận|Xin giấy xác nhận sinh viên để đăng ký thi chứng chỉ|approve
CTSV|Phúc khảo|Phúc khảo điểm cộng hoạt động ngoại khóa|approve
DAO_TAO|Phúc khảo|Phúc khảo môn Vật lý đại cương|approve
CTSV|Giấy xác nhận|Xin giấy xác nhận sinh viên để đăng ký bảo hiểm y tế|approve
DAO_TAO|Hoãn thi|Xin hoãn thi vì chưa đủ điều kiện dự thi|reject=Chưa có giấy tờ chứng minh lý do hoãn thi
CTSV|Giấy xác nhận|Xin giấy xác nhận thu nhập gia đình|reject=Hồ sơ không thuộc phạm vi xử lý của phòng
DAO_TAO|Phúc khảo|Phúc khảo môn Triết học sau thời hạn quy định|reject=Quá thời hạn nhận đơn phúc khảo
CTSV|Hoãn thi|Xin hoãn thi vì đi du lịch cùng gia đình|reject=Lý do không thuộc diện được hoãn thi
DAO_TAO|Giấy xác nhận|Xin giấy xác nhận sinh viên năm cuối|reject=Đề nghị nộp lại kèm mã sinh viên
DAO_TAO|Giấy xác nhận|Xin giấy xác nhận sinh viên để làm thẻ thư viện|withdraw
CTSV|Hoãn thi|Xin hoãn thi, em đã sắp xếp được lịch nên rút đơn|withdraw
DAO_TAO|Phúc khảo|Phúc khảo môn Anh văn 3, em xin rút lại đơn|withdraw
CTSV|Giấy xác nhận|Xin giấy xác nhận sinh viên để xin học bổng khuyến khích|file,note=Em gửi kèm bảng điểm học kỳ trước
DAO_TAO|Hoãn thi|Xin hoãn thi môn Pháp luật đại cương do trùng lịch công tác|file,note=Đơn có xác nhận của đơn vị thực tập
CTSV|Phúc khảo|Phúc khảo điểm rèn luyện năm học trước|file,note=Em xin đính kèm giấy khen của khoa'

# The mark of a run that has filed the records: the first reason is found among them.
already_filed() {
  local first reason
  first="$(head -n1 <<<"$RECORDS")"
  IFS='|' read -r _ _ reason _ <<<"$first"
  curl -sS -G -o "$WORK/body" -w '%{http_code}' -H "@$WORK/header" --data-urlencode "filter.reason=$reason" \
    --data-urlencode size=1 "$BASE/api/v1/data/$MODEL/request" >"$WORK/status" || true
  [[ "$(cat "$WORK/status")" == 200 ]] || return 1
  [[ "$(json_field totalElements "$(cat "$WORK/body")")" =~ ^[1-9][0-9]*$ ]]
}

file_records() {
  local student officer_dt officer_ct supervisor line unit kind reason steps id step officer count=0
  student="$(token password judge_student "$DEMO_PASSWORD")"
  officer_dt="$(token password judge_officer "$DEMO_PASSWORD")"
  officer_ct="$(token password judge_officer2 "$DEMO_PASSWORD")"
  supervisor="$(token password judge_supervisor "$DEMO_PASSWORD")"
  [[ -n "$student" && -n "$officer_dt" && -n "$officer_ct" && -n "$supervisor" ]] \
    || die "an account could not sign in; the roles may need a minute to reach the token, try again"
  api "$supervisor" GET "/data/$MODEL/request?size=1"
  [[ "$API_STATUS" == 200 ]] || die "the model $MODEL cannot be read (HTTP $API_STATUS): $API_BODY"
  printf '%%PDF-1.4 sample evidence\n' >"$WORK/evidence.pdf"
  # The marker is read with the token of the supervisor, who sees every request.
  printf 'Authorization: Bearer %s\n' "$supervisor" >"$WORK/header"
  if already_filed; then
    echo "The sample records are filed already; nothing added."
    return 0
  fi
  while IFS='|' read -r unit kind reason steps; do
    [[ -n "$unit" ]] || continue
    api_json "$student" POST "/data/$MODEL/request" \
      "{\"reason\":\"$reason\",\"kind\":\"$kind\",\"department\":\"$unit\"}"
    [[ "$API_STATUS" == 200 ]] || die "a request was refused (HTTP $API_STATUS): $API_BODY"
    id="$(json_field id "$API_BODY")"
    officer="$officer_dt"
    [[ "$unit" == CTSV ]] && officer="$officer_ct"
    IFS=',' read -ra todo <<<"$steps"
    for step in ${todo[@]+"${todo[@]}"}; do
      case "$step" in
        file)
          printf 'Authorization: Bearer %s\n' "$student" >"$WORK/header"
          API_STATUS="$(curl -sS -o "$WORK/body" -w '%{http_code}' -H "@$WORK/header" \
            -F "file=@$WORK/evidence.pdf;type=application/pdf;filename=minh-chung.pdf" \
            "$BASE/api/v1/data/$MODEL/request/$id/files?field=evidence")" || true
          [[ "$API_STATUS" == 200 ]] || die "a file was refused (HTTP $API_STATUS): $(cat "$WORK/body")" ;;
        note=*)
          api_json "$student" POST "/data/$MODEL/request_note" "{\"request\":$id,\"note\":\"${step#note=}\"}"
          [[ "$API_STATUS" == 200 ]] || die "a note was refused (HTTP $API_STATUS): $API_BODY" ;;
        approve) act "$officer" "$id" approve "" ;;
        reject=*) act "$officer" "$id" reject "${step#reject=}" ;;
        return=*) act "$officer" "$id" return "${step#return=}" ;;
        resubmit) act "$student" "$id" resubmit "" ;;
        withdraw) act "$student" "$id" withdraw "" ;;
        *) die "unknown step $step" ;;
      esac
    done
    count=$((count + 1))
  done <<<"$RECORDS"
  printf 'Filed %s sample requests.\n' "$count"
}

# act <token> <id> <action> <note>
act() {
  local body="{}"
  [[ -z "$4" ]] || body="{\"note\":\"$4\"}"
  api_json "$1" POST "/data/$MODEL/request/$2/actions/$3" "$body"
  [[ "$API_STATUS" == 200 ]] || die "the action $3 on request $2 was refused (HTTP $API_STATUS): $API_BODY"
}

# --- Commands ----------------------------------------------------------------------------------

require_stack() {
  [[ -n "$(compose ps -q --status running backend keycloak)" ]] || die "DX-OS is not running; run dxos start"
  command -v curl >/dev/null 2>&1 || die "curl is required"
}

# Applies the one-stop template when no model of that name runs. The template comes from the API, so
# an installed copy needs nothing but the stack.
ensure_model() {
  local admin secret content
  secret="$(env_value DXOS_CLI_SECRET)"
  [[ -n "$secret" ]] || die "DXOS_CLI_SECRET is empty in $DEPLOY_DIR/.env"
  admin="$(token client_credentials dxos-cli "$secret")"
  [[ -n "$admin" ]] || die "Keycloak refused the client dxos-cli (see docs/OPERATIONS.md)"
  api "$admin" GET "/models/$MODEL/versions"
  if [[ "$API_STATUS" == 200 ]]; then
    echo "The model $MODEL runs already."
    return 0
  fi
  [[ "$API_STATUS" == 404 ]] || die "cannot read the versions of $MODEL (HTTP $API_STATUS): $API_BODY"
  api "$admin" GET /models/templates
  [[ "$API_STATUS" == 200 ]] || die "cannot read the templates (HTTP $API_STATUS)"
  # The entry of the template, then its file: \n and \" are the only escapes the file holds.
  content="$(tr '\n' ' ' <<<"$API_BODY" | sed 's/},{"/}\n{"/g' | grep "\"id\":\"$MODEL\"" | head -n1 \
    | sed -n 's/.*"content":"\(.*\)"[,}].*/\1/p' | sed 's/\\n/\n/g; s/\\"/"/g')"
  [[ -n "$content" ]] || die "the API has no template $MODEL"
  printf '%s\n' "$content" >"$WORK/model.yaml"
  api "$admin" POST /models/plan application/yaml "$WORK/model.yaml"
  [[ "$API_STATUS" == 200 ]] || die "the plan of $MODEL was refused (HTTP $API_STATUS): $API_BODY"
  api "$admin" POST "/models/apply?base=$(json_field fromVersion "$API_BODY")" application/yaml "$WORK/model.yaml"
  [[ "$API_STATUS" == 200 ]] || die "applying $MODEL failed (HTTP $API_STATUS): $API_BODY"
  echo "Applied the model $MODEL."
}

cmd_demo() {
  DEMO_PASSWORD="$PASSWORD_ARG"
  [[ -n "$DEMO_PASSWORD" ]] || DEMO_PASSWORD="$(od -An -N10 -tx1 /dev/urandom | tr -d ' \n')"
  (( ${#DEMO_PASSWORD} >= 8 )) || die "the password needs at least 8 characters"
  require_stack
  ensure_model
  export DEMO_PASSWORD
  keycloak_admin accounts DEMO_PASSWORD >/dev/null
  SEED_CLIENT_MADE=true
  keycloak_admin make-seed-client
  file_records
  cat <<EOF

Accounts for trying DX-OS (one password for all, shown only now; run dxos demo again for a new one):
  judge_student      student, files requests
  judge_officer      officer of the unit DAO_TAO
  judge_officer2     officer of the unit CTSV
  judge_supervisor   supervisor, reads every request
  password           $DEMO_PASSWORD
  sign in at         $BASE/
EOF
}

cmd_remove() {
  require_stack
  keycloak_admin remove
  echo "Removed the accounts: $USERS. The sample requests stay."
}

case "${1:-}" in
  "") cmd_demo ;;
  --password) PASSWORD_ARG="${2:?--password needs a value}"; (( $# == 2 )) || die "usage: dxos demo [--password PASSWORD] | dxos demo remove"; cmd_demo ;;
  --password=*) PASSWORD_ARG="${1#--password=}"; (( $# == 1 )) || die "usage: dxos demo [--password PASSWORD] | dxos demo remove"; cmd_demo ;;
  remove) (( $# == 1 )) || die "usage: dxos demo remove"; cmd_remove ;;
  *) die "usage: dxos demo [--password PASSWORD] | dxos demo remove" ;;
esac
