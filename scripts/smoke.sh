#!/usr/bin/env bash
#
# End-to-end smoke test for a live Gatekeeper deployment (doc/scope.md §12).
#
#   scripts/smoke.sh <base-url> [bootstrap-token]
#
# Walkthrough: bootstrap a throwaway tenant -> log in as its admin -> create a role ->
# grant flag:write -> create a member user -> assign the role -> create a flag at 50%
# rollout -> evaluate it -> whitelist the member -> re-evaluate and assert the flip to true.
#
# Env:
#   BOOTSTRAP_TOKEN   bootstrap token (or pass as $2)
#
set -euo pipefail

BASE_URL="${1:?usage: smoke.sh <base-url> [bootstrap-token]}"
BASE_URL="${BASE_URL%/}"
BOOTSTRAP_TOKEN="${2:-${BOOTSTRAP_TOKEN:?set BOOTSTRAP_TOKEN or pass it as the 2nd arg}}"

SUFFIX="$(date +%s)-$RANDOM"
SLUG="smoke-${SUFFIX}"
ADMIN_EMAIL="admin@${SLUG}.test"
ADMIN_PW="smoke-admin-$SUFFIX"
MEMBER_EMAIL="member@${SLUG}.test"
MEMBER_PW="smoke-member-$SUFFIX"
FLAG_KEY="smoke-flag-${SUFFIX}"

need() { command -v "$1" >/dev/null || { echo "missing dependency: $1" >&2; exit 1; }; }
need curl
need jq

api() {
  # api <method> <path> [json-body] [extra curl args...]
  local method="$1" path="$2" body="${3:-}"; shift $(( $# >= 3 ? 3 : 2 ))
  local args=(-sS -X "$method" "${BASE_URL}${path}" -H 'Content-Type: application/json')
  [[ -n "$body" ]] && args+=(--data "$body")
  curl "${args[@]}" "$@"
}

step() { printf '\n\033[1m▶ %s\033[0m\n' "$1"; }
ok()   { printf '  \033[32m✓ %s\033[0m\n' "$1"; }
fail() { printf '  \033[31m✗ %s\033[0m\n' "$1" >&2; exit 1; }

step "Health check"
curl -fsS "${BASE_URL}/actuator/health" | jq -e '.status == "UP"' >/dev/null \
  && ok "/actuator/health is UP" || fail "service is not healthy"

step "Bootstrap tenant + admin ($SLUG)"
api POST /api/v1/tenants \
  "$(jq -nc --arg s "$SLUG" --arg e "$ADMIN_EMAIL" --arg p "$ADMIN_PW" \
      '{slug:$s, name:"Smoke Test Co", adminEmail:$e, adminPassword:$p}')" \
  -H "X-Bootstrap-Token: ${BOOTSTRAP_TOKEN}" -o /tmp/smoke_tenant.json -w '%{http_code}' \
  | grep -q 201 && ok "tenant created (201)" || fail "bootstrap failed: $(cat /tmp/smoke_tenant.json)"

step "Log in as admin"
TOKEN="$(api POST /api/v1/auth/login \
  "$(jq -nc --arg s "$SLUG" --arg e "$ADMIN_EMAIL" --arg p "$ADMIN_PW" \
      '{tenantSlug:$s, email:$e, password:$p}')" | jq -r '.accessToken')"
[[ -n "$TOKEN" && "$TOKEN" != "null" ]] && ok "got access token" || fail "login failed"
AUTH=(-H "Authorization: Bearer ${TOKEN}")

step "Create role 'flag-manager'"
ROLE_ID="$(api POST /api/v1/roles '{"name":"flag-manager","description":"smoke"}' "${AUTH[@]}" | jq -r '.id')"
[[ -n "$ROLE_ID" && "$ROLE_ID" != "null" ]] && ok "role $ROLE_ID" || fail "role creation failed"

step "Grant flag:write to the role"
api POST "/api/v1/roles/${ROLE_ID}/permissions/flag:write" '' "${AUTH[@]}" -o /dev/null -w '%{http_code}' \
  | grep -q 204 && ok "granted" || fail "grant failed"

step "Create member user + assign role"
MEMBER_ID="$(api POST /api/v1/users \
  "$(jq -nc --arg e "$MEMBER_EMAIL" --arg p "$MEMBER_PW" '{email:$e, password:$p}')" "${AUTH[@]}" | jq -r '.id')"
[[ -n "$MEMBER_ID" && "$MEMBER_ID" != "null" ]] && ok "member $MEMBER_ID" || fail "user creation failed"
api POST "/api/v1/users/${MEMBER_ID}/roles" "$(jq -nc --arg r "$ROLE_ID" '{roleId:$r}')" "${AUTH[@]}" \
  -o /dev/null -w '%{http_code}' | grep -q 204 && ok "role assigned" || fail "assignment failed"
api GET "/api/v1/users/${MEMBER_ID}/permissions" '' "${AUTH[@]}" | jq -e 'index("flag:write")' >/dev/null \
  && ok "effective permissions include flag:write" || fail "permission not resolved via CTE"

step "Create flag '$FLAG_KEY' and set 50% rollout"
api POST /api/v1/flags "$(jq -nc --arg k "$FLAG_KEY" '{flagKey:$k, description:"smoke"}')" "${AUTH[@]}" -o /dev/null
VERSION="$(api GET "/api/v1/flags/${FLAG_KEY}" '' "${AUTH[@]}" | jq -r '.version')"
api PUT "/api/v1/flags/${FLAG_KEY}" \
  "$(jq -nc --argjson v "$VERSION" '{enabled:false, rolloutPercentage:50, description:"smoke", version:$v}')" \
  "${AUTH[@]}" | jq -e '.rolloutPercentage == 50' >/dev/null && ok "flag at 50%, globally disabled" || fail "flag update failed"

step "Evaluate for the member (expect false: disabled, not whitelisted)"
BEFORE="$(api POST "/api/v1/flags/${FLAG_KEY}/evaluate?userId=${MEMBER_ID}" '' "${AUTH[@]}" | jq -r '.enabled')"
[[ "$BEFORE" == "false" ]] && ok "evaluated false" || fail "expected false, got '$BEFORE'"

step "Whitelist the member, then re-evaluate (expect the flip to true)"
api PUT "/api/v1/flags/${FLAG_KEY}/whitelist/${MEMBER_ID}" '' "${AUTH[@]}" -o /dev/null -w '%{http_code}' \
  | grep -q 204 && ok "whitelisted" || fail "whitelist failed"
AFTER="$(api POST "/api/v1/flags/${FLAG_KEY}/evaluate?userId=${MEMBER_ID}" '' "${AUTH[@]}" | jq -r '.enabled')"
[[ "$AFTER" == "true" ]] && ok "evaluated true — whitelist beats the global disable switch (§7.1)" \
  || fail "expected true after whitelist, got '$AFTER'"

printf '\n\033[1;32mSMOKE OK\033[0m — %s\n' "$BASE_URL"
