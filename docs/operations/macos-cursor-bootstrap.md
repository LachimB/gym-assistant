# macOS + Cursor bootstrap (personal accounts only)

Target repo: `<private-path>`; name `gym-assistant`; confirmed personal GitHub owner: `LachimB`. The starter package originally assumed `michal-buzek`, and a later instruction said `MichalB`. Both are withdrawn as the GitHub login. `gh api user --jq .login` must return exactly `LachimB` before any GitHub write. Git author name stays `MichalB`; author email stays `<private-contact-redacted>`.

## Step 0 — Inspect, do not copy existing project credentials

`<private-path>` may be used to *inspect* harmless GitHub and build conventions: `git remote -v`, `.gitignore`, GitHub workflow layout. **Never copy** its `.git` directory, git config, credentials, `gh` tokens, SSH private keys, `.env` or project-specific secrets. Existing setup can contain account-specific overrides.

## Step 1 — Inspect local credentials without printing secrets

```bash
gh --version
git --version
gh auth status
git config --show-origin --get-all credential.helper || true
# Print only names/presence of any token override, never values.
for key in GH_TOKEN GITHUB_TOKEN GH_ENTERPRISE_TOKEN GITHUB_ENTERPRISE_TOKEN; do
  if printenv "$key" >/dev/null 2>&1; then echo "WARNING: $key is set"; fi
done
```

The `${!key+x}` syntax requires bash; macOS interactive default may be zsh, so run that loop with `bash` or simply use the project preflight script.

## Step 2 — Explicit personal identity

The confirmed personal author email for this repository is `<private-contact-redacted>`. Do **not** reuse a company email. Pass it explicitly to preflight; do not rely on global Git config. Example invocation:

```bash
# Clear overrides first. A set GITHUB_TOKEN makes gh ignore the keyring account.
unset GH_TOKEN GITHUB_TOKEN GH_ENTERPRISE_TOKEN GITHUB_ENTERPRISE_TOKEN
export EXPECTED_GITHUB_OWNER='LachimB'
export EXPECTED_GIT_AUTHOR_NAME='MichalB'
export EXPECTED_GIT_AUTHOR_EMAIL='<private-contact-redacted>'
# Before initializing a new repo use --bootstrap (validates account and author intent).
bash tools/preflight_personal_github.sh --bootstrap
```

This stops on token environment overrides and requires the actual effective `gh api user` result to match `EXPECTED_GITHUB_OWNER`. After creating the repo, run it again without `--bootstrap` to verify `.git/config` and remote.

## Step 3 — Initialize the existing directory (only after user approves creation)

Do **not** run `tools/bootstrap_private_repo.sh`. It exits when `<private-path>` already exists, The script now pins GitHub owner `LachimB`. It still exits when this directory already exists, so use the manual steps below.

The starter package is already in the target directory. After preflight `--bootstrap` passes as `LachimB`, and after a separate yes to create the remote:

```bash
cd <private-path>
# Stop if `gh repo view LachimB/gym-assistant` shows an existing repository.
git init -b main
git config --local user.name 'MichalB'
git config --local user.email '<private-contact-redacted>'
git add .
git commit -m 'Add Gym Assistant starter specification'
# Do not pass --push. The default helper can select the wrong account.
gh repo create LachimB/gym-assistant --private --source=. --remote=origin
bash tools/preflight_personal_github.sh
bash tools/personal_git_push.sh --set-upstream origin main
```

Creating the remote, pushing, and relying on GitHub Actions each need explicit approval. If `LachimB/gym-assistant` already exists, stop and do not overwrite its history. GitHub Actions on a private repository can consume included minutes; stop before enabling a workflow that would be billed.

## Step 4 — Validate in Cursor

Open the destination folder in Cursor and run:

```bash
python3 -m pip install -r tools/requirements.txt
python3 -m unittest discover -s tests -v
```

Read `AGENTS.md`, `docs/architecture/open-questions.md`, `docs/milestones/m0.md`, `M0_START_PROMPT.md`; give Cursor the starter prompt and request an M0-only implementation plan. No cloud services unless separately approved.

## Step 5 — Versioning and approvals

PRs and GitHub Issues are encouraged. CI runs fixture validation. Only after user approval may the agent create a Google Cloud project, OAuth client, authentication service or hosted previews; **separate explicit approval before billable resources**. Distinct stage/prod app IDs and credentials. Never hardcode Android application ID final values before appropriate review.
