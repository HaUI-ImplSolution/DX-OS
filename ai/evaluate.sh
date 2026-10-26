#!/usr/bin/env bash
set -Eeuo pipefail

# Standalone local RAG evaluation lifecycle. Product Compose is never addressed here.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
COMPOSE_FILE="$SCRIPT_DIR/evaluation/docker-compose.yml"
PROJECT="${EVAL_PROJECT:-dx-rag-evaluation-runner}"
STATE_DIR="${EVAL_STATE_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/dx-os-rag-evaluation-runner}"
TIMEOUT="${EVAL_VIEW_TIMEOUT_SECONDS:-600}"
MODE="${1:---help}"

die() { printf 'evaluate.sh: %s\n' "$*" >&2; exit 2; }
usage() {
  cat <<'EOF'
Usage: ./ai/evaluate.sh <quality|performance|all|view|down|--help>

quality      collect, score and optionally publish the 42-question RAG baseline
performance  run the 18-trial HTTP retrieval benchmark
all          run quality then performance in one prepared environment
view         reopen the most recent published local artifacts
down         stop only this evaluation project and retain state/cache/results
EOF
}

case "$MODE" in --help|-h|"") usage; exit 0 ;; quality|performance|all|view|down) ;; *) usage >&2; exit 2 ;; esac

command -v docker >/dev/null 2>&1 || die "Docker is required"
docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required"
command -v curl >/dev/null 2>&1 || die "curl is required for the performance health check"
[[ "$(uname -s)" == Linux ]] || die "This runner currently supports Linux Docker Engine only"
[[ "$(uname -m)" == x86_64 || "$(uname -m)" == amd64 ]] || die "This runner currently supports Linux x86_64 only"

mkdir -p "$STATE_DIR/runs" "$STATE_DIR/cache"
STATE_DIR="$(cd -- "$STATE_DIR" && pwd -P)"
LOCK_DIR="$STATE_DIR/.lock"
ENV_FILE="$STATE_DIR/evaluation.env"
RUN_DIR=""
COMPOSE=(docker compose --project-name "$PROJECT" --file "$COMPOSE_FILE" --env-file "$ENV_FILE")

if [[ "$MODE" == down ]]; then
  [[ -f "$ENV_FILE" ]] || { printf 'No evaluation state found.\n'; exit 0; }
  "${COMPOSE[@]}" down --remove-orphans
  exit 0
fi

if [[ "$MODE" == view ]]; then
  [[ -t 0 ]] || die "view requires an interactive terminal"
  latest="$(find "$STATE_DIR/runs" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1)"
  [[ -n "$latest" ]] || die "No evaluation runs found in $STATE_DIR/runs"
  printf 'Latest artifacts: %s\n' "$latest"
  printf 'Press Enter to stop the evaluation UI, or wait %s seconds.\n' "$TIMEOUT"
  IFS= read -r -t "$TIMEOUT" _ || true
  exit 0
fi

if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  die "another evaluation session owns $STATE_DIR (finish it in its terminal or use down)"
fi
printf '%s\n' "$$" > "$LOCK_DIR/pid"
cleanup() {
  status=$?
  set +e
  if [[ -f "$ENV_FILE" ]]; then "${COMPOSE[@]}" down --remove-orphans >/dev/null 2>&1; fi
  rmdir "$LOCK_DIR" >/dev/null 2>&1 || true
  exit "$status"
}
trap cleanup EXIT INT TERM HUP

if [[ ! -f "$ENV_FILE" ]]; then
  umask 077
  postgres_password="$(openssl rand -hex 24 2>/dev/null || od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
  readonly_password="$(openssl rand -hex 24 2>/dev/null || od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
  api_key="$(openssl rand -hex 24 2>/dev/null || od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
  {
    printf 'EVAL_PROJECT=%q\n' "$PROJECT"
    printf 'EVAL_STATE_DIR=%q\n' "$STATE_DIR"
    printf 'EVAL_ARTIFACT_DIR=%q\n' "$STATE_DIR/runs"
    printf 'EVAL_POSTGRES_PASSWORD=%q\n' "$postgres_password"
    printf 'EVAL_READONLY_PASSWORD=%q\n' "$readonly_password"
    printf 'EVAL_AI_SERVICE_API_KEY=%q\n' "$api_key"
    printf 'EVAL_VIEW_TIMEOUT_SECONDS=%q\n' "$TIMEOUT"
    printf 'EVAL_POSTGRES_DB=dxos_ai_vector_db\nEVAL_POSTGRES_USER=eval_admin\nEVAL_READONLY_USER=eval_ro\n'
    printf 'EVAL_EMBEDDING_MODEL=sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2\n'
    printf 'EVAL_AI_PORT=18765\nEVAL_LANGFUSE_PORT=13000\nEVAL_LOAD_PORT=18088\nK6_VERSION=0.49.0\n'
  } > "$ENV_FILE"
fi

RUN_DIR="$STATE_DIR/runs/$(date -u +%Y%m%dT%H%M%SZ)-$MODE"
mkdir -p "$RUN_DIR"
# Compose mounts the whole runs directory; the helper writes to the current run explicitly.
printf 'EVAL_INGEST_RECORD=/artifacts/%s/ingest.json\n' "$(basename "$RUN_DIR")" >> "$ENV_FILE"

"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" build prepare >/dev/null
"${COMPOSE[@]}" run --rm prepare

run_quality() {
  printf 'Running quality evaluation...\n'
  "${COMPOSE[@]}" run --rm evaluator scripts/evaluate_rag.py collect \
    --ingest-record "/artifacts/$(basename "$RUN_DIR")/ingest.json" \
    --output "/artifacts/$(basename "$RUN_DIR")/quality"
  "${COMPOSE[@]}" run --rm evaluator scripts/evaluate_rag.py score \
    --collect "/artifacts/$(basename "$RUN_DIR")/quality/collect.json" \
    --output "/artifacts/$(basename "$RUN_DIR")/quality"
  "${COMPOSE[@]}" run --rm evaluator scripts/evaluate_rag.py publish \
    --collect "/artifacts/$(basename "$RUN_DIR")/quality/collect.json" \
    --score-file "/artifacts/$(basename "$RUN_DIR")/quality/score.json" --dry-run
}

run_performance() {
  printf 'Starting isolated AI service for performance evaluation...\n'
  "${COMPOSE[@]}" up -d ai >/dev/null
  until curl -fsS "http://127.0.0.1:${EVAL_LOAD_PORT:-18088}/api/v1/disease/health" >/dev/null; do sleep 2; done
  ai_id="$("${COMPOSE[@]}" ps -q ai)"
  vector_id="$("${COMPOSE[@]}" ps -q vector)"
  "${COMPOSE[@]}" exec -T ai python scripts/benchmark_rag.py serve \
    --host 0.0.0.0 --port 8000 \
    --ingest-record "/artifacts/$(basename "$RUN_DIR")/ingest.json" \
    --output "/artifacts/$(basename "$RUN_DIR")/server.json" >/tmp/dx-rag-eval-server.log 2>&1 &
  sleep 5
  "${COMPOSE[@]}" run --rm evaluator scripts/benchmark_rag.py run \
    --k6 /usr/local/bin/k6 --output "/artifacts/$(basename "$RUN_DIR")/performance" \
    --server-record "/artifacts/$(basename "$RUN_DIR")/server.json" \
    --container "$ai_id" --container "$vector_id" --seconds 30 --repeats 3
}

case "$MODE" in
  quality) run_quality ;;
  performance) run_performance ;;
  all) run_quality; run_performance ;;
esac
printf 'Artifacts: %s\n' "$RUN_DIR"
if [[ -t 0 ]]; then
  printf 'Press Enter to clean up, or wait %s seconds.\n' "$TIMEOUT"
  IFS= read -r -t "$TIMEOUT" _ || true
fi
