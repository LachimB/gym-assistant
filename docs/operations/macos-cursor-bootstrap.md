# Personal repository setup

The confirmed personal GitHub account is `LachimB`; repository:
`https://github.com/LachimB/gym-assistant`. This repository is intentionally public.
Use your chosen `<repository-root>`; never copy another project's authentication.

```sh
cd "$HOME/path/to/gym-assistant"
git config --local user.name LachimB
git config --local user.email 17935459+LachimB@users.noreply.github.com
export EXPECTED_GITHUB_OWNER=LachimB
export EXPECTED_GIT_AUTHOR_NAME=LachimB
export EXPECTED_GIT_AUTHOR_EMAIL=17935459+LachimB@users.noreply.github.com
export EXPECTED_LOCAL_REPO_PATH="$PWD"
bash tools/preflight_personal_github.sh
```

Never change global Git identity or use company credentials. Resolve token override
variables and authenticate the CLI to the confirmed personal account. Use
`tools/personal_git_push.sh` for HTTPS pushes after the preflight passes.
The legacy bootstrap script is for an explicitly approved new directory only;
never run it against an existing checkout or create another remote for this project.

Read [publication privacy](publication-privacy.md) before staging or sharing.
Private accounts, provider IDs, source documents, sessions, screenshots, logs and
acceptance details belong in `<private-evidence-directory>` outside the checkout.
Public examples use reserved example domains and synthetic identifiers.
