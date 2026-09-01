# Unified Syncthing Runtime Architecture Design

## 1. Status

Approved architecture design for replacing the staged split normal/root runtime implementation with a unified AIDL-driven runtime model.

This design is based on the proven modern-superuser implementation and the maintainer suggestion that privileged and unprivileged execution should use the same architecture to maximize code sharing and long-term maintainability.

Where this document conflicts with `2026-08-25-modern-superuser-access-design.md`, this document supersedes that design for runtime execution, state access, folder access, state transfer, process supervision, Binder topology, and error vocabulary. The earlier design remains authoritative for user-facing superuser behavior and security invariants that are not replaced here.

## 2. Motivation

The staged modern-superuser implementation successfully proves the required root mechanics: libsu 6.0.0, a non-exported UID-0 RootService, narrow AIDL capabilities, fail-closed transitions, bounded state access, orphan recovery, ownership repair, and physical qualification flows.

Its current internal architecture is intentionally asymmetric:

- normal mode executes Syncthing directly under the application UID;
- normal folder/state/transfer helpers use local Java/Android APIs;
- superuser mode routes equivalent work through Binder into `SyncthingSuperuserService`.

That asymmetry increases maintenance cost. Semantically equivalent features require normal and privileged adapters, routers, tests, and failure handling even when their only meaningful difference is effective UID.

The long-term target is therefore:

> Privilege selects the service host and effective UID, not a different application runtime architecture.

Normal and superuser modes must use the same runtime AIDL contract, the same application-side session abstraction, the same service-side capability implementation, and the same process-supervision model wherever the underlying capability is valid in both modes.

## 3. Goals

1. Make normal and superuser execution use the same AIDL runtime contract.
2. Preserve the proven superuser behavior and security properties of the staged implementation.
3. Keep `SyncthingService` as the sole lifecycle authority for whether Syncthing should run.
4. Keep the normal runtime service in the existing application process and UID.
5. Keep exactly one UID-0 RootService.
6. Share service-side runtime semantics through composition rather than duplicate implementations.
7. Share process supervision and exact orphan recovery across normal and superuser modes.
8. Remove application-level normal/root adapter trees whose only responsibility is privilege routing.
9. Generalize shared runtime IPC/error vocabulary so normal execution is not expressed through `Superuser*` types.
10. Preserve stable diagnostic identifiers and logging usefulness while generalizing internal error types.
11. Preserve backup/import format and workflow semantics.
12. Preserve `PREF_USE_ROOT` as the durable configured-mode source of truth.

## 4. Non-goals

This design does not:

- change the user-facing superuser setting or its semantics;
- add arbitrary shell execution;
- add arbitrary filesystem access through Binder;
- add a second UID-0 service;
- move the normal runtime into a dedicated Android process;
- create another foreground-service lifecycle authority;
- change backup archive shape;
- change import workflow semantics;
- weaken root fail-closed behavior;
- preserve obsolete internal adapters solely for source compatibility;
- introduce a database or user-data migration.

## 5. Architectural Principles

### 5.1 Runtime parity

Capabilities that are valid in both normal and superuser modes must traverse the same application-facing runtime interface.

### 5.2 Privilege remains explicit

Root authorization, UID-0 verification, ownership repair, and SELinux restoration remain root-only control operations. They must not be disguised as general runtime capabilities.

### 5.3 Lifecycle authority remains singular

`SyncthingService` decides whether Syncthing should run. Runtime services own capability execution and supervised process state only.

### 5.4 Closed capability boundary remains mandatory

AIDL continues to expose semantic operations only. No generic command execution, arbitrary executable selection, arbitrary root path reads/writes, or generic root shell APIs are permitted.

### 5.5 Fail closed

When superuser mode is configured, any failure to authorize, bind, verify UID 0, recover the prior process, or execute required privileged work must not fall back to normal execution.

## 6. Target Architecture

```text
                           SyncthingService
                                 |
                                 v
                      SyncthingRuntimeSession
                                 |
                    +------------+-------------+
                    |                          |
                    v                          v
             normal binding              root binding
                    |                          |
                    v                          v
        SyncthingRuntimeService     SyncthingSuperuserService
              app UID                     UID 0
                    |                          |
                    +------------+-------------+
                                 |
                                 v
                  SyncthingRuntimeCapabilities
                                 |
          +----------------------+----------------------+
          |                      |                      |
          v                      v                      v
     CoreSupervisor         StateAccess            FolderAccess
          |
          v
  ProcessIdentityStore /
   orphan recovery
```

The normal service is a bound-only Android `Service` running in the existing application process. It is not assigned an `android:process` and is not independently started as a lifecycle authority.

The superuser service remains a single non-exported libsu `RootService` running as UID 0.

Both hosts delegate common runtime operations to the same service-side implementation.

## 7. AIDL Contracts

### 7.1 Shared runtime contract

Introduce a privilege-neutral runtime interface conceptually equivalent to:

```text
ISyncthingRuntimeService
  recoverOrphanedCore()
  startCore(commandId, environment, captureStdout)
  waitForCoreExit()
  stopOwnedCore()
  getCoreStatus()

  testFolderWritable(absolutePath)
  findSyncConflicts(absoluteConfiguredFolderPath)

  openStateFileForRead(stateFileId)
  writeStateFileAtomic(stateFileId, source)
  deleteStateFile(stateFileId)

  stageBackupState(transferId, appUid, appGid)
  installBackupState(transferId)
```

This interface is implemented by both normal and root runtime hosts.

The exact capability set must remain closed and reviewable. Implementation may refine names or result types, but it must not broaden the security boundary.

### 7.2 Root-only control contract

Keep genuinely privileged transition mechanics separate:

```text
ISyncthingSuperuserControl
  verifySuperuser()
  repairAppPrivateState(appUid, appGid)
  getRuntime() -> ISyncthingRuntimeService
```

Normal mode binds directly to `ISyncthingRuntimeService`.

Root mode binds once to `ISyncthingSuperuserControl`, verifies UID 0, then obtains the runtime sub-interface from the same RootService instance.

There must not be a second RootService or second independent root Binder connection for runtime work.

## 8. Application-side Runtime Session

Introduce a single application-side connection owner, conceptually `SyncthingRuntimeSession`.

Responsibilities:

- select normal or root binding based on configured mode;
- own the active Binder connection;
- expose the active `ISyncthingRuntimeService` to capability consumers;
- own common connection state, timeout handling, stale callback rejection, disconnect behavior, and Binder-death mapping;
- ensure only one selected runtime session is active for the configured mode;
- surface runtime-mode context for diagnostics and logging.

### 8.1 Normal connection

```text
SyncthingRuntimeSession
 -> Context.bindService(SyncthingRuntimeService)
 -> ISyncthingRuntimeService
```

### 8.2 Root connection

```text
SyncthingRuntimeSession
 -> libsu RootService.bind(SyncthingSuperuserService)
 -> ISyncthingSuperuserControl
 -> verifySuperuser()
 -> getRuntime()
 -> ISyncthingRuntimeService
```

The root preflight/authorization logic already proven in the staged implementation remains part of establishing the root session.

`SuperuserModeController` and runtime capability consumers must share this same session/connection owner. Separate independently binding root-control and runtime clients are not permitted.

## 9. Service-side Runtime Composition

Create a plain Java shared service-side component, conceptually `SyncthingRuntimeCapabilities`.

It owns common semantics for:

- core launch/wait/stop/status;
- process identity verification and orphan recovery;
- folder writeability probes;
- conflict discovery;
- fixed app-private state read/write/delete;
- backup state staging/install.

The Android service hosts remain thin:

### 9.1 Normal host

`SyncthingRuntimeService extends Service`

Responsibilities:

- expose `ISyncthingRuntimeService`;
- enforce non-exported/app-owned access;
- create runtime capabilities using normal application context and UID;
- remain bound-only.

### 9.2 Root host

`SyncthingSuperuserService extends RootService`

Responsibilities:

- expose `ISyncthingSuperuserControl`;
- verify the owning application caller;
- verify that the service itself is running as UID 0;
- expose the shared runtime sub-interface;
- implement root-only state ownership/SELinux repair;
- create the same runtime capabilities under UID 0.

Shared service behavior must use composition. A common Android-service superclass is not required and should not be introduced merely to force inheritance between `Service` and `RootService`.

## 10. Process Supervision and Orphan Recovery

Generalize the proven root process-supervision model so both runtime hosts use the same invariants.

For every core launch:

1. persist `LAUNCH_PENDING` before process creation;
2. launch only from the closed `SyncthingCommand` vocabulary;
3. resolve the exact launched child;
4. verify PID, process start time, and executable path;
5. persist the verified identity as `RUNNING`;
6. retain the live `Process` handle while the service instance remains alive;
7. clear the identity record after verified termination.

On service/runtime recovery:

- recover only an exact persisted identity;
- never adopt an ambiguous process;
- never kill a process unless PID/start-time/executable verification matches the persisted record;
- resolve stale `LAUNCH_PENDING` or corrupt state fail-closed according to the shared recovery policy;
- complete recovery before launching another Syncthing process.

The invariant is:

> The selected runtime host owns at most one verified Syncthing process.

The current root-specific process identity classes should be generalized into runtime-neutral components rather than duplicated for normal mode.

### 10.1 Existing identity record compatibility

The staged implementation already has a root process identity record. If implementation renames or relocates that record, it must safely reconcile the existing staged record first so an already-running root Syncthing process cannot become invisible to orphan recovery.

Source-level cleanup is allowed; loss of recovery provenance is not.

## 11. Capability Consumers and Router Removal

After a runtime session is established, execution/state/folder/transfer behavior must not branch on `PREF_USE_ROOT`.

The current application-level parallel structure should be collapsed where privilege routing is its only purpose, including the conceptual roles currently represented by:

- `NormalSyncthingExecutionBackend`;
- `SuperuserSyncthingExecutionBackend`;
- `SyncthingExecutionController`;
- normal/superuser folder-access adapters and router;
- normal/superuser state-access adapters and router;
- normal/superuser state-transfer adapters and router.

Reusable logic from existing normal helpers should move behind the shared service-side capability implementation rather than be rewritten unnecessarily.

Application consumers should depend on privilege-neutral runtime abstractions/session-backed adapters.

`PREF_USE_ROOT` remains relevant for mode transition and session selection only.

## 12. Error Vocabulary and Logging

### 12.1 Shared runtime errors

Generalize common runtime failures into privilege-neutral types, conceptually including:

- `CORE_LAUNCH_FAILED`;
- `CORE_WAIT_FAILED`;
- `CORE_STOP_FAILED`;
- `ORPHAN_RECOVERY_FAILED`;
- `FOLDER_ACCESS_FAILED`;
- `STATE_ACCESS_FAILED`;
- `STATE_TRANSFER_FAILED`;
- `INVALID_REQUEST`;
- `SERVICE_BIND_FAILED` for runtime-service binding/transport establishment failures;
- `BINDER_DIED` for loss of an established runtime Binder;
- `TIMEOUT` for bounded runtime connection/operation waits;
- `INTERNAL_ERROR` for invariant-preserving failures that do not fit a narrower stable code.

### 12.2 Root-only errors

Root-control failures remain explicitly root-specific, including:

- `ROOT_UNAVAILABLE`;
- `UID_VERIFICATION_FAILED`;
- ownership/SELinux repair failures.

### 12.3 Stable diagnostics invariant

Generalizing the Java/AIDL type hierarchy must not degrade logging or qualification evidence.

Stable externally useful identifiers such as `CORE_LAUNCH_FAILED`, `STATE_ACCESS_FAILED`, `ROOT_UNAVAILABLE`, and `UID_VERIFICATION_FAILED` must remain stable unless there is a separately approved reason to change them.

Logs must preserve runtime-mode context, conceptually:

```text
mode=NORMAL error=CORE_LAUNCH_FAILED ...
mode=SUPERUSER error=CORE_LAUNCH_FAILED ...
```

Root authorization failures remain clearly root-specific.

Tests, qualification records, user-visible diagnostics, or log tooling that depend on stable identifiers must continue to work. Internal class/package renames are not themselves a reason to change observable identifiers.

## 13. Superuser Mode Transitions

`SuperuserModeController` remains the only writer of the durable root preference and remains responsible for privilege transitions.

### 13.1 Normal to root

Required ordering:

```text
obtain/resolve root authorization
 -> bind root control
 -> verify UID 0
 -> recover root orphan if present
 -> stop normal runtime/core
 -> commit PREF_USE_ROOT=true
 -> activate root runtime session
 -> restart Syncthing if run conditions require it
```

If preparation fails before the preference commit, normal mode remains configured.

### 13.2 Root to normal

Required ordering:

```text
bind/verify root control
 -> stop root runtime/core
 -> repair app-private ownership/SELinux context
 -> verify normal state access
 -> commit PREF_USE_ROOT=false
 -> release root session
 -> activate normal runtime session
 -> restart Syncthing if run conditions require it
```

If repair or normal-access verification fails, normal mode must not be reported as restored and the durable root preference must remain set.

### 13.3 Root loss

Configured superuser mode must never silently fall back to the normal runtime host after authorization loss, Binder loss, UID verification failure, or root-service failure.

The recently implemented fast root-denial reporting behavior remains a regression requirement.

## 14. Normal Runtime Service Lifetime

`SyncthingRuntimeService` is bound-only.

It must not become a separately started service or foreground service solely to preserve process ownership.

`SyncthingService` remains the lifecycle authority. Shared durable process identity/recovery provides the safety needed if the runtime host must recover prior process ownership before a new launch.

Because the normal runtime host runs in the same app process, application process death naturally destroys both `SyncthingService` and the normal runtime service. The common session abstraction should still be used, but implementation must not invent unnecessary cross-process recovery behavior merely to simulate the root topology.

## 15. Security Model

The unified architecture must preserve or strengthen the current security boundary.

Requirements:

- both runtime services are non-exported;
- root service continues to enforce owning-app caller validation;
- root service continues to verify its own UID 0 state;
- normal runtime service does not expose root-only control operations;
- shared runtime AIDL remains a closed semantic capability contract;
- no arbitrary executable, argument vector, shell string, or unrestricted path API is introduced;
- environment transfer remains structured data, not shell serialization;
- state-file access remains limited to the existing closed state-file vocabulary;
- backup staging remains confined to the fixed transfer location and closed snapshot shape;
- conflict discovery remains constrained to configured Syncthing folders;
- orphan recovery remains exact-identity only.

## 16. Backup and Import

The architectural refactor must not alter backup archive format or user workflow.

Requirements:

- preserve the pre-root export archive shape as the supported format;
- preserve the corrected root export staging behavior;
- preserve the corrected import behavior;
- preserve transactional export semantics;
- preserve fixed closed state snapshot contents;
- use the shared runtime state-transfer capability in both modes;
- do not introduce compatibility handling for previously buggy unreleased exporter shapes.

## 17. Onboarding and UI Behavior

This refactor is internal and must preserve existing user-visible behavior.

In particular:

- root-configured onboarding continues to defer config validation to the service path as already corrected;
- onboarding must not independently authorize or bind root merely to inspect configuration;
- folder/device lists continue to render root authorization/unavailable states without waiting for the bind timeout;
- the settings toggle and restart behavior remain unchanged unless a separately approved UX change is required.

## 18. Testing Strategy

### 18.1 Shared runtime capability tests

Add focused tests proving the shared implementation preserves semantics independent of host privilege for:

- closed core command validation;
- launch/wait/stop/status;
- folder writeability;
- conflict discovery;
- fixed state-file read/write/delete;
- state staging/install;
- failure mapping.

### 18.2 Core supervisor tests

Cover:

- `LAUNCH_PENDING` before process start;
- verified `RUNNING` identity;
- normal exit clearing;
- graceful stop;
- force-stop after exact verification;
- stale process recovery;
- corrupt/mismatched identity refusal;
- refusal to kill an unverified process;
- normal and root host use of the same supervision logic.

### 18.3 Runtime session tests

Cover:

- normal binding selection;
- root binding selection;
- root preflight and authorization resolution;
- UID verification before root runtime exposure;
- timeout handling;
- Binder death;
- stale connection callback rejection;
- reconnect/disconnect behavior;
- one connection owner for root control and runtime;
- no silent mode fallback.

### 18.4 Service host tests

Normal host tests must prove delegation to the shared runtime implementation and non-exported/app-owned access.

Root host tests must prove:

- owning-app caller enforcement;
- UID-0 verification;
- root-only control separation;
- runtime sub-interface exposure;
- delegation to the same shared capability implementation.

### 18.5 Transition tests

Preserve and extend `SuperuserModeController` coverage for exact transition ordering, preference commit points, state repair, normal-access verification, restart behavior, and fail-closed failures.

### 18.6 Structural regression tests

Add tests or static checks sufficient to prove that capability consumers no longer branch on `PREF_USE_ROOT` after the runtime session has been selected.

## 19. Physical Qualification

Because normal execution now crosses the same runtime boundary, qualification must cover both modes.

Required scenarios include:

1. normal startup, stop, and restart;
2. normal force-stop/relaunch and stale-process recovery;
3. superuser enable and initial authorization;
4. superuser startup, stop, and restart;
5. root denial/revocation with immediate unavailable reporting rather than a 60-second spinner;
6. normal -> root transition;
7. root -> normal transition including ownership/SELinux repair;
8. folder writeability in both modes;
9. conflict discovery in both modes;
10. config/state reads and writes in both modes;
11. export in both modes;
12. import in both modes;
13. exact root orphan recovery using PID/start-time/executable identity;
14. exact normal orphan recovery using the same supervision model;
15. existing Syncthing run-condition and service-lifecycle scenarios.

Qualification must preserve the existing rule that evidence recorded before the refactor cannot be represented as a post-refactor physical pass.

## 20. Migration and Cleanup Strategy

This staged implementation is unreleased. The unified architecture should replace, not wrap, the obsolete split implementation.

Therefore:

- remove obsolete normal/root application-level routers/adapters after consumers move to the shared runtime session;
- rename shared runtime parcelables/error types away from `Superuser*` where they are no longer privilege-specific;
- retain `Superuser*` naming only for root authorization, transition, runtime status, and root-only control concepts;
- migrate tests to the new boundaries;
- preserve useful existing implementation helpers by moving them behind shared service-side components;
- do not retain compatibility aliases solely to reduce source churn;
- reconcile any existing staged process-identity record before renaming/removing the root-specific storage path.

No user-data migration is otherwise required.

## 21. Long-term Invariants

The implementation is complete only if all of the following remain true:

1. Both normal and superuser modes use `ISyncthingRuntimeService` for capabilities valid in both modes.
2. Privilege changes the runtime host/effective UID, not the application capability architecture.
3. `SyncthingService` remains the sole authority for whether Syncthing should run.
4. The normal runtime service remains bound-only and in the application process.
5. Exactly one UID-0 RootService exists.
6. Root control and common runtime capabilities are distinct interfaces.
7. Root mode obtains the common runtime interface from the same verified RootService connection.
8. One application-side runtime session owns connection/death/reconnect state.
9. Both modes share service-side runtime capability implementation.
10. Both modes share exact process supervision/orphan recovery semantics.
11. No unverified process may be killed during recovery.
12. No arbitrary shell or filesystem capability is exposed.
13. Root loss never causes normal-mode fallback.
14. `PREF_USE_ROOT` remains the durable configured-mode source of truth and is written only by the transition authority.
15. Shared runtime error type names may change, but stable useful error identifiers and diagnostic context are preserved.
16. Backup/import format and workflow semantics remain unchanged.
17. Existing proven superuser fixes remain regression requirements.
18. After session selection, common runtime capability consumers do not branch on `PREF_USE_ROOT`.

## 22. Success Criterion

The refactor succeeds when the staged superuser implementation's proven functionality is retained while the normal and privileged runtime paths converge on one maintainable architecture:

> For every capability available in both modes, Syncthing Android invokes the same AIDL contract through the same runtime session and shared service-side implementation. Enabling superuser changes only the selected service host, root-control handshake, and effective UID required for that capability.
