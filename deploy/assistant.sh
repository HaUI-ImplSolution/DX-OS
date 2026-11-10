#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Optional assistant lifecycle; Core's compose invocation stays independent.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
STATE="$PWD/data/assistant"
ENABLED="$STATE/enabled"
GENERATED="$STATE/runtime-compose.json"
OVERLAY="$PWD/docker-compose.assistant.yml"
BASE="$PWD/docker-compose.yml"
die() { echo "dxos assistant: $*" >&2; exit 1; }
env_value() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- || true; }
compose() {
  local files=(-f "$BASE" -f "$OVERLAY")
  [[ ! -f "$GENERATED" ]] || files+=(-f "$GENERATED")
  docker compose --project-directory "$PWD" "${files[@]}" "$@"
}
defaults() {
  [[ -f .env ]] || die "run make_env.sh first"
  local line key
  while IFS= read -r line; do
    if [[ "$line" == \#* ]]; then line="${line#\#}"; line="${line# }"; fi
    [[ "$line" == ASSISTANT_*=* ]] || continue
    key="${line%%=*}"
    if ! grep -q "^${key}=" .env; then
      [[ -z "$(tail -c1 .env)" ]] || echo >> .env
      printf '%s\n' "$line" >> .env
    fi
  done < .env.example
}
image() {
  local value; value="$(env_value ASSISTANT_IMAGE)"
  [[ "$value" =~ ^[a-zA-Z0-9][a-zA-Z0-9/_.:@-]*$ ]] || die "set ASSISTANT_IMAGE to an explicit image reference"
  printf '%s\n' "$value"
}
services() {
  if [[ -f "$GENERATED" ]]; then compose config --services | grep -E '^assistant-runtime-[123]$'; return; fi
  local count i; count="$(env_value ASSISTANT_RUNTIME_COUNT)"
  [[ "$count" =~ ^[123]$ ]] || die "ASSISTANT_RUNTIME_COUNT must be 1, 2 or 3"
  for ((i=1; i<=count; i++)); do printf 'assistant-runtime-%s\n' "$i"; done
}
prepare() {
  defaults
  if [[ -z "${DXOS_ASSISTANT_PUBLIC_URL:-}" ]]; then
    DXOS_ASSISTANT_PUBLIC_URL="$(env_value GATEWAY_PUBLIC_URL)"
    if [[ "$DXOS_ASSISTANT_PUBLIC_URL" == *'${'* ]]; then
      local host port
      host="$(env_value PUBLIC_HOST)"; port="$(env_value APISIX_HOST_PORT)"
      [[ -n "$host" && -n "$port" ]] || die "set the public gateway address before preparing the assistant"
      DXOS_ASSISTANT_PUBLIC_URL="http://$host:${port##*:}"
    fi
    export DXOS_ASSISTANT_PUBLIC_URL
  fi
  local build=false
  case "${1:-}" in --build) build=true ;; "") ;; *) die "usage: dxos assistant prepare [--build]" ;; esac
  if $build; then
    [[ -f ../assistant/Dockerfile ]] || die "building needs the source checkout; installed releases use their published image"
    compose build assistant-control
  elif ! docker image inspect "$(image)" >/dev/null 2>&1; then
    docker pull "$(image)"
  fi
  if [[ ! -d "$STATE" ]]; then
    docker run --rm --network none --read-only --user 0 --cap-drop ALL --cap-add CHOWN \
      --security-opt no-new-privileges:true -v "$STATE:/bootstrap" --entrypoint chown \
      "$(image)" "$(id -u):$(id -g)" /bootstrap
  fi
  chmod 700 "$STATE"
  docker run --rm --network none --user "$(id -u):$(id -g)" -e DXOS_ASSISTANT_PUBLIC_URL -v "$STATE:/bootstrap" \
    "$(image)" dist/setup.js prepare /bootstrap "$(env_value ASSISTANT_RUNTIME_COUNT)"
}
connect_core() {
  local control project network service container network_id
  control="$(compose ps -aq assistant-control)"
  project="$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$control")"
  for network in assistant-core assistant-ingress; do
    network_id="$(docker network ls -q --filter "label=com.docker.compose.project=$project" --filter "label=com.docker.compose.network=$network")"
    [[ -n "$network_id" ]] || die "the optional $network network is missing"
    network_id="$(docker network inspect -f '{{.Id}}' "$network_id")"
    local services=(backend keycloak)
    [[ "$network" != assistant-ingress ]] || services=(apisix)
    for service in "${services[@]}"; do
      container="$(compose ps -q "$service")"
      [[ -n "$container" ]] || die "start Core before enabling the assistant"
      container="$(docker inspect -f '{{.Id}}' "$container")"
      if ! docker inspect -f '{{range .NetworkSettings.Networks}}{{println .NetworkID}}{{end}}' "$container" | grep -Fxq "$network_id"; then
        docker network connect --alias "$service" "$network_id" "$container"
      fi
    done
  done
}
resume() {
  [[ -f "$ENABLED" ]] || return 0
  [[ -f "$GENERATED" ]] || die "run dxos assistant prepare first"
  local runtime_services=() service attempt
  mapfile -t runtime_services < <(services)
  compose up -d --no-build --no-deps "${runtime_services[@]}"
  for service in "${runtime_services[@]}"; do
    local ready=false
    for ((attempt=0; attempt<30; attempt++)); do
      if compose exec -T "$service" node -e 'fetch("http://127.0.0.1:18792/status",{headers:{Authorization:"Bearer "+process.env.ASSISTANT_RUNTIME_CONTROL_TOKEN}}).then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))' >/dev/null 2>&1; then ready=true; break; fi
      sleep 1
    done
    $ready || die "$service did not become ready; Core remains independent"
  done
  compose up --no-start --no-build --no-deps assistant-control
  connect_core
  compose up -d --no-build --no-deps assistant-control
  compose up -d --no-build --no-deps --force-recreate apisix-config
}
stop() {
  [[ -f "$GENERATED" ]] || return 0
  local runtime_services=(); mapfile -t runtime_services < <(services)
  compose stop -t 30 assistant-control "${runtime_services[@]}"
}
disable_route() {
  docker compose -f "$BASE" up --no-build --no-deps --force-recreate apisix-config || return 1
  local gateway project network
  gateway="$(docker compose -f "$BASE" ps -q --status running apisix)" || return 1
  [[ -n "$gateway" && -f "$GENERATED" ]] || return 0
  project="$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$gateway")" || return 1
  network="$(docker network ls -q --filter "label=com.docker.compose.project=$project" --filter label=com.docker.compose.network=default)" || return 1
  docker run --rm --network "$network" --read-only --cap-drop ALL --security-opt no-new-privileges:true "$(image)" --input-type=module -e '
    for (let i = 0; i < 20; i++) {
      try {
        const r = await fetch("http://apisix:9080/assistant-api/v1/features", {signal: AbortSignal.timeout(1500)});
        if (r.status < 500 && (!r.headers.get("content-type")?.includes("application/json") || (await r.json()).enabled === false)) process.exit(0);
      } catch {}
      await new Promise(resolve => setTimeout(resolve, 250));
    }
    process.exit(1);
  '
}
running() {
  [[ -f "$GENERATED" ]] || return 1
  [[ -n "$(compose ps -q --status running assistant-control)" ]]
}

case "${1:-status}" in
  prepare) shift; prepare "$@" ;;
  on)
    shift; prepare "$@"
    docker compose ps -q --status running keycloak | grep -q . || die "run dxos start first"
    ./assistant-sso.sh
    touch "$ENABLED"; resume
    echo "Open the DX-OS application and choose Personal assistant to link your own API and bot." ;;
  off) disable_route || die "The gateway did not confirm the route change. The assistant remains enabled; check gateway logs and retry dxos assistant off."
    stop; rm -f "$ENABLED"; echo "Assistant stopped; state and keys kept." ;;
  resume) resume ;;
  stop) stop ;;
  is-running) running ;;
  status)
    if [[ ! -f "$ENABLED" ]]; then echo "Assistant: off";
    elif [[ -f "$GENERATED" ]]; then
      runtime_services=(); mapfile -t runtime_services < <(services)
      compose ps assistant-control "${runtime_services[@]}"
    else echo "Assistant: preparation required"; fi ;;
  snapshot)
    [[ $# == 2 && "$2" == /* ]] || die "snapshot needs a private absolute destination"
    runtime_services=(); mapfile -t runtime_services < <(services)
    [[ -z "$(compose ps -q --status running assistant-control "${runtime_services[@]}")" ]] || die "stop the assistant before copying its state"
    mkdir -p "$2"; chmod 700 "$2"
    cp -a "$STATE/." "$2/"
    find "$2" -type d -exec chmod 700 {} +
    find "$2" -type f -exec chmod 600 {} + ;;
  *) die "usage: dxos assistant on|off|status|prepare [--build]" ;;
esac
