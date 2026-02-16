# Transit Model Cycle Remediation Plan (`network` <-> `timetable`)

## Goal

Break the architectural cycle between:

- `org.opentripplanner.transit.model.network..`
- `org.opentripplanner.transit.model.timetable..`

Target dependency direction:

- `timetable -> network` is allowed.
- `network -> timetable` is not allowed.

After remediation, re-enable the cycle test in:

- `application/src/test/java/org/opentripplanner/transit/model/TimetableRepositoryArchitectureTest.java`

## Current Evidence

- Cycle currently acknowledged in test comments and rules:
  - `application/src/test/java/org/opentripplanner/transit/model/TimetableRepositoryArchitectureTest.java:60`
  - `application/src/test/java/org/opentripplanner/transit/model/TimetableRepositoryArchitectureTest.java:92`
- Concrete bidirectional imports exist, e.g.:
  - `application/src/main/java/org/opentripplanner/transit/model/network/TripPattern.java:27`
  - `application/src/main/java/org/opentripplanner/transit/model/timetable/Timetable.java:13`

## Phased Plan

### 1. Lock Architecture Drift (small PR)

- Branch: `lock-network-timetable-drift`
- Add a temporary ArchUnit rule that prevents *new* `network -> timetable` dependencies.
- Keep current functionality intact while we incrementally remove existing edges.
- Update:
  - `application/src/test/java/org/opentripplanner/transit/model/TimetableRepositoryArchitectureTest.java`

### 2. Remove Low-Risk Cross-Package Edges (small PR) — DONE

- Branch: `remove-low-risk-network-timetable-edges`
- ~~Move shared primitives used by both sides out of `timetable` (start with `Direction`).~~
  - `Direction` moved from `timetable` to `basic`.
- ~~Move relation classes that are timetable-centric out of `network`:~~
  - ~~`ReplacedByRelation`~~ — moved to `timetable`.
  - ~~`ReplacementForRelation`~~ — moved to `timetable`.
- Grandfathered set reduced from 6 → 4 classes.
- Objective: reduce cycle surface area before touching core aggregate ownership.

### 3. Break Main Aggregate Coupling (core PR)

- Extract scheduled-time concerns from `TripPattern` into timetable-owned component(s).
- Remove direct `TripPattern` ownership of `Timetable`:
  - `application/src/main/java/org/opentripplanner/transit/model/network/TripPattern.java:86`
- Refactor delegated timetable methods in `TripPattern`, including:
  - `getDirection` (`TripPattern.java:386`)
  - `scheduledTripsAsStream` (`TripPattern.java:396`)
  - `getScheduledTimetable` (`TripPattern.java:410`)
- Refactor `TripPatternBuilder` so it no longer stores/builds timetable internals:
  - `application/src/main/java/org/opentripplanner/transit/model/network/TripPatternBuilder.java:32`
  - `application/src/main/java/org/opentripplanner/transit/model/network/TripPatternBuilder.java:33`

### 4. Re-enable Architecture Enforcement (small PR)

- Remove temporary cycle allowance in:
  - `TimetableRepositoryArchitectureTest.java:60`
  - `TimetableRepositoryArchitectureTest.java:91`
- Re-enable `enforceNoCyclicDependencies`.
- Add explicit rule: `NETWORK` must not depend on `TIMETABLE`.

### 5. Remove Transitional Compatibility Layer (final PR)

- Remove temporary adapters/deprecations introduced during migration.
- Re-run transit + realtime integration tests and speed tests.

## Acceptance Criteria

1. `enforceNoCyclicDependencies` in `TimetableRepositoryArchitectureTest` is enabled and passing.
2. No imports from `org.opentripplanner.transit.model.network..` to `org.opentripplanner.transit.model.timetable..`.
3. Routing and realtime behavior remains unchanged based on existing integration/speed test baselines.

## Risks and Mitigations

- **Risk:** `TripPattern` is central and highly used; refactor may cause broad regressions.
  - **Mitigation:** split step 3 into isolated commits:
    - introduce new API
    - migrate call sites
    - remove legacy field/methods
- **Risk:** external/internal call sites may rely on timetable-facing `TripPattern` methods.
  - **Mitigation:** keep temporary compatibility methods for one transition PR, then remove in step 5.
