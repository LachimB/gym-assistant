#!/usr/bin/env bash
# CI installer for the existing pinned Linux runner. Local users install 8.30.1.
set -euo pipefail
: "${RUNNER_TEMP:?This installer requires an isolated CI runner}"
archive="$RUNNER_TEMP/security-gitleaks.tar.gz"
curl --proto '=https' --tlsv1.2 -fsSL --retry 2 -o "$archive" https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/gitleaks_8.30.1_linux_x64.tar.gz
echo "551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb  $archive" | sha256sum -c -
mkdir -p "$RUNNER_TEMP/security-bin"
tar -xzf "$archive" -C "$RUNNER_TEMP/security-bin" gitleaks
"$RUNNER_TEMP/security-bin/gitleaks" version
printf '%s\n' "$RUNNER_TEMP/security-bin" >> "$GITHUB_PATH"
