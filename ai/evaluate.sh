#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
set -Eeuo pipefail
umask 077
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
MODE="${1:---help}"
die() { printf 'evaluate.sh: %s\n' "$*" >&2; exit 2; }
usage() {
  cat <<'HELP'
Usage: ./ai/evaluate.sh <quality|performance|all|view|down|--self-check|--help>
  quality      Compare retrieval strategies, score and publish to local Langfuse
  performance  Run 36 HTTP retrieval trials with k6 and Docker resource samples
  all          Run quality then performance, sharing preparation
  view         Reopen saved Langfuse history and artifact reports
  down         Recover an interrupted evaluation session; keep data and cache
  --self-check Check lifecycle guards without creating Docker resources
Linux x86_64, Bash, local Docker Engine and Docker Compose are required.
Results/state: ${XDG_DATA_HOME:-$HOME/.local/share}/dx-os-rag-evaluation-runner
Override EVAL_STATE_DIR for another location; defaults: ai/evaluation/.env.example.
After publishing, Enter or 600 seconds stops evaluation services automatically.
Without an interactive stdin, measurement modes publish and stop immediately.
HELP
}

self_check() {
  local temp rc
  temp="$(mktemp -d)"
  trap 'rm -rf -- "$temp"' RETURN
  EVAL_STATE_DIR="$temp/unused" bash "$SCRIPT_DIR/evaluate.sh" --help >/dev/null
  [[ ! -e "$temp/unused" ]] || die 'help created state'
  rc=0; bash "$SCRIPT_DIR/evaluate.sh" bad-mode >/dev/null 2>&1 || rc=$?
  [[ "$rc" == 2 ]] || die 'invalid mode accepted'
  rc=0; EVAL_PROJECT=deploy bash "$SCRIPT_DIR/evaluate.sh" down >/dev/null 2>&1 || rc=$?
  [[ "$rc" == 2 ]] || die 'product project accepted'
  rc=0; EVAL_STATE_DIR="$REPO_DIR" bash "$SCRIPT_DIR/evaluate.sh" down >/dev/null 2>&1 || rc=$?
  [[ "$rc" == 2 ]] || die 'repository state accepted'
  ln -s "$REPO_DIR" "$temp/linked"
  rc=0; EVAL_STATE_DIR="$temp/linked" bash "$SCRIPT_DIR/evaluate.sh" down >/dev/null 2>&1 || rc=$?
  [[ "$rc" == 2 ]] || die 'symlink repository state accepted'
  rc=0; EVAL_STATE_DIR="$temp/unused" bash "$SCRIPT_DIR/evaluate.sh" view </dev/null >/dev/null 2>&1 || rc=$?
  [[ "$rc" == 2 && ! -e "$temp/unused" ]] || die 'noninteractive view created state'
  mkdir "$temp/bin"
  cat > "$temp/bin/docker" <<'MOCK'
#!/usr/bin/env bash
if [[ "$1" == context ]]; then printf 'unix:///var/run/docker.sock\n'
elif [[ "$1" == info ]]; then
  if [[ "$*" == *DockerRootDir* ]]; then printf '/\n'; else printf 'linux\n'; fi
elif [[ "$1" == compose ]]; then
  [[ " $* " != *' --help '* ]] || { printf '%s\n' --wait-timeout; exit; }
  for item in "$@"; do
    if [[ "$item" == down ]]; then printf 'down\n' >> "$SELF_CHECK_EVENTS"; exit 0; fi
    if [[ "$item" == collect && "${SELF_CHECK_FAIL:-0}" == 1 ]]; then exit 23; fi
  done
fi
MOCK
  chmod +x "$temp/bin/docker"
  local fail expected actual
  for fail in 0 1; do
    expected=0; [[ "$fail" == 0 ]] || expected=23
    actual=0
    PATH="$temp/bin:$PATH" SELF_CHECK_EVENTS="$temp/events-$fail" SELF_CHECK_FAIL="$fail" \
      EVAL_STATE_DIR="$temp/state-$fail" EVAL_PROJECT="dx-rag-evaluation-runner-selfcheck-$$" \
      EVAL_MIN_AVAILABLE_MIB=0 EVAL_MIN_DISK_MIB=0 \
      bash "$SCRIPT_DIR/evaluate.sh" quality </dev/null > "$temp/output" 2>&1 || actual=$?
    [[ "$actual" == "$expected" && "$(cat "$temp/events-$fail")" == down ]] || die 'cleanup or original error code lost'
  done
  printf 'Runner guard/cleanup self-check: PASS\n'
}
case "$MODE" in
  --help|-h) usage; exit 0 ;;
  --self-check) self_check; exit 0 ;;
  quality|performance|all|view|down) [[ $# == 1 ]] || die 'expected one mode' ;;
  *) usage >&2; exit 2 ;;
esac
[[ "$MODE" != view || -t 0 ]] || die 'view requires an interactive terminal'
[[ "$(uname -sm)" == 'Linux x86_64' ]] || die 'Linux x86_64 Docker Engine is required'
# Read data, never execute dotenv as shell code. Explicit caller settings win.
load_env() {
  local key value
  while IFS='=' read -r key value; do
    [[ "$key" =~ ^(EVAL_[A-Z_]+|K6_VERSION)$ ]] || continue
    [[ -v "$key" ]] || export "$key=$value"
  done < "$1"
}
STATE_DIR="$(realpath -m -- "${EVAL_STATE_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/dx-os-rag-evaluation-runner}")"
[[ "$STATE_DIR" != "$REPO_DIR" && "$STATE_DIR" != "$REPO_DIR/"* && "$STATE_DIR" != / && "$STATE_DIR" != "$HOME" ]] || die 'state must be a dedicated directory outside the checkout'
[[ "$STATE_DIR$REPO_DIR" != *'$'* && "$STATE_DIR$REPO_DIR" != *$'\n'* && "$STATE_DIR$REPO_DIR" != *$'\r'* ]] || die 'paths containing dollar signs or newlines are unsupported'
[[ ! -L "$STATE_DIR/evaluation.env" && ! -L "$STATE_DIR/owner" ]] || die 'state config must not be a symlink'
if [[ -f "$STATE_DIR/evaluation.env" ]]; then load_env "$STATE_DIR/evaluation.env"; fi
load_env "$SCRIPT_DIR/evaluation/.env.example"
[[ "$EVAL_PROJECT" =~ ^dx-rag-evaluation-runner(-[a-z0-9]+)*$ ]] || die 'project must belong to dx-rag-evaluation-runner namespace'
[[ "$EVAL_TOOL_IMAGE" =~ ^dxos-rag-evaluation-tool:[a-zA-Z0-9_.-]+$ ]] || die 'the tool image must belong to the evaluation namespace'
[[ "$EVAL_EMBEDDING_MODEL" == "$(sed -n 's/^EVAL_EMBEDDING_MODEL=//p' "$SCRIPT_DIR/evaluation/.env.example")" ]] || die 'embedding source must match this baseline'
[[ "$EVAL_VIEW_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ && "$EVAL_START_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] || die 'timeouts must be positive integers'
if [[ "$MODE" == down && ! -d "$STATE_DIR" ]]; then printf 'No evaluation state found.\n'; exit 0; fi
for executable in docker flock realpath stat awk df tar; do command -v "$executable" >/dev/null || die "$executable is required"; done
endpoint="${DOCKER_HOST:-$(docker context inspect --format '{{.Endpoints.docker.Host}}')}"
[[ "$endpoint" == unix:///var/run/docker.sock ]] || die 'use the local Docker Engine socket /var/run/docker.sock'
# No Docker context changes or daemon configuration are made by this tool.
[[ "$(docker info --format '{{.OSType}}')" == linux ]] || die 'local Docker Engine is unavailable'
unset COMPOSE_FILE COMPOSE_PROJECT_NAME COMPOSE_PROFILES COMPOSE_ENV_FILES
docker compose version >/dev/null || die 'Docker Compose is required'
docker compose up --help | grep -q -- --wait-timeout || die 'Compose must support --wait-timeout'
[[ ! -d "$STATE_DIR" || ( "$(stat -c %u "$STATE_DIR")" == "$(id -u)" && ! -L "$STATE_DIR/runs" && ! -L "$STATE_DIR/cache" ) ]] || die 'state must be owned by the caller with local cache/runs directories'
mkdir -p -- "$STATE_DIR"; chmod 700 "$STATE_DIR"
lock_root="/tmp/dx-rag-evaluation-$(id -u)"
[[ ! -L "$lock_root" ]] || die 'unsafe lock directory'
mkdir -p "$lock_root"
[[ "$(stat -c %u "$lock_root")" == "$(id -u)" ]] || die 'unsafe lock owner'
chmod 700 "$lock_root"
exec 9>"$STATE_DIR/session.lock"
flock -n 9 || die 'another session owns this state; use Enter/Ctrl+C in its terminal'
exec 8>"$lock_root/$EVAL_PROJECT.lock"
flock -n 8 || die 'another session owns this project; use Enter/Ctrl+C in its terminal'
owner="$EVAL_PROJECT|$STATE_DIR"
if [[ -f "$STATE_DIR/owner" ]]; then
  [[ "$(cat "$STATE_DIR/owner")" == "$owner" ]] || die 'state ownership mismatch'
else
  [[ "$MODE" != down && "$MODE" != view ]] || die 'state ownership marker missing'
  printf '%s\n' "$owner" > "$STATE_DIR/owner"
fi
# Validate every existing project resource, including volumes, before any down/up.
for kind in container volume network; do
  while IFS= read -r resource; do
    [[ -z "$resource" ]] && continue
    if [[ "$kind" == container ]]; then label_path='.Config.Labels'; else label_path='.Labels'; fi
    label="$(docker "$kind" inspect --format "{{index $label_path \"io.dxos.rag-evaluation.state\"}}" "$resource")"
    [[ "$label" == "$STATE_DIR" ]] || die "refusing foreign $kind in evaluation project"
  done < <(docker "$kind" ls -q --filter "label=com.docker.compose.project=$EVAL_PROJECT")
done
mkdir -p "$STATE_DIR/runs" "$STATE_DIR/cache"
export EVAL_STATE_DIR="$STATE_DIR" EVAL_REPO_DIR="$REPO_DIR" EVAL_UID="$(id -u)" EVAL_GID="$(id -g)"
export EVAL_DOCKER_GID="$(stat -c %g /var/run/docker.sock)" EVAL_RUN=recovery
# Random secrets are persisted only in private state; no service keys are printed.
random() { od -An -N32 -tx1 /dev/urandom | tr -d ' \n'; }
for key in POSTGRES_PASSWORD READONLY_PASSWORD AI_SERVICE_API_KEY LF_DB_PASSWORD LF_SALT LF_ENCRYPTION_KEY LF_AUTH_SECRET CLICKHOUSE_PASSWORD REDIS_PASSWORD STORAGE_PASSWORD LOGIN_PASSWORD LF_PUBLIC_KEY LF_SECRET_KEY; do
  key="EVAL_$key"
  [[ -v "$key" ]] || export "$key=$(random)"
done
# Save config atomically. Compose interpolation cannot interpret generated secrets.
config="$STATE_DIR/evaluation.env"
while IFS= read -r key; do
  [[ "$key" =~ ^(EVAL_[A-Z_]+|K6_VERSION)$ ]] || continue
  value="${!key}"
  [[ "$value" != *'$'* && "$value" != *$'\n'* && "$value" != *$'\r'* && "$value" != *'"'* && "$value" != *"'"* ]] || die "unsupported characters in $key"
  printf '%s=%s\n' "$key" "$value"
done < <(compgen -e | sort) > "$config.tmp"
mv -- "$config.tmp" "$config"; chmod 600 "$config"
export COMPOSE_PARALLEL_LIMIT=1
COMPOSE=(docker compose --project-name "$EVAL_PROJECT" --file "$SCRIPT_DIR/evaluation/docker-compose.yml" --env-file "$config")
RUN_DIR=''; child=''; cleaning=0
run() { "$@" & child=$!; local rc=0; wait "$child" || rc=$?; child=''; return "$rc"; }
cleanup() {
  local rc=$? clean_rc=0
  (( cleaning == 0 )) || return
  cleaning=1
  trap - EXIT INT TERM HUP
  set +e
  if [[ -n "$child" ]]; then kill -TERM "$child" 2>/dev/null; wait "$child" 2>/dev/null; fi
  if [[ -n "$RUN_DIR" ]]; then
    "${COMPOSE[@]}" logs --no-color > "$RUN_DIR/services.log" 2>&1 || true
  fi
  "${COMPOSE[@]}" down --remove-orphans --timeout 10 > "$STATE_DIR/cleanup.log" 2>&1 || clean_rc=$?
  if (( clean_rc )); then
    printf 'Cleanup incomplete; see %s/cleanup.log; retry ./ai/evaluate.sh down\n' "$STATE_DIR" >&2
    (( rc != 0 )) || rc=1
  else
    printf 'Evaluation stopped; cache, account, volumes and artifacts retained.\n'
  fi
  if [[ -n "$RUN_DIR" ]]; then
    printf '{"exit_code":%d,"cleanup_exit_code":%d}\n' "$rc" "$clean_rc" > "$RUN_DIR/status.json"
  fi
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP
"${COMPOSE[@]}" config --quiet
if [[ "$MODE" == down ]]; then exit 0; fi
if [[ "$MODE" == view ]]; then
  [[ -n "$(find "$STATE_DIR/runs" -mindepth 2 -name collect.json -o -mindepth 2 -name benchmark.json)" ]] || die 'no saved evaluation artifacts; run quality or performance first'
fi
[[ "$EVAL_AI_PORT" =~ ^[0-9]+$ && "$EVAL_LANGFUSE_PORT" =~ ^[0-9]+$ && "$EVAL_REPORT_PORT" =~ ^[0-9]+$ ]] || die 'ports must be integers'
[[ "$EVAL_AI_PORT" != "$EVAL_LANGFUSE_PORT" && "$EVAL_AI_PORT" != "$EVAL_REPORT_PORT" && "$EVAL_LANGFUSE_PORT" != "$EVAL_REPORT_PORT" ]] || die 'ports must be distinct'
for port in "$EVAL_AI_PORT" "$EVAL_LANGFUSE_PORT" "$EVAL_REPORT_PORT"; do
  (( port > 1024 && port < 65536 && port != 5432 )) || die 'invalid evaluation port'
  # Linux /dev/tcp checks a listener without creating or changing it.
  if (exec 7<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then die "port $port is already occupied"; fi
done
[[ "$EVAL_MIN_AVAILABLE_MIB" =~ ^[0-9]+$ && "$EVAL_MIN_DISK_MIB" =~ ^[0-9]+$ && "$EVAL_MIN_COLD_DISK_MIB" =~ ^[0-9]+$ ]] || die 'resource minima must be integers'
required_disk="$EVAL_MIN_DISK_MIB"
for image in "$EVAL_TOOL_IMAGE" "$EVAL_STORAGE_IMAGE" "$EVAL_POSTGRES_IMAGE" "$EVAL_LANGFUSE_IMAGE" "$EVAL_WORKER_IMAGE" "$EVAL_CLICKHOUSE_IMAGE" "$EVAL_REDIS_IMAGE"; do
  if ! docker image inspect "$image" >/dev/null 2>&1; then required_disk="$EVAL_MIN_COLD_DISK_MIB"; break; fi
done
available="$(awk '/MemAvailable:/ {print int($2/1024)}' /proc/meminfo)"
disk="$(df -Pm "$STATE_DIR" | awk 'NR==2 {print $4}')"
docker_disk="$(df -Pm "$(docker info --format '{{.DockerRootDir}}')" | awk 'NR==2 {print $4}')"
(( available >= EVAL_MIN_AVAILABLE_MIB && disk >= required_disk && docker_disk >= required_disk )) || die 'insufficient available RAM/disk; stop unused services or free space first'
# Reuse tagged images without contacting registries on warm runs.
ensure_image() {
  docker image inspect "$1" >/dev/null 2>&1 || run docker pull "$1"
}
ensure_image "$EVAL_POSTGRES_IMAGE"
context="$STATE_DIR/build/tool"; mkdir -p "$context"
rebuild=0
for file in requirements.txt requirements.lock requirements-eval.txt; do
  cmp -s "$SCRIPT_DIR/$file" "$context/$file" || rebuild=1
  cp "$SCRIPT_DIR/$file" "$context/$file"
done
cmp -s "$SCRIPT_DIR/evaluation/Dockerfile" "$context/Dockerfile" || rebuild=1
cp "$SCRIPT_DIR/evaluation/Dockerfile" "$context/Dockerfile"
[[ -f "$context/k6-version" && "$(cat "$context/k6-version")" == "$K6_VERSION" ]] || rebuild=1
printf '%s\n' "$K6_VERSION" > "$context/k6-version"
[[ -f "$context/built" ]] || rebuild=1
if (( rebuild )) || ! docker image inspect "$EVAL_TOOL_IMAGE" >/dev/null 2>&1; then
  rm -f "$context/built"
  base_image=python:3.11-slim
  for candidate in "$EVAL_TOOL_IMAGE" dxos-rag-evaluation-tool:2; do
    if docker image inspect "$candidate" >/dev/null 2>&1; then base_image="$candidate"; break; fi
  done
  run docker build --build-arg "EVAL_BASE_IMAGE=$base_image" --build-arg "K6_VERSION=$K6_VERSION" -t "$EVAL_TOOL_IMAGE" "$context"
  touch "$context/built"
fi
if [[ "$MODE" != view ]]; then
  export EVAL_RUN="$(date -u +%Y%m%dT%H%M%S)-$$-$MODE"
  RUN_DIR="$STATE_DIR/runs/$EVAL_RUN"; mkdir "$RUN_DIR"
  printf 'Mode: %s\nArtifacts: %s\n' "$MODE" "$RUN_DIR"
  printf 'mode=%s\nembedding_source=%s\nk6=%s\nAI CPUs=%s memory=%s\n' "$MODE" "$EVAL_EMBEDDING_MODEL" "$K6_VERSION" "$EVAL_AI_CPUS" "$EVAL_AI_MEMORY" > "$RUN_DIR/config.txt"
  cp "$REPO_DIR/data/rag/evaluation/metadata.json" "$RUN_DIR/dataset.json"
  run "${COMPOSE[@]}" up -d --wait --wait-timeout "$EVAL_START_TIMEOUT_SECONDS" vector
  run "${COMPOSE[@]}" run --rm --no-deps -T prepare
  if [[ "$MODE" == quality || "$MODE" == all ]]; then
    for strategy in legacy bm25 hybrid hybrid_rerank; do
      run "${COMPOSE[@]}" run --rm --no-deps -T evaluator scripts/evaluate_rag.py collect --strategy "$strategy" --ingest-record "/artifacts/$EVAL_RUN/ingest.json" --output "/artifacts/$EVAL_RUN/quality-$strategy-$EVAL_RUN"
      run "${COMPOSE[@]}" run --rm --no-deps -T evaluator scripts/evaluate_rag.py score --collect "/artifacts/$EVAL_RUN/quality-$strategy-$EVAL_RUN/collect.json" --output "/artifacts/$EVAL_RUN/quality-$strategy-$EVAL_RUN"
    done
    run "${COMPOSE[@]}" run --rm --no-deps -T evaluator evaluation/prepare.py compare-quality "/artifacts/$EVAL_RUN"
    run "${COMPOSE[@]}" run --rm --no-deps -T evaluator scripts/evaluate_scope.py --output "/artifacts/$EVAL_RUN/scope.json"
  fi
  if [[ "$MODE" == performance || "$MODE" == all ]]; then
    for strategy in legacy hybrid_rerank; do
      export EVAL_CHAT_STRATEGY="$strategy"
      run "${COMPOSE[@]}" up -d --force-recreate --wait --wait-timeout "$EVAL_START_TIMEOUT_SECONDS" ai
      ai_id="$(docker inspect --format '{{.Name}}' "$("${COMPOSE[@]}" ps -q ai)")"
      vector_id="$(docker inspect --format '{{.Name}}' "$("${COMPOSE[@]}" ps -q vector)")"
      ai_id="${ai_id#/}"; vector_id="${vector_id#/}"
      run "${COMPOSE[@]}" run --rm --no-deps -T sampler scripts/benchmark_rag.py run --k6 /usr/local/bin/k6 --output "/artifacts/$EVAL_RUN/performance-$strategy-$EVAL_RUN" --server-record "/artifacts/$EVAL_RUN/server-$strategy.json" --container "$ai_id" --container "$vector_id" --seconds "$EVAL_BENCH_SECONDS" --repeats "$EVAL_BENCH_REPEATS"
      run "${COMPOSE[@]}" run --rm --no-deps -T sampler evaluation/prepare.py check-benchmark "/artifacts/$EVAL_RUN/performance-$strategy-$EVAL_RUN"
      run "${COMPOSE[@]}" stop ai
    done
  fi
  run "${COMPOSE[@]}" stop ai vector
fi
# Start the UI only after measurement, so it does not compete with measured services.
for image in "$EVAL_LANGFUSE_IMAGE" "$EVAL_WORKER_IMAGE" "$EVAL_CLICKHOUSE_IMAGE" "$EVAL_REDIS_IMAGE"; do ensure_image "$image"; done
ensure_image "$EVAL_STORAGE_IMAGE"
run "${COMPOSE[@]}" up -d --wait --wait-timeout "$EVAL_START_TIMEOUT_SECONDS" lf-postgres clickhouse redis storage
run "${COMPOSE[@]}" up -d --wait --wait-timeout "$EVAL_START_TIMEOUT_SECONDS" langfuse-worker langfuse-web reports
if [[ "$MODE" != view ]]; then
  if [[ "$MODE" == quality || "$MODE" == all ]]; then
    run "${COMPOSE[@]}" run --rm --no-deps -T publisher scripts/evaluate_scope.py --publish "/artifacts/$EVAL_RUN/scope.json"
    for strategy in legacy bm25 hybrid hybrid_rerank; do
      run "${COMPOSE[@]}" run --rm --no-deps -T publisher scripts/evaluate_rag.py publish --collect "/artifacts/$EVAL_RUN/quality-$strategy-$EVAL_RUN/collect.json" --score-file "/artifacts/$EVAL_RUN/quality-$strategy-$EVAL_RUN/score.json"
    done
  fi
  if [[ "$MODE" == performance || "$MODE" == all ]]; then
    for strategy in legacy hybrid_rerank; do
      run "${COMPOSE[@]}" run --rm --no-deps -T publisher scripts/benchmark_rag.py publish --output "/artifacts/$EVAL_RUN/performance-$strategy-$EVAL_RUN"
    done
  fi
fi
printf 'Langfuse: http://localhost:%s\nReports: http://localhost:%s/\nLogin: %s\nPassword: %s\nState: %s\n' "$EVAL_LANGFUSE_PORT" "$EVAL_REPORT_PORT" "$EVAL_LOGIN_EMAIL" "$EVAL_LOGIN_PASSWORD" "$STATE_DIR"
printf 'Experiments replay saved results; trace duration is publishing time.\n'
if [[ -t 0 ]]; then
  printf 'Press Enter to stop, or wait %s seconds.\n' "$EVAL_VIEW_TIMEOUT_SECONDS"
  # A timed read is interrupted by the signal traps; no polling background timer.
  IFS= read -r -t "$EVAL_VIEW_TIMEOUT_SECONDS" _ || true
else
  printf 'No interactive terminal; stopping now. Reopen later with ./ai/evaluate.sh view\n'
fi
