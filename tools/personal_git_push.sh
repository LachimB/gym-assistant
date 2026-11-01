#!/usr/bin/env bash
# Strict personal GH credential path for HTTPS pushes (no printing tokens).
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$repo_dir"
bash tools/preflight_personal_github.sh
if [[ $# -eq 0 ]]; then set -- origin HEAD; fi
# Empty first credential.helper explicitly clears accumulated global helpers.
exec git -c credential.helper= -c 'credential.helper=!gh auth git-credential' push "$@"
