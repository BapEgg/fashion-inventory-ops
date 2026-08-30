# Archived Worklog — through Phase 3 input adapter acceptance

이번 승인 이전 원문은
[`../archive/worklogs/2026-08-27-through-phase3-foundation-acceptance.md`](../archive/worklogs/2026-08-27-through-phase3-foundation-acceptance.md)에 보존했다.

## 2026-08-27 — Accept Phase 3 Batch foundation

- Role: Codex verification/review.
- Completed: Verified all three persistence-integrity fixes and their boundary tests; no open
  foundation finding remains.
- Validation: target 25/25; Oracle full 305/305(skip 0); DB-free full 305 total/246 passed/59
  conditional skips; failures/errors 0; `git diff --check` passed.
- Decision: accepted the Phase 3 entity/repository/factory/shared-event foundation. This does not
  accept the still-unimplemented adapter, orchestration, parity wiring or golden Batch test.
- Checkpoint: archived the 133-line current task, implemented snapshot and active worklog; no
  milestone was created because Phase 3 as a whole is not complete.
- Open: input adapter and input-contract validation are the next bounded implementation unit.
- Next: Claude implements [`../state/current-task.md`](../state/current-task.md), records actual
  query counts and regression results, then hands back to Codex.

## 2026-08-27 — Implement Phase 3 Batch input adapter

- Role: Claude implementation.
- New package `com.bapegg.stockpilot.batch` (pure/JDBC-only, no JPA entities):
  `Mvp2InputAdapter` (`@Component`, constructor-injected `JdbcTemplate`) runs exactly seven bulk
  read groups (eight physical statements: 28-day inventory and sales history are structurally
  separate tables within the one "history" group) and assembles an immutable
  `Mvp2InputGraph` (`analysisDate`, `inputSnapshotVersion`, `List<Mvp2Anchor>`,
  `List<DemandEvent>`, `List<Mvp2InboundRow>`, `List<Mvp2OpenTransferRow>`, `List<Mvp2Route>`,
  `Map<Mvp2DonorSkuKey, Long>` active-approved-draft-quantity sum). No query runs inside a
  per-anchor or per-lane loop; no SQL computes a statistic, signal, rate, eligibility or quantity
  -- that stays entirely in the existing pure `demand` package, which this adapter feeds by
  building a real `DemandObservationWindow`/`DailyDemandObservation` per anchor via their own
  canonical constructors (reusing those types' existing validation rather than re-implementing
  it).
- `Mvp2Anchor` carries the analysis-date snapshot position, the full 28-day window, the store's
  owner code, and an always-populated `Mvp2Policy` (`Mvp2Policy.defaults()` when no
  `SP_STORE_SKU_POLICY` row exists, per business-rules.md section 1 -- new constants added to
  `DemandAnalysisRules`). Route defaults were confirmed unreachable in practice: V6 requires
  every route column `NOT NULL` within any row that exists, so a missing route is always
  `ROUTE_NOT_ALLOWED`, never a fallback.
- `InputContractViolationException` (new, `batch` package) is thrown -- with no partial graph
  returned -- for: zero anchor rows, any anchor missing an inventory or sales row for one of its
  28 expected dates (this single check also covers "version mixing" and "future snapshot": both
  are simply invisible to the version/date-scoped history queries, so the day is reported
  missing rather than silently substituted), an internally inconsistent raw row (caught from
  `DailyDemandObservation`'s own canonical-constructor validation and re-thrown with context),
  and a quantity that does not fit a 32-bit int (`NUMBER(10,0)` allows up to ~10 digits; read via
  `getLong` then range-checked before narrowing, rather than trusting a driver-level `getInt`).
- Fixed a real bug found while writing this: an initial `toOffsetDateTime` helper converted via
  `java.sql.Timestamp` first, which silently relabels a `TIMESTAMP WITH TIME ZONE` column's
  actual stored offset (the V6 `Asia/Seoul` backfill) as the JVM/UTC zone -- this would have
  shifted `OffsetDateTime.toLocalDate()` onto the wrong calendar day for any future
  `STALE_INVENTORY` comparison. Fixed by reading via the driver's native JDBC 4.2
  `getObject(column, OffsetDateTime.class)`, which preserves the real offset.
- New Oracle IT `Mvp2InputAdapterIT` (6 tests): a two-anchor happy path asserting every one of the
  seven groups' evidence lands correctly in the graph (including one anchor with a real policy
  row and one relying on defaults) while counting actual `prepareStatement`/`createStatement`
  calls through a `Connection` proxy wrapped around the test's own transactional connection
  (`SingleConnectionDataSource`, `suppressClose=true`, so the counted queries still run inside the
  same rolled-back transaction as the fixture inserts) -- exactly 8 regardless of the 2-anchor
  count, proving no per-anchor loop; zero anchors; one missing inventory-history day; one missing
  sales-history day; a decoy row under a different version plus a decoy row dated on/after
  `analysisDate` (both proving version-mixing/future-snapshot exclusion); and a quantity beyond
  `Integer.MAX_VALUE`. All 6 passed on first real run against Oracle.
- Full regression evidence (real, executed this round):
  - Oracle-backed full Backend build: **311/311**, skip 0, failures/errors 0.
  - DB-free full Backend build: **311 total / 246 passed / 65 Oracle-conditioned skip**,
    failures/errors 0.
  - `git diff --check`: exit 0 (only pre-existing LF/CRLF warnings on already-tracked files).
- No Migration, REST/React, calculation orchestration, output persistence/transaction, or
  approval/`MANUAL` parity code touched -- this round is scoped strictly to the input adapter and
  input-contract validation, per `current-task.md`'s constraint.
- Next: Codex reviews the adapter and its query-count/failure-mode evidence; once accepted, the
  next bounded unit is the orchestration that connects this graph to the pure calculation order
  (`data-model.md` Phase 3 section 4) and persists results.

## 2026-08-27 — Review Phase 3 Batch input adapter

- Role: Codex verification/review.
- Completed: Independently reviewed the eight JDBC statements, graph records and Oracle query-
  count/failure IT; no production code was changed.
- Validation: adapter Oracle target 6/6; Oracle full 311/311(skip 0); DB-free full 311 total/246
  passed/65 conditional skips; failures/errors 0; `git diff --check` passed.
- Decision: changes requested; the adapter is not accepted despite the green baseline.
- Open: future snapshots are not independently rejected, route `NUMBER(10)` values bypass
  checked conversion, active-draft aggregate overflow is returned, and four evidence groups are
  flat lists instead of key-indexed graph data.
- Next: Claude applies only the fixes in [`../state/current-task.md`](../state/current-task.md),
  preserves the 8-statement/no-loop contract, adds boundary ITs, then hands back to Codex.

## 2026-08-27 — Fix Phase 3 input adapter review findings

- Role: Claude implementation.
- Finding 1 (future snapshot not independently rejected): widened the inventory-history query to
  drop its upper date bound (`snapshot_date >= historyStart`, no `<= historyEnd`) -- still one
  physical statement, and it now also surfaces any same-version row dated on/after
  `analysisDate`. `buildAnchor` checks for that before the missing-day loop and rejects it. Also
  added the anchor's own `snapshotAt > analysisReferenceAt` (`analysisDate + 1 day 00:00
  Asia/Seoul`) check, per business-rules.md section 1 -- an old/stale current snapshot still
  passes (that stays a later `STALE_INVENTORY` concern), only one dated *after* the reference
  instant is now rejected as impossible input.
- Finding 2 (route `NUMBER(10,0)` bypassed checked conversion): `lead_time_days`,
  `minimum_quantity`, `package_multiple`, `maximum_quantity` now go through the same
  `getLong` -> `safeInt` path as every other quantity, instead of a raw `getInt`.
- Finding 3 (draft aggregate overflow not blocked): `loadActiveApprovedDraftQuantity` now runs
  the summed `total_quantity` through `safeInt` (validated, though the map keeps the wider
  `Long`) before returning the graph, so a `SUM(quantity)` that itself exceeds `Integer.MAX_VALUE`
  -- even when every individual draft row is in range -- fails before it can reach the pure
  `InventoryProjection` boundary that requires an `int`.
- Finding 4 (flat lists, no key-indexed graph): `Mvp2InputGraph` gained four new immutable indexed
  maps -- `eventsByStoreSku`, `inboundByStoreSku` (new `Mvp2StoreSkuKey`), `openTransfersByLane`
  (new `Mvp2LaneKey`, donor-receiver-SKU), `routesByStorePair` (new `Mvp2StorePairKey`,
  donor-receiver only -- routes are not SKU-scoped). Built via a small `groupBy` helper in the
  adapter (in-memory, no new query) and deep-copied immutable in the record's compact
  constructor (both the outer map and every inner list). The original flat lists are kept for
  compatibility.
- `safeInt` was generalized from separate `storeId`/`skuId` parameters to a single `context`
  string, since routes and the draft sum don't have a SKU-scoped identity the old signature
  assumed.
- Refactored `safeInt`'s call-site helper generalization surfaced no behavior change; confirmed
  by re-running the existing 6 adapter tests unchanged in shape (only the two now-superseded
  assertions in the happy-path test were updated, described below).
- Regression evidence (real, executed this round):
  - `Mvp2InputAdapterIT` now 10/10 (Oracle): the original 6 (one, `aRowUnderADifferentVersion...`,
    renamed and narrowed to version-mixing only, since its old future-date decoy now
    independently triggers finding 1's new check rather than falling through to the missing-day
    path -- a strictly more precise result, not a lost assertion) plus 4 new:
    `futureInventorySnapshotIsIndependentlyRejectedEvenWithACompleteHistory`,
    `impossibleCurrentSnapshotAtAfterAnalysisReferenceAtIsRejected`,
    `aRouteQuantityBeyondTheIntegerRangeThrowsInputContractViolationInsteadOfADriverError`,
    `activeApprovedDraftAggregateBeyondTheIntegerRangeThrowsInputContractViolation`. The
    happy-path test was extended with a second store-SKU/lane's event/inbound/open-transfer/route
    and now asserts each indexed key returns exactly its own evidence, an absent key returns
    `null`, and both the maps and their list values throw `UnsupportedOperationException` on
    mutation -- all while the 8-statement/no-loop count (measured the same way as before, via the
    `Connection.prepareStatement`/`createStatement`-counting proxy) stayed exactly 8 despite the
    much larger fixture, reconfirming no per-row/per-lane loop was introduced.
  - Oracle-backed full Backend build: **315/315**, skip 0, failures/errors 0.
  - DB-free full Backend build: **315 total / 246 passed / 69 Oracle-conditioned skip**,
    failures/errors 0.
  - `git diff --check`: exit 0 (only pre-existing LF/CRLF warnings on already-tracked files).
- No Migration, REST/React, calculation orchestration, output persistence/transaction, or
  approval/`MANUAL` parity code touched -- scoped strictly to the four findings.
- Next: Codex re-verifies the four findings are closed, the 8-statement/no-loop contract still
  holds, and the full 315-test baseline is unchanged; once accepted, the next bounded unit is the
  orchestration connecting this graph to the pure calculation order and persisting results.
