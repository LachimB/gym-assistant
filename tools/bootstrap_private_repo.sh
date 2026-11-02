#!/usr/bin/env bash
# User-approved bootstrap only; intentionally interactive; no paid/cloud resources.
set -euo pipefail
TARGET='<private-path>
: "${EXPECTED_GITHUB_OWNER:?Confirm personal owner in env before running}"
: "${EXPECTED_GIT_AUTHOR_NAME:?Confirm personal Git author name in env before running}"
: "${EXPECTED_GIT_AUTHOR_EMAIL:?Confirm personal Git author email in env before running}"
if [[ "$EXPECTED_GITHUB_OWNER" != 'LachimB' || "$EXPECTED_GIT_AUTHOR_NAME" != 'LachimB' ]]; then echo 'STOP: confirmed personal GitHub owner is LachimB and author name is LachimB.' >&2; exit 1; fi
if [[ "$(printf %s "$EXPECTED_GIT_AUTHOR_EMAIL" | tr '[:upper:]' '[:lower:]')" != '<private-contact-redacted>' ]]; then echo 'STOP: confirmed personal author email does not match.' >&2; exit 1; fi
bash "$(dirname "$0")/preflight_personal_github.sh" --bootstrap
if [[ -e "$TARGET" ]]; then echo "STOP: target already exists ($TARGET); inspect manually, this script will not overwrite" >&2; exit 1; fi
printf '\nThis will initialize a NEW PRIVATE GitHub repo %s/gym-assistant at %s.\n' "$EXPECTED_GITHUB_OWNER" "$TARGET"
read -r -p 'Type exactly CREATE PRIVATE GYM REPO to proceed: ' confirmation
[[ "$confirmation" == 'CREATE PRIVATE GYM REPO' ]] || { echo 'Cancelled'; exit 1; }
# Inspect starter files before executing script. Do not copy .git or secrets from existing repos.
repo_source="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
if [[ "$repo_source" == "$TARGET" ]]; then echo 'STOP: starter already in target; initialize manually after inspecting existing files'; exit 1; fi
if gh repo view "${EXPECTED_GITHUB_OWNER}/gym-assistant" >/dev/null 2>&1; then echo 'STOP: GitHub repo already exists'; exit 1; fi
mkdir -p "$TARGET"
# tar includes dotfiles and excludes version control; no reliance on glob behavior.
(cd "$repo_source" && tar --exclude=.git -cf - .) | (cd "$TARGET" && tar -xf -)
cd "$TARGET"
git init -b main
git config --local user.name "$EXPECTED_GIT_AUTHOR_NAME"
git config --local user.email "$EXPECTED_GIT_AUTHOR_EMAIL"
git add .
git commit -m 'Add Gym Assistant starter package'
# gh creates the private repo, but do not automatically push via unverified global git credential helpers.
gh repo create "${EXPECTED_GITHUB_OWNER}/gym-assistant" --private --source=. --remote=origin
bash tools/preflight_personal_github.sh
bash tools/personal_git_push.sh --set-upstream origin main
printf 'Created private repo and pushed initial commit using verified personal gh credentials.\n'
