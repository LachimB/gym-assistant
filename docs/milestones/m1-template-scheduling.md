# M1 persistent Template wakeups

Approved scope: ADR-0009; GA-DATA-001/002/003/004/010/011. WorkManager runtime and
instrumentation testing are pinned at2.12.0. Compatibility compilation succeeds
with compileSdk36/minSdk31, existing Room2.8.5 and selected Kotlin stdlib2.1.20.
Declared-license/POM hashes and original WorkManager Apache license are retained;
binary-release clearance remains separate.

## Implemented boundary

- Durable enabled bindings under an active same-account root determine scheduled
  work. Reconciliation recreates missing work and cancels inactive bindings.
- One unique periodic wakeup per binding at15 minutes, with the first periodic
  check delayed15 minutes. App opening/Sync now/local publication may request a
  unique one-off wakeup. KEEP preserves existing requests across reconciliation.
  A request arriving during a running one-off may be coalesced; durable database
  reconciliation and periodic discovery preserve pending work.
- CONNECTED permits Wi-Fi and mobile; Android/Data Saver restrictions still apply.
  Transient handler outcomes request exponential backoff starting at30 seconds,
  capped by WorkManager's platform limit. Owner-action outcomes end that wakeup
  without automatically retrying authorization. A wakeup never acknowledges a
  file, changes a library head or completes a durable publication job.
- WorkManager inputs/tags contain only local binding identity. No source, provider
  ID, account, token or recording enters WorkData. The handler is responsible for
  rechecking the durable binding, state and remaining allowance before work.
- The application must supply the worker handler; absent configuration fails
  closed. No handler, UI opt-in or startup scheduling is wired in this slice.
  Unsupported/extra worker inputs fail before invoking it; cancellation propagates.
- Ordinary deferrable jobs only. Unused foreground service/permission and the
  library diagnostic broadcast receiver are removed from the merged manifest.
  Network-state, wake-lock and reboot support remain available to WorkManager.

## Evidence and remaining work

Local86 JVM checks pass and instrumentation compiles. Five real WorkManager
test-driver cases cover unique/repeated startup reconciliation, connected network
constraints and periodic timing, opt-out/unverified-account cancellation, retry
versus owner action, invalid/missing handler inputs and packaged-manifest controls. They use synthetic local
bindings and a fake wake handler. Current-head seven-version CI must execute them.

This does not prove actual background/screen-off, Doze, reboot or live transfer.
Wire the account-bound/budgeted durable publication and observation coordinator,
then reconcile on startup and local publication. WorkManager's database is derived
scheduling state; the application database/source archives remain authoritative.
Preserve the approved common-root inventory review, required CI and fresh verified
private backup before any physical phone migration. No live request, token,
allowance reservation, phone update or root creation/move occurred here.

Android may defer work; explicit force-stop prevents ordinary background recovery
until the owner launches the app. Do not promise exact scheduling or bypass that
restriction. Verify process death/reboot recovery and genuine screen-off behavior
on the supported emulator matrix and physical device before claiming acceptance.

## Primary references

- [WorkManager2.12.0 release](https://developer.android.com/jetpack/androidx/releases/work#2.12.0)
- [Periodic work, constraints and backoff](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- [Integration testing with WorkManager](https://developer.android.com/develop/background-work/background-tasks/testing/persistent/integration-testing)
