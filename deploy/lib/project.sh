#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# The project commands of dxos.
# Sourced by dxos, which provides die, api, pretty, json_field and json_text.

# Puts what a path names in a project, or takes it out when the project is "-".
set_project() {
  local work value
  if [[ "$2" == - ]]; then
    value=null
  else
    [[ "$2" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] || die "not a project name: $2 (lower case letters, digits, - and _; - takes it out of its project)"
    value="\"$2\""
  fi
  work="$(mktemp)"
  printf '{"project":%s}' "$value" >"$work"
  api PUT "$1" "$work" application/json
  rm -f "$work"
  if [[ "$API_STATUS" != 200 ]]; then pretty "$API_BODY"; return 1; fi
}

project_text() {
  if [[ "$1" == - ]]; then echo "$2 is in no project now."; else echo "$2 is in the project $1 now."; fi
}

# The Keycloak id of an account, found by its exact username.
user_id_of() {
  local username="${1,,}" found id
  [[ "$username" =~ ^[a-z0-9._@-]{1,100}$ ]] || die "not a username: $1"
  api GET "/users?search=$username&size=50"
  [[ "$API_STATUS" == 200 ]] || { pretty "$API_BODY"; return 1; }
  found="$(tr '{' '\n' <<<"$API_BODY" | grep "\"username\":\"$username\"" | head -n1 || true)"
  id="$(json_field id "$found")"
  [[ -n "$id" ]] || die "no account has the username $username"
  printf '%s\n' "$id"
}

cmd_project_member() {
  local action="${1:-}" project user id level=MEMBER work
  case "$action" in
    list)
      (( $# == 2 )) || die "usage: dxos project member list <project>"
      [[ "$2" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] || die "not a project name: $2"
      api GET "/projects/$2/members"
      pretty "$API_BODY"
      [[ "$API_STATUS" == 200 ]] || return 1 ;;
    add)
      shift
      project="${1:-}"
      user="${2:-}"
      [[ "$project" =~ ^[a-z][a-z0-9_-]{0,39}$ && -n "$user" ]] || die "usage: dxos project member add <project> <username> [--admin]"
      shift 2
      while (( $# )); do
        case "$1" in
          --admin) level=ADMIN; shift ;;
          *) die "unknown option: $1" ;;
        esac
      done
      id="$(user_id_of "$user")" || return 1
      work="$(mktemp)"
      printf '{"level":"%s"}' "$level" >"$work"
      api PUT "/projects/$project/members/$id" "$work" application/json
      rm -f "$work"
      if [[ "$API_STATUS" != 200 ]]; then pretty "$API_BODY"; return 1; fi
      echo "${user,,} is now ${level,,} of the project $project." ;;
    remove)
      (( $# == 3 )) || die "usage: dxos project member remove <project> <username>"
      [[ "$2" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] || die "not a project name: $2"
      id="$(user_id_of "$3")" || return 1
      api DELETE "/projects/$2/members/$id"
      if [[ "$API_STATUS" != 200 ]]; then pretty "$API_BODY"; return 1; fi
      echo "${3,,} is no longer in the project $2; the roles it was given stay until they are removed." ;;
    *) die "usage: dxos project member list <project> | add <project> <username> [--admin] | remove <project> <username>" ;;
  esac
}

cmd_project() {
  local action="${1:-}" name label="" description="" work
  case "$action" in
    member) shift; cmd_project_member "$@" ;;
    list)
      (( $# == 1 )) || die "usage: dxos project list"
      api GET /projects
      pretty "$API_BODY"
      [[ "$API_STATUS" == 200 ]] || return 1 ;;
    add)
      shift
      name="${1:-}"
      [[ "$name" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] || die "usage: dxos project add <name> [--label TEXT] [--description TEXT] (name: lower case letters, digits, - and _)"
      shift
      while (( $# )); do
        case "$1" in
          --label) label="${2:?--label needs a text}"; shift 2 ;;
          --label=*) label="${1#--label=}"; shift ;;
          --description) description="${2:?--description needs a text}"; shift 2 ;;
          --description=*) description="${1#--description=}"; shift ;;
          *) die "unknown option: $1" ;;
        esac
      done
      work="$(mktemp)"
      printf '{"name":"%s","label":"%s","description":"%s"}' "$name" "$(json_text "${label:-$name}")" "$(json_text "$description")" >"$work"
      api POST /projects "$work" application/json
      rm -f "$work"
      if [[ "$API_STATUS" != 200 ]]; then pretty "$API_BODY"; return 1; fi
      echo "Created the project $name." ;;
    remove)
      (( $# == 2 )) || die "usage: dxos project remove <name>"
      [[ "$2" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] || die "not a project name: $2"
      api DELETE "/projects/$2"
      if [[ "$API_STATUS" != 200 ]]; then pretty "$API_BODY"; return 1; fi
      echo "Removed the project $2." ;;
    *) die "usage: dxos project list | add <name> [--label TEXT] [--description TEXT] | remove <name> | member list|add|remove" ;;
  esac
}
