#!/usr/bin/env bash
# Read-only verification. Never prints secret values; use before all GitHub write actions.
set -euo pipefail
BOOTSTRAP=false
if [[ "${1:-}" == '--bootstrap' ]]; then BOOTSTRAP=true; elif [[ $# -ne 0 ]]; then echo 'Usage: preflight_personal_github.sh [--bootstrap]' >&2; exit 2; fi
: "${EXPECTED_GITHUB_OWNER:?Set EXPECTED_GITHUB_OWNER to explicitly confirmed personal GitHub login}"
: "${EXPECTED_GIT_AUTHOR_EMAIL:?Set EXPECTED_GIT_AUTHOR_EMAIL to explicitly confirmed PERSONAL author email}"
if [[ ! "$EXPECTED_GITHUB_OWNER" =~ ^[a-zA-Z0-9-]+$ ]]; then echo 'Invalid expected owner' >&2; exit 2; fi
if [[ ! "$EXPECTED_GIT_AUTHOR_EMAIL" =~ ^[^[:space:]@]+@[^[:space:]@]+$ ]]; then echo 'Invalid personal email format' >&2; exit 2; fi
# Hard safety gate for the previously used company domain, not the sole proof of identity.
if [[ "$(printf %s "$EXPECTED_GIT_AUTHOR_EMAIL" | tr "[:upper:]" "[:lower:]")" == *'@bolt.eu' ]]; then echo 'Company author email prohibited' >&2; exit 2; fi
# Confirmed by user 2026-11-02: GitHub owner and local author name LachimB.
: "${EXPECTED_GIT_AUTHOR_NAME:?Set EXPECTED_GIT_AUTHOR_NAME to the confirmed personal author name}"
CONFIRMED_PERSONAL_OWNER='LachimB'
CONFIRMED_PERSONAL_NAME='LachimB'
CONFIRMED_PERSONAL_EMAIL='michal.buzek@gmail.com'
if [[ "$EXPECTED_GITHUB_OWNER" != "$CONFIRMED_PERSONAL_OWNER" ]]; then
  echo "STOP: EXPECTED_GITHUB_OWNER must be the confirmed personal login ${CONFIRMED_PERSONAL_OWNER}." >&2
  exit 1
fi
if [[ "$EXPECTED_GIT_AUTHOR_NAME" != "$CONFIRMED_PERSONAL_NAME" ]]; then
  echo "STOP: EXPECTED_GIT_AUTHOR_NAME must be the confirmed personal author name ${CONFIRMED_PERSONAL_NAME}." >&2
  exit 1
fi
if [[ "$(printf %s "$EXPECTED_GIT_AUTHOR_EMAIL" | tr '[:upper:]' '[:lower:]')" != "$CONFIRMED_PERSONAL_EMAIL" ]]; then
  echo 'STOP: EXPECTED_GIT_AUTHOR_EMAIL does not match the confirmed personal author email.' >&2
  exit 1
fi
for v in GH_TOKEN GITHUB_TOKEN GH_ENTERPRISE_TOKEN GITHUB_ENTERPRISE_TOKEN; do
  if [[ -n "$(printenv "$v" 2>/dev/null || true)" ]]; then echo "STOP: $v is set and can override personal gh auth; clear/resolve it before continuing" >&2; exit 1; fi
done
command -v gh >/dev/null || { echo 'gh CLI missing'; exit 1; }
command -v git >/dev/null || { echo 'git missing'; exit 1; }
login="$(gh api user --jq .login 2>/dev/null)" || { echo 'gh personal authentication unavailable; fix manually'; exit 1; }
if [[ "$login" != "$EXPECTED_GITHUB_OWNER" ]]; then
 echo "STOP: Effective GitHub login '$login' differs from confirmed personal account '$EXPECTED_GITHUB_OWNER'" >&2; exit 1
fi
if [[ "$BOOTSTRAP" == true ]]; then
 echo "PASS: effective gh API login matches intended personal owner '$EXPECTED_GITHUB_OWNER'."
 echo "INFO: bootstrap mode cannot validate repo-local Git identity/remote until initialized."
 echo "INFO: author email is supplied by user and should be checked against personal account."
 exit 0
fi
root="$(git rev-parse --show-toplevel 2>/dev/null)" || { echo 'STOP: not inside initialized Git repo'; exit 1; }
cd "$root"
expected_local="${EXPECTED_LOCAL_REPO_PATH:-/Users/michal/repos/private/gym-assistant}"
if [[ "$root" != "$expected_local" ]]; then echo "STOP: repo path $root differs from personal expected $expected_local" >&2; exit 1; fi
actual_email="$(git config --local --get user.email || true)"
actual_name="$(git config --local --get user.name || true)"
if [[ "$actual_name" != "$EXPECTED_GIT_AUTHOR_NAME" || "$actual_email" != "$EXPECTED_GIT_AUTHOR_EMAIL" ]]; then
 echo 'STOP: repository-local author identity is missing or does not match explicitly confirmed personal identity' >&2; exit 1
fi
remote="$(git remote get-url origin 2>/dev/null || true)"
expected_https="https://github.com/${EXPECTED_GITHUB_OWNER}/gym-assistant"
expected_ssh="git@github.com:${EXPECTED_GITHUB_OWNER}/gym-assistant"
if [[ "$remote" != "$expected_https" && "$remote" != "${expected_https}.git" && "$remote" != "$expected_ssh" && "$remote" != "${expected_ssh}.git" ]]; then
 echo "STOP: origin '$remote' is not the confirmed personal gym-assistant repository" >&2; exit 1
fi
# `gh api user` proves gh effective login, NOT the identity in every possible external Git helper.
# For HTTPS pushes use the wrapper below to force gh credentials rather than cached work credentials.
if [[ "$remote" == git@* ]]; then
 echo 'STOP: SSH origin cannot prove which SSH key GitHub will select; use HTTPS + gh credential helper for strict personal separation.' >&2
 exit 1
fi
visibility="$(gh repo view "${EXPECTED_GITHUB_OWNER}/gym-assistant" --json visibility --jq .visibility 2>/dev/null)" || { echo 'STOP: cannot check remote repo ownership/visibility'; exit 1; }
if [[ "$visibility" != PRIVATE ]]; then echo "STOP: expected PRIVATE repo, found $visibility" >&2; exit 1; fi
printf 'PASS: verified gh owner %s, repo-local author %s <%s>, private origin %s\n' "$login" "$actual_name" "$actual_email" "$remote"
echo 'NOTE: For git push use tools/personal_git_push.sh (forces gh credentials); do not use an unverified global credential helper.'
