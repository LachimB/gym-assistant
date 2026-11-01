# Development working agreement

- Agent-neutral Markdown instructions are canonical; Cursor, Codex and Claude adapters must only reference them.
- Technical flexibility is allowed inside fixed behavioral/security/data requirements.
- Significant choices: ADR with context, options, effects, owner, date and status; request approval if an architecture boundary changes.
- External service register must record proposed service, data sent, OAuth permissions, owner, estimated recurring and one-time cost, budget risk, alternatives, approval status/time. Approval for provider != approval for chargeable resource.
- PR checklist: requirement IDs, change summary, security/privacy impacts, evidence of tests, screenshots or device observations where applicable, migrations/rollback strategy, docs updates, no secrets/private fixtures.
- Do not auto-merge failing CI. Milestone completion and production releases require approval; ordinary within-milestone PRs can merge after successful validation.
- Keep README usable by a developer who was not present in this chat; do not rely on tool conversation memory.
- User-approved local Android workflow: after each successful debug APK build run by an agent, install with `adb install -r` when exactly one authorized device is connected. If none is connected, report installation skipped; if several are connected, request the target. Never uninstall or clear app data as part of this workflow. CI builds do not install to the user's phone. Record the installed build identity for device acceptance tests.
