# M0 npm test-toolchain security review — 2026-11-01

Scope: `apps/web` and `packages/engine-ts`. These packages have separate lockfiles;
both require review. Application/engine source, tests, fixtures, permissions, and
the separate-engine decision in ADR-0002 are unchanged.

## Baseline and remediation

Each baseline lockfile resolved Vitest / `@vitest/mocker` 3.2.7 and included
Tinypool. A full audit reported three flagged packages per lockfile: moderate
`@vitest/mocker`, critical `tinypool`, and critical `vitest`. Vitest's aggregate
critical rating included the transitive Tinypool findings; these are not three
independent critical vulnerabilities.

| Advisory | Affected path / precondition described upstream | Remediation |
|---|---|---|
| [GHSA-82fw-gwwq-j7x9](https://github.com/advisories/GHSA-82fw-gwwq-j7x9) | Redirect mock can disclose files outside the allowed root; unauthenticated route involves standalone mocker/interceptor plugins | Patched mocker/Vitest 4.1.11 or later |
| [GHSA-qxg6-89mg-rqv8](https://github.com/advisories/GHSA-qxg6-89mg-rqv8) | Unauthenticated manual mock registration can inject generated browser code through the standalone plugin route | Patched mocker 4.1.12 or later on the v4 line |
| [GHSA-5gmw-xhrv-c9v3](https://github.com/advisories/GHSA-5gmw-xhrv-c9v3) | Prior prototype pollution can influence inherited worker options and lead to worker code execution | Tinypool 2.1.1 fixes this advisory; removed from the selected toolchain |
| [GHSA-85c8-ppgw-ccpr](https://github.com/advisories/GHSA-85c8-ppgw-ccpr) | Prior prototype pollution can redirect a worker via inherited `run()` options | Tinypool 2.1.2 fixes this advisory; removed from the selected toolchain |

Repository inspection found `vitest run` scripts and ordinary Node-based tests;
no direct `mockerPlugin`, `interceptorPlugin`, browser-mode, or custom pool usage.
This describes configured exposure, not proof of non-exploitability or evidence
of compromise. The dependencies are updated regardless.

Both packages now declare `vitest: ^4.1.12`, with lockfiles resolving Vitest and
mocker **4.1.12**. This is the smallest patched major line covering both mocker
advisories. [The 4.1.12 release](https://github.com/vitest-dev/vitest/releases/tag/v4.1.12)
contains the mock hardening backport. [Vitest 4's migration guide](https://v4.vitest.dev/guide/migration)
documents its removal of Tinypool and compatibility with Node 20+ / Vite 6+;
our Node 22 CI and existing Vite versions meet those requirements. Current tests
do not use the changed mock, browser-provider, custom pool, or coverage APIs.

The web package retains Vite **6.4.5**; the engine package retains Vite **7.3.7**.
The latter is now an explicit dev dependency to prevent Vitest's wider Vite range
from pulling an unrelated Vite 8 major upgrade. Existing TypeScript, React,
React DOM, and YAML versions are retained. Other changed packages belong to the
updated Vitest dependency graph. No forced audit fix, overrides, ignored advisory,
test weakening, or new runtime feature is used.

## Verification and regression gate

- `npm ci` succeeds independently in both package directories.
- Unmodified tests pass: 25 TypeScript engine tests and 12 web tests.
- Web `npm run build` passes, including test type-checking; production asset names
  and sizes remain the same as the previously verified build.
- `npm audit --include=dev --audit-level=moderate --json` reports **zero findings**
  at every severity in each package after clean installation. Tinypool has no
  entries in either resolved lockfile.
- Existing CI tests/build remain enabled. Two explicit audit steps in the `web`
  job run `npm audit --include=dev --audit-level=moderate` in the respective package
  directories. Moderate, high, or critical findings fail the check. Advisory-feed
  availability is a CI dependency; failures are investigated rather than ignored.
- CI installs each package with `npm ci` so tests and audits use the committed
  lockfiles without a fresh semver resolution or lockfile rewrite.

Audit results describe the registry advisory database at the time of the run;
they are not a guarantee that the software has no undiscovered vulnerabilities.
No Android rebuild/reinstall or new phone/browser runtime behavior is claimed for
this development-toolchain-only change. Rollback reverts manifests, lockfiles,
and CI gates together and reintroduces the recorded vulnerable baseline.
