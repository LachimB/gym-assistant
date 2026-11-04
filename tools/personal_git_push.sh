#!/usr/bin/env bash
# Strict personal GH credential path for HTTPS pushes (no printing tokens).
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$repo_dir"
bash tools/preflight_personal_github.sh
python3 tools/check_repo_hygiene.py
command -v gitleaks >/dev/null || { echo "STOP: Gitleaks required before push" >&2; exit 1; }
gitleaks dir . --config .gitleaks.toml --redact=100 --ignore-gitleaks-allow --no-banner
# The enforced pre-push hook scans every introduced commit and its full preimages.
if [[ $# -eq 0 ]]; then set -- origin HEAD; fi
# Empty first credential.helper explicitly clears accumulated global helpers.
exec git -c core.hooksPath=.githooks -c credential.helper= -c 'credential.helper=!gh auth git-credential' push "$@"
