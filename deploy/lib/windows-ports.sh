# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# Sourced by dxos and doctor.sh. In WSL, asks Windows itself whether a published port of the stack answers there,
# because Docker can start a container while a Windows program holds that port exclusively and
# the shell in WSL then reaches the container and sees nothing wrong.
# The caller defines env_value (value of a key in deploy/.env).

# True in WSL when powershell.exe can be called.
windows_probe_available() {
  grep -qi microsoft /proc/version 2>/dev/null && command -v powershell.exe >/dev/null 2>&1 && command -v iconv >/dev/null 2>&1
}

# The address Windows connects to for a *_HOST_PORT value such as 9080 or 127.0.0.1:5432.
windows_probe_host() {
  local host=127.0.0.1
  [[ "$1" == *:* ]] && host="${1%:*}"
  [[ "$host" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ && "$host" != 0.0.0.0 ]] || host=127.0.0.1
  echo "$host"
}

# Prints the state of the port of KEY (APISIX_HOST_PORT or POSTGRES_HOST_PORT) as Windows sees it:
#   stack    what answers is the gateway (its status JSON) or PostgreSQL (an answer to SSLRequest)
#   foreign  something accepts the connection and is neither
#   free     nothing accepts the connection
#   unknown  the answer could not be read
windows_port_state() {
  local key="$1" value host port body script state
  value="$(env_value "$key")"
  port="${value##*:}"
  [[ "$port" =~ ^[0-9]{1,5}$ ]] || { echo unknown; return; }
  host="$(windows_probe_host "$value")"
  case "$key" in
    APISIX_HOST_PORT)
      body='$request = [Text.Encoding]::ASCII.GetBytes("GET /api/v1/system/status HTTP/1.0`r`nHost: '"$host"'`r`n`r`n")
$stream.Write($request, 0, $request.Length)
$text = (New-Object System.IO.StreamReader($stream)).ReadToEnd()
if ($text -match "X-RateLimit-Limit" -or $text -match """services""") { "stack" } else { "foreign" }' ;;
    POSTGRES_HOST_PORT)
      body='$request = [byte[]](0,0,0,8,4,210,22,47)
$stream.Write($request, 0, $request.Length)
$answer = $stream.ReadByte()
if ($answer -eq 83 -or $answer -eq 78) { "stack" } else { "foreign" }' ;;
    *) echo unknown; return ;;
  esac
  script='$ErrorActionPreference = "Stop"
$client = New-Object System.Net.Sockets.TcpClient
try {
  $pending = $client.BeginConnect("'"$host"'", '"$port"', $null, $null)
  if (-not $pending.AsyncWaitHandle.WaitOne(3000)) { "free"; exit }
  $client.EndConnect($pending)
} catch { "free"; exit }
try {
  $stream = $client.GetStream()
  $stream.ReadTimeout = 3000
'"$body"'
} catch { "foreign" } finally { $client.Close() }'
  state="$(powershell.exe -NoProfile -NonInteractive -EncodedCommand \
    "$(printf '%s' "$script" | iconv -f UTF-8 -t UTF-16LE | base64 -w0)" 2>/dev/null | tr -d '\r' | tail -n1)"
  case "$state" in stack|foreign|free) echo "$state" ;; *) echo unknown ;; esac
}

# After the stack is up: returns 1 and names the key to change when a program on Windows answers
# on a port of the stack in place of the stack. Does nothing outside WSL.
windows_ports_check() {
  local key state port status=0
  windows_probe_available || return 0
  for key in APISIX_HOST_PORT POSTGRES_HOST_PORT; do
    [[ -n "$(env_value "$key")" ]] || continue
    port="$(env_value "$key")"; port="${port##*:}"
    state="$(windows_port_state "$key")"
    case "$state" in
      foreign)
        echo "dxos: port $port of $key answers on Windows with a program outside this stack, so Windows cannot reach the stack; change $key in deploy/.env or stop that program, then run dxos start again" >&2
        status=1 ;;
      free)
        echo "dxos: warning: Windows could not connect to port $port of $key; the stack runs in WSL, but a browser on Windows may not reach it" >&2 ;;
    esac
  done
  return "$status"
}
