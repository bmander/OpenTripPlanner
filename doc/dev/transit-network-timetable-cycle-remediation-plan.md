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

### 3. Break Main Aggregate Coupling (core PR) — DONE

- Branch: `break-network-timetable-coupling`
- Stored `Direction` directly on `TripPattern` (no longer delegates to timetable).
- Pre-computed `tripHeadsign` on `TripPattern` during construction.
- Added `tripsAsStream()` to `Timetable`.
- Added scheduled timetable storage to `TimetableRepository` and `TransitService`.
- Migrated all production and test callers of `pattern.getScheduledTimetable()` and
  `pattern.scheduledTripsAsStream()` to use `TimetableRepository` lookup.
- Decoupled `TripPatternBuilder` from timetable construction.
- Removed `scheduledTimetable` field and all timetable methods from `TripPattern`.
- Removed `Trip` dependency from `TransitGroupPriorityService` by making `EntityAdapter`
  public and moving `TripAdapter` to `model.plan.grouppriority`.
- Grandfathered set reduced from 4 → 0 classes.
- Zero `network -> timetable` imports remain.

### 4. Re-enable Architecture Enforcement — DONE (included in Phase 3)

- Emptied grandfathered set in `TimetableRepositoryArchitectureTest`.
- Removed `TIMETABLE` from `NETWORK` allowed dependencies.
- Re-enabled `enforceNoCyclicDependencies` test (removed `@Disabled`).

### 5. Remove Transitional Compatibility Layer (final PR)

- No transitional compatibility layer was needed; all migration was done in-place.
- Phase effectively complete as part of Phase 3.

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
