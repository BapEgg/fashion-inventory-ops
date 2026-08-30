# 2026-08 Active Worklog

이번 회전 전 기록은
[`../archive/worklogs/2026-08-27-through-v10-review.md`](../archive/worklogs/2026-08-27-through-v10-review.md)에
원문 그대로 보존했다.

## 2026-08-27 — V10 review and hot-state compaction

- Role: Codex verification/review and knowledge maintenance
- Completed: V10 Migration과 legacy decision Entity 호환 수정을 리뷰하고,
  승인된 감사·오류 계약과 다른 결함을 V11 수정 범위로 인계했다.
- Validation: DB-free Backend 188 total/182 passed/6 Oracle skips; current Oracle V10
  Backend 188/188 passed; `git diff --check` passed.
- Decisions: V10은 이미 적용돼 immutable로 취급하며 미승인 결함은 V11로 고친다.
  Phase/MVP 완료가 아니므로 milestone은 만들지 않았다.
- Open: approval basis, 오류 카탈로그·제약 매핑, 정책 예외 Check, Comment와
  V10/V11 전용 clean/upgrade/constraint 검증.
- Next: Claude가 [`../state/current-task.md`](../state/current-task.md)의 V11만
  구현하고 Codex가 재리뷰한다.

## 2026-08-27 — V11 approval basis and error catalog corrections

- Role: Claude implementation
- Completed: Added `V11__correct_approval_basis_and_error_catalog.sql`, fixing all
  five Codex-reviewed V10 findings without touching V10 itself. `sp_approval_basis`
  gained `basis_contract_version`/`receiver_projected_before_demand`/
  `donor_projected_at_dispatch`/`already_approved_draft_quantity`, and its
  `analysis_run_id` moved from a free-text `VARCHAR2` to a validated `NUMBER(19,0)`
  FK on `sp_analysis_run`; a PL/SQL guard at the top of V11 raises an explicit error
  (rather than silently guessing) if the table is ever found non-empty, since none
  of the new evidence columns can be losslessly backfilled from anything already
  captured. `candidate_eligible_flag` now only allows `'Y'` (an ineligible candidate
  never gets an approval basis to record). `sp_error_catalog` gained
  `title_ko`/`default_detail_ko`/`active_flag`/`updated_at` plus five approval-specific
  codes (`INVALID_REQUEST`, `INVALID_DECISION_REQUEST`, `RECOMMENDATION_NOT_FOUND`,
  `INVALID_DECISION_TRANSITION`, `DECISION_CONFLICT`); `UQ_SP_DEC_REC_SEQ` now maps to
  `DECISION_CONFLICT` instead of `DECISION_ALREADY_TERMINAL`, since a bare sequence
  collision doesn't itself prove a terminal state (that stays a Java-side check).
  Added `ck_sp_dec_policy_exception_scope` restricting `policy_exception_flag='Y'` to
  `MVP-2`+`APPROVED` decisions. Completed every missing Korean comment on V10/V11's
  new tables/columns, including the `sp_approval_basis.approval_basis_id` surrogate
  PK that V10 had left uncommented (caught by cross-checking against how other tables
  comment their own PK, e.g. `sp_analysis_run.analysis_run_id`).
- Completed (new test coverage): added `ApprovalTransactionSchemaIT`
  (`com.bapegg.stockpilot.rebalance`, 6 tests) using raw `JdbcTemplate` SQL --
  no JPA entity exists yet for the new tables, that's the next unit. The whole class
  runs in one `@Transactional` per test method that always rolls back, so every row
  it inserts (including the ones proving a constraint rejects an invalid value) never
  lands in the shared instance. Covers: new-column type/nullable/FK/unique metadata;
  all 13 error-catalog rows' status/retryable/active/title/detail plus both
  constraint-map rows; every new V10/V11 column across the three new/touched tables
  has a Korean comment; `candidate_eligible_flag='N'` rejected; a non-existent
  `analysis_run_id` FK rejected; `policy_exception_flag='Y'` accepted only for an
  `MVP-2`/`APPROVED` decision and rejected for `MVP-2`/`HELD` and for `MVP-1`.
- Validation: hit and fixed a real mid-development snag worth recording -- Oracle DDL
  auto-commits (it is not part of Flyway's migration transaction), so an early V11
  draft that forgot to backfill `sp_error_catalog.updated_at` before its `MODIFY ...
  NOT NULL` failed partway through, leaving the already-run `ALTER TABLE` statements
  committed and a `success=0` row in `flyway_schema_history`. Recovered by manually
  reverting the dev container's partial DDL back to the exact V10 shape (drop/recreate
  `sp_approval_basis`, drop the four half-added `sp_error_catalog` columns, restore the
  `UQ_SP_DEC_REC_SEQ` mapping, delete the failed history row) before re-running the
  corrected script -- documented in `current-task.md` as a "next time" note. After the
  fix: existing `V10`->`V11` upgrade (the always-on dev container) and a **clean
  `V1`->`V11`** run (a throwaway `gvenzl/oracle-free` container on port 15211,
  removed after) both passed `.\backend\gradlew.bat -p backend build --rerun-tasks`
  at 194/194 tests, 0 failures/errors, both Oracle ITs and the new schema IT included
  (no skips), Flyway recording `success=1` for every migration 1-11 on the clean run.
  Confirmed via direct `sqlplus` queries that the schema IT's transactional rollback
  left zero residue (`sp_approval_basis` empty, no leftover test analysis runs or
  decisions, `sp_error_catalog` at exactly 13 rows). `git diff --check` passed.
- Open: JPA mapping (append-only `SpRebalanceDecision`, new `SpTransferDraft`/
  `SpApprovalBasis` entities, MVP-2 field mapping on `SpAnalysisRun`/
  `SpInventorySnapshot`/`SpRebalanceRecommendation`), the `@Transactional` use case,
  REST/error contract, and the real two-transaction concurrency test are all still
  unimplemented.
- Next: Codex re-reviews V11. Only after approval does `implemented-state.md` move
  the V10/V11 section to "accepted" and work proceeds to the JPA mapping unit.

## 2026-08-27 — V11 Codex re-review

- Role: Codex verification/review
- Completed: Re-reviewed V11, its six Oracle schema tests, and the owning approval/error
  contracts. The five prior V10 P1 findings are correctly closed at runtime/schema level.
- Validation: DB-free `backend build --rerun-tasks` — 194 total, 182 passed, 12 Oracle
  skips; existing Oracle — 11 migrations validated and 194/194 passed; throwaway empty
  Oracle — V1→V11 applied and 194/194 passed; `git diff --check` passed.
- Decisions: Removed the review-only Oracle container after the clean run. V10/V11 remain
  immutable because both are already applied.
- Open: three P2 findings remain — V11 comments violate V9 wording rules, schema tests
  under-assert the metadata they claim, and `data-model.md` still labels V10 unimplemented
  and omits V11.
- Next: Claude adds comment-only V12, strengthens exact Oracle readback assertions, and
  synchronizes the physical data-model document before another Codex review.

## 2026-08-27 — Close the three V11 review P2 findings (V12 + tests + docs)

- Role: Claude implementation
- Completed: Added `V12__align_approval_comments_with_v9_style.sql`, replacing every
  V10/V11 new/changed column's Comment with V9's exact convention -- plain columns get
  a concise Korean noun phrase (no English identifiers, no Java symbols, no embedded
  rationale), and enum-like columns get "값: 의미" for every currently allowed value.
  `donor`/`receiver` became `출고 매장`/`입고 매장` (matching `sp_store_transfer_route`'s
  existing terms), the `InventoryProjection.*` Java references were dropped entirely,
  `생성 시각`/`수정 시각` became `생성일시`/`수정일시` (matching every other audit
  timestamp column), and `sp_error_catalog.error_code` now enumerates all 13 codes'
  Korean meaning inline, the same way `sp_metric_quality_flag.flag_code` already
  enumerates its four. No data, constraint, index, or Java-visible behavior changed;
  V10 and V11 are untouched.
- Completed (test coverage): rewrote `ApprovalTransactionSchemaIT`'s two weakest tests.
  `errorCatalogHasExactlyTheApprovedRowsWithExactMetadata` now pins the exact
  `http_status`/`retryable_flag`/`active_flag`/`title_ko`/`default_detail_ko` for all 13
  rows (via an `ExpectedErrorCode` record list) instead of spot-checking a few and
  merely asserting non-null on the rest, and also asserts the catalog has exactly 13
  rows and the constraint map exactly 2. `newColumnsHaveTheCorrectTypeNullabilityAndForeignKeyTarget`
  now additionally resolves `FK_SP_BASIS_ANALYSIS_RUN`'s actual local column and
  referenced table/column, and `UQ_SP_DEC_REQUEST_ID`/`UQ_SP_BASIS_DECISION`'s target
  columns, instead of only counting that a constraint with that name exists. Added
  `newAndChangedColumnsMatchTheExactV9StyleComment`, which pins the exact V12 comment
  string for every corrected table and column.
- Completed (docs): `data-model.md`'s migration table (section 1) now lists `V10`/`V11`/
  `V12` as "구현됨, Codex 최종 승인 대기" instead of omitting them; the two section-7
  headings that said "(V10 계획, 미구현)" now say "(V10/V11 구현됨)" and
  "(V10/V11/V12 구현됨)"; section 9 gained `V11`/`V12` subsections describing what each
  migration actually does, replacing the stale "계획, 미구현" `V10` entry.
- Validation: hit one new mid-development snag -- `COMMENT ON ... IS` only accepts a
  single string literal in Oracle, not a `||`-concatenated expression (`ORA-03048` on
  the first V12 draft's multi-line `error_code` comment); fixed by writing it as one
  literal spanning multiple source lines. Unlike V11's DDL failure, a failed `COMMENT
  ON` script needs no partial-state reversion (comments are idempotently overwritten,
  not additive DDL) -- just delete the failed `flyway_schema_history` row and rerun.
  After the fix: existing `V11`->`V12` upgrade (the always-on dev container) and a
  fresh **clean `V1`->`V12`** run (a throwaway `gvenzl/oracle-free` container on port
  15212, removed after) both passed `.\backend\gradlew.bat -p backend build
  --rerun-tasks` at 195/195 tests, 0 failures/errors, all 12 migrations `success=1` on
  the clean run. DB-free build: 195 total, 182 passed, 13 Oracle-conditioned skips (up
  from 12, the new schema-IT method), 0 failures/errors. `git diff --check` passed.
- Open: JPA mapping (append-only `SpRebalanceDecision`, new `SpTransferDraft`/
  `SpApprovalBasis` entities, MVP-2 field mapping on `SpAnalysisRun`/
  `SpInventorySnapshot`/`SpRebalanceRecommendation`), the `@Transactional` use case,
  REST/error contract, and the real two-transaction concurrency test are all still
  unimplemented.
- Next: Codex re-reviews V12 plus the strengthened tests and synced `data-model.md`.
  Only after approval does `implemented-state.md` move the V10/V11/V12 section to
  "accepted" and work proceeds to the JPA mapping unit.

## 2026-08-27 — V12 Codex re-review

- Role: Codex verification/review
- Completed: Re-reviewed V12, the strengthened seven-test Oracle schema suite, and the
  synchronized physical-model sections. Error metadata, FK target and exact Comment
  readback improvements work as implemented.
- Validation: DB-free Backend build — 195 total/182 passed/13 Oracle skips; existing
  Oracle — 12 migrations validated and 195/195 passed; throwaway empty Oracle —
  V1→V12 applied and 195/195 passed; `git diff --check` passed.
- Decisions: Removed the review-only Oracle container. Applied V10~V12 remain immutable.
- Open: physical-model PK/cardinality facts still contradict the schema; V12 retains
  translatable `BASE`/`Draft` in plain-column comments; unique metadata assertions do
  not confirm constraint type/table.
- Next: Claude adds comment-only V13, corrects the physical model, and strengthens
  unique readback/behavior tests before another Codex review.

## 2026-08-27 — Close the V12 review findings (V13 + unique tests + data-model.md)

- Role: Claude implementation
- Completed (V13 migration): Added `V13__translate_remaining_english_comment_terms.sql`,
  replacing the last two untranslated-English comments V12 left behind. `sp_approval_basis
  .recommended_base_quantity` -> `추천 기준수량` (matching `sp_rebalance_scenario
  .scenario_type`'s existing `BASE: 기준` gloss) and `.already_approved_draft_quantity` ->
  `출고 매장 기승인 활성 이동 초안 합계수량` (matching `sp_transfer_draft`'s table comment
  `재고 이동 초안`) -- found both established Korean terms by searching for the concept's
  existing precedent rather than inventing new wording. No data/constraint/index change;
  V10-V12 untouched.
- Completed (test strengthening): `ApprovalTransactionSchemaIT` gained
  `assertUniqueConstraint(name, table, column)`, which checks `user_constraints
  .constraint_type = 'U'` plus the actual target table and column -- the prior version
  only checked `user_cons_columns` for a matching column name, which would still pass
  for a same-named check constraint or a unique constraint on the wrong table. Added two
  new tests proving the real duplicate-rejection behavior on a live connection:
  `decisionRequestIdRejectsAReusedIdempotencyKeyOnADifferentDecision` and
  `approvalBasisRejectsASecondRowForTheSameDecision`. Updated the exact-comment
  assertions for the two V13-corrected columns.
- Completed (data-model.md physical-model corrections): `SP_APPROVAL_BASIS`'s "Identity"
  column group was documented as `decision_id` PK/FK (implying a shared PK) when the
  actual schema is a separate `approval_basis_id` identity PK plus a `decision_id`
  UNIQUE FK -- fixed. The section-2 ERD still showed `SP_REBALANCE_RECOMMENDATION
  ||--o| SP_REBALANCE_DECISION` (V1-era, at most one decision) even though V6 made it
  append-only 1:N via `decision_sequence`; changed to `||--o{` with a note explaining
  the V6 change (section 3's separate MVP-2 ERD already had this right). A stray
  reference to a nonexistent `SP_REBALANCE_DECISION.approval_basis_id` column (that field
  actually lives on `SP_APPROVAL_BASIS`) was corrected. The error-code list only named
  10 of the 13 real rows, omitting the three legacy fallback codes
  (`VALIDATION_ERROR`/`NOT_FOUND`/`DECISION_ALREADY_TERMINAL`) -- replaced with all 13,
  split into "approval-specific" and "generic fallback" groups matching V12's own
  in-database distinction. Added a `V13` row to the migration table and a `V13`
  subsection to section 9.
- Validation: existing `V12`->`V13` upgrade (always-on dev container) and a fresh clean
  `V1`->`V13` run (a throwaway `gvenzl/oracle-free` container on port 15213, removed
  after) both passed `.\backend\gradlew.bat -p backend build --rerun-tasks` at 197/197
  tests, 0 failures/errors, all 13 migrations `success=1` on the clean run. Direct
  `sqlplus` readback on the clean instance confirmed the two corrected comment strings.
  DB-free build: 197 total, 182 passed, 15 Oracle-conditioned skips (up from 13, the two
  new duplicate-rejection tests), 0 failures/errors. `git diff --check` passed.
- Open: JPA mapping (append-only `SpRebalanceDecision`, new `SpTransferDraft`/
  `SpApprovalBasis` entities, MVP-2 field mapping on `SpAnalysisRun`/
  `SpInventorySnapshot`/`SpRebalanceRecommendation`), the `@Transactional` use case,
  REST/error contract, and the real two-transaction concurrency test are all still
  unimplemented.
- Next: Codex re-reviews V13, the strengthened unique-constraint tests, and the
  corrected `data-model.md`. Only after approval does `implemented-state.md` move the
  V10-V13 section to "accepted" and work proceeds to the JPA mapping unit.

## 2026-08-27 — Accept V10~V13 approval foundation

- Role: Codex verification/review
- Completed: Re-reviewed V13, exact Comment readback, unique metadata/duplicate-write
  tests and corrected physical-model relationships. No functional or Schema finding
  remains. Corrected three trivial stale labels/counts during review.
- Validation: DB-free Backend build — 197 total/182 passed/15 Oracle skips; existing
  Oracle V13 — 13 migrations validated and 197/197 passed; throwaway empty Oracle —
  V1→V13 applied and 197/197 passed; `git diff --check` passed.
- Decisions: Accepted V10~V13 as the persistence foundation. Removed the review-only
  Oracle container; V1~V13 remain immutable.
- Open: JPA mapping, approval transaction/locking, REST error handling and real
  two-transaction concurrency remain unimplemented.
- Next: Claude implements only the JPA persistence mapping and Oracle rollback tests
  described in [`../state/current-task.md`](../state/current-task.md).

## 2026-08-27 — Approval transaction JPA persistence mapping

- Role: Claude implementation
- Completed: Mapped every V6/V10/V11 physical column that had no JPA field yet, without
  touching any migration. `SpRebalanceDecision`'s relationship to
  `SpRebalanceRecommendation` changed from `@OneToOne` to `@ManyToOne`, since V6 dropped
  `uq_sp_dec_rec` (unique on `recommendation_id` alone) in favor of append-only
  `uq_sp_dec_rec_seq (recommendation_id, decision_sequence)`. Added `decisionSequence`/
  `decisionContractVersion`/`reasonCode`/`recommendationVersion` (V6) and fixed
  `selectedQuantity`/`reason`'s `nullable=false` (V6 made both columns nullable for
  PENDING/HELD/REJECTED/EXPIRED rows). The existing 5-arg MVP-1 constructor fills the
  four new fields with the exact values their DB `DEFAULT`s already produced before
  they were mapped (`1`/`"MVP-1"`/`null`/`1`), so the existing create path is
  byte-for-byte unchanged. Added
  `findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc` to the
  repository for reading an append-only history, with a javadoc warning that the
  existing `findByRecommendation_RecommendationId` (still `Optional`-returning) would
  throw once a real multi-decision write path exists.
- Completed (new entities): Added `SpTransferDraft` and `SpApprovalBasis`
  (`com.bapegg.stockpilot.rebalance`) plus their repositories, mapping V6's and
  V10/V11's final table shapes in full, including `sp_approval_basis.analysis_run_id`'s
  V11 change to a `NUMBER(19,0)` FK. Both are 1:1 with a decision via a unique FK.
  `SpApprovalBasis.candidateEligibleFlag` is fixed to `"Y"` in the constructor (never a
  caller-supplied parameter) since V11's `ck_sp_basis_eligible` only allows `Y` --
  ineligibility is decided upstream by the pure-Java `ApprovalRequestValidation`, which
  never produces a row here for an ineligible candidate; this entity only persists the
  outcome. Added `CandidateStatus`/`RecommendationMode`/`DraftStatus` enums matching
  the corresponding `V6` check constraints.
- Completed (existing entities): `SpAnalysisRun` gained `inputSnapshotVersion` (V6,
  constructor defaults it to the same `"MVP-1-LEGACY"` literal V6 used for backfill and
  as the column's DB default). `SpInventorySnapshot` gained `snapshotAt`/
  `outOfStockFlag`/`inputSnapshotVersion` (V6) as `insertable=false, updatable=false`
  read-only fields, since nothing writes a new row through this entity yet -- real
  MVP-2 snapshot ingestion is separate, later work. `SpRebalanceRecommendation` gained
  `routeId`/`candidateStatus`/`candidateVersion`/`recommendationMode`/
  `projectedReceiverAtArrival`/`projectedDonorAtDispatch`/`receiverCapacityRemaining`/
  `evaluatedAt` (V6) and fixed the same nullable-quantity mismatch as the decision
  entity. `routeId` is a plain nullable FK id, not a `@ManyToOne` to a new
  `sp_store_transfer_route` entity, since no Java code joins across that relationship
  yet -- introducing it is deferred until something needs it.
- Completed (test coverage): Added `ApprovalPersistenceMappingIT` (4 tests,
  `com.bapegg.stockpilot.rebalance`, one `@Transactional` rollback per test). Verifies:
  the MVP-1 create path round-trips every new column's DB-default value correctly
  through Hibernate (using `entityManager.flush()`/`.clear()` to force a real Oracle
  round-trip -- without it, Hibernate's first-level cache returns the same in-memory
  instance for a PK already in the session, which would never pick up
  `decided_at`/`evaluated_at`'s server-generated `DEFAULT SYSTIMESTAMP` and made the
  first draft of this test fail); one recommendation can carry an append-only decision
  history (a second decision row is inserted directly via `JdbcTemplate` at
  `decision_sequence = 2`, since no Java code builds a non-MVP-1-shaped decision yet,
  then read back through the new repository method); `SpApprovalBasis`/`SpTransferDraft`
  each save and read back 1:1 with a decision; and the existing Golden Scenario
  inventory snapshot's new V6 columns are readable without error.
- Validation: existing Oracle (the always-on dev container, already at V13) --
  `.\backend\gradlew.bat -p backend build --rerun-tasks` -- `BUILD SUCCESSFUL`,
  201/201 tests (up from 197), 0 failures/errors; Hibernate's `ddl-auto=validate`
  accepted every new/changed mapping against the real schema on the first attempt
  (each column's exact type/nullable/length was read directly from the migration SQL
  before writing the `@Column` annotation). DB-free build: 201 total, 182 passed, 19
  Oracle-conditioned skips (up from 15, the four new mapping tests), 0 failures/errors.
  No migration changed this round, so a clean `V1`->`V13` re-run was skipped --
  Hibernate validate only compares against the live schema catalog, which the V13 round
  already confirmed is identical on the clean and upgrade paths. `git diff --check`
  passed.
- Open: the `@Transactional` approval use case (idempotency-key handling, donor-row
  locking, current-basis recomputation, atomic decision/basis/draft insert), the
  REST/error contract, and the real two-transaction concurrency test are all still
  unimplemented -- none of the new entities/repositories are called by any service yet.
- Next: Codex reviews this persistence-mapping unit. Only after approval does
  `current-task.md` define the next scope (the transactional use case itself).

## 2026-08-27 — Reject approval persistence mapping review

- Role: Codex verification/review
- Completed: Checked the changed entities, repositories and four Oracle mapping tests
  against V6/V10/V11 and the approved decision/draft rules; implementation code was not
  changed.
- Validation: targeted `ApprovalPersistenceMappingIT` 4/4 passed; existing Oracle V13
  full Backend build independently passed 201/201 with 0 failures/errors/skips;
  `git diff --check` passed before the state-only handoff edits.
- Decisions: Rejected the mapping unit with two P1 findings: the persistence
  `DecisionStatus` cannot read V6 `PENDING/HELD/EXPIRED` rows, and the retained singular
  decision query throws once valid append-only history has multiple rows instead of
  selecting the largest sequence. Expanding the enum must not accidentally broaden the
  current MVP-1 REST request contract.
- Open: P2 — `SpTransferDraft` promises `CREATED`→`READY`, but exposes no transition and
  maps `updated_at` read-only while V6 supplies only an insert default, not an update
  trigger. Current tests cover none of these three cases.
- Next: Claude applies only the corrections and regression tests specified in
  [`../state/current-task.md`](../state/current-task.md), then requests Codex re-review.

## 2026-08-27 — Fix the three JPA persistence mapping review findings

- Role: Claude implementation
- Completed (P1 -- incomplete decision-status mapping): Widened `rebalance
  .DecisionStatus` from `APPROVED`/`REJECTED` to all five physical states
  (`PENDING`/`HELD`/`APPROVED`/`REJECTED`/`EXPIRED`), matching `V6`'s
  `ck_sp_dec_mvp2_shape`, so Hibernate can deserialize any row a future append-only
  writer produces. Widening the enum alone would have silently widened
  `POST /api/rebalancing-decisions`'s accepted values too (it binds
  `RebalanceDecisionRequest.decisionStatus` directly to this enum), so
  `RebalanceDecisionService.decide` now explicitly rejects anything other than
  `APPROVED`/`REJECTED` with 400 before doing anything else -- the MVP-1 REST contract
  stays exactly as narrow as before.
- Completed (P1 -- single-result query conflicts with 1:N): Removed
  `SpRebalanceDecisionRepository.findByRecommendation_RecommendationId` (an
  `Optional`-returning derived query that would throw
  `IncorrectResultSizeDataAccessException` the moment a recommendation has more than
  one decision) and replaced it with two purpose-built methods:
  `existsByRecommendation_RecommendationId` for the MVP-1 duplicate-decision guard
  (now used in `RebalanceDecisionService.decide`), and
  `findFirstByRecommendation_RecommendationIdOrderByDecisionSequenceDesc` for "the
  current decision" (now used in `InventoryExceptionService.toRecommendationView`,
  which previously read the wrong thing once a second decision existed). Also fixed
  `ApiGoldenScenarioIT.deleteAnalysisRun`'s cleanup helper, which called the removed
  method, to delete every decision for a recommendation via the existing
  `findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc` instead of just
  one.
- Completed (P2 -- transfer draft update-audit gap): Added `SpTransferDraft.markReady()`
  -- the one `CREATED` -> `READY` transition MVP-2 implements (throws
  `IllegalStateException` from any other state) -- which explicitly stamps `updatedAt`
  with the current time, since `V6` gives `updated_at` an insert-time `DEFAULT` but no
  update trigger. Changed the column mapping from `insertable=false, updatable=false` to
  `insertable=false` only, so the entity itself now owns the update responsibility
  `markReady()` documents.
- Completed (regression tests): Added
  `ApiGoldenScenarioIT.decisionWorkflowRejectsNonMvp1DecisionStatuses` (hits the real
  `POST /api/rebalancing-decisions` with `PENDING`/`HELD`/`EXPIRED`, expects 400 for
  each, and confirms no decision row was persisted). Extended
  `ApprovalPersistenceMappingIT.oneRecommendationCanHaveAnAppendOnlyDecisionHistory` to
  insert `REJECTED`/`HELD`/`EXPIRED` rows via raw SQL (not just `REJECTED` as before)
  and assert `findFirstBy...Desc` returns the highest-sequence row while
  `existsBy...` still returns `true` across all four rows. Added
  `transferDraftMarkReadyTransitionsStatusAndStampsUpdatedAt`, which round-trips a
  draft through Oracle before and after `markReady()`, asserts `updated_at` actually
  changed (compared for inequality rather than ordering, since the JVM and the Oracle
  container do not share a clock), and asserts a second `markReady()` call throws.
- Validation: existing Oracle (the always-on dev container, already at V13) --
  `.\backend\gradlew.bat -p backend build --rerun-tasks` -- `BUILD SUCCESSFUL`,
  203/203 tests (up from 201), 0 failures/errors, on the first attempt. DB-free build:
  203 total, 182 passed, 21 Oracle-conditioned skips (up from 19), 0 failures/errors.
  Confirmed via `sqlplus` that the new REST regression test's `finally`-block cleanup
  left no residual test data. No migration changed this round, so a clean `V1`->`V13`
  re-run was skipped for the same reason as the prior round. `git diff --check` passed.
- Open: the `@Transactional` approval use case, donor-row locking, current-basis
  recomputation, atomic decision/basis/draft insert, the REST/error contract, and the
  real two-transaction concurrency test are all still unimplemented -- none of the new
  entities/repositories are called by any service yet.
- Next: Codex re-reviews this correction round. Only after approval does
  `current-task.md` define the next scope (the transactional use case itself).

## 2026-08-27 — Accept corrected approval persistence mapping

- Role: Codex verification/review
- Completed: Re-reviewed the three prior findings against the actual V6 contract and
  production call paths. The five-state persistence enum keeps the MVP-1 write endpoint
  restricted to two states; duplicate detection and latest-decision lookup are split;
  draft `CREATED`→`READY` now owns its audit timestamp. No new mapping finding remains.
- Validation: Oracle-focused `ApprovalPersistenceMappingIT` + `ApiGoldenScenarioIT`
  passed 11/11; existing Oracle V13 full Backend build passed 203/203 with zero skips;
  DB-free build passed 182 and conditionally skipped 21 Oracle tests; failures/errors 0;
  `git diff --check` passed.
- Decisions: Accepted the JPA persistence mapping. The current two-state Frontend is not
  widened in this unit because MVP-2 REST/React application wiring is explicitly later;
  that wiring must handle `HELD` as non-terminal and all Backend response states.
- Open: Approval transaction service, locking/recalculation, atomic persistence,
  ProblemDetail mapping and real two-transaction concurrency remain unimplemented.
- Next: Codex specifies that bounded transaction use case before Claude implements it,
  as summarized in [`../state/current-task.md`](../state/current-task.md).

## 2026-08-27 — Specify the approval transaction use case

- Role: Codex planning/design
- Completed: Reconciled the accepted persistence model with Section 10 rules and fixed
  an implementation-ready transaction contract in
  [`../state/current-task.md`](../state/current-task.md).
- Validation: document hot-state budget and `git diff --check` passed; no executable
  code changed, so application tests were not rerun.
- Decisions: Preserve the MVP-1 REST endpoint in this unit; implement an application
  facade/executor only. Use recommendation-first then donor-snapshot native locks,
  SHA-256 of a reconstructable normalized command without a new DB column, and a
  post-rollback winner lookup for request-key insert races.
- Open: The transaction service, approval-basis loader/recalculation, persistence error
  translator, and independent Oracle concurrency tests remain unimplemented.
- Next: Claude implements exactly the current-task scope and records actual targeted,
  Oracle-full, and DB-free test results for Codex re-review.

## 2026-08-27 — Implement the approval transaction use case

- Role: Claude implementation
- Completed: Implemented Codex's confirmed section-10 transaction spec in full. New
  `com.bapegg.stockpilot.approval` package: `ApprovalTransactionCommand` (NFC-normalizes
  and shape-validates every field, rejects anything but `HELD`/`APPROVED`/`REJECTED`),
  `IdempotencyFingerprint` (SHA-256 over a fixed-order canonical encoding, no new DB
  column), `ApprovalTransactionResult`, `ApprovalErrorCode`, `ApprovalTransactionException`,
  `PersistenceErrorTranslator` (lock timeout / Oracle constraint name / connection failure
  -> stable codes, never leaking raw SQL text), `ApprovalTransactionReader` (read-only key
  pre-check, its own `@Transactional(readOnly=true)` bean), `ApprovalTransactionExecutor`
  (the `@Transactional` write path: recommendation lock first, donor snapshot lock second
  and only for `APPROVED`, append-only decision + atomic basis/draft insert),
  `ApprovalTransactionFacade` (the orchestrating entry point, including the
  cross-recommendation `UQ_SP_DEC_REQUEST_ID` race retry-as-replay path). New pure
  `demand.ApprovalBasisRecalculation`, re-deriving BASE quantity/donor-transferable/
  route-and-capacity limits from freshly locked data using the same formula
  `TransferScenarioSet`'s BASE scenario already uses.
- Completed (supporting entities/repositories, none previously existed): `SpStoreTransferRoute`,
  `SpStoreSkuPolicy`, `SpOpenTransfer`, `SpInboundSchedule` (all matching V6's DDL exactly),
  and a read-only `SpErrorConstraintMap`/`SpErrorConstraintMapRepository` over
  `sp_error_constraint_map`. Added `PESSIMISTIC_WRITE` + 3-second
  `jakarta.persistence.lock.timeout` `lockById` queries to `SpRebalanceRecommendationRepository`
  and `SpInventorySnapshotRepository` -- the first pessimistic locking anywhere in this
  codebase, confirmed translating to Oracle's `FOR UPDATE WAIT 3` with no native SQL needed.
- Completed (small pre-existing-entity gaps found while wiring the recalculation, all
  in-scope per current-task.md's "새 Java 조합 로직" allowance): `SpInventoryMetric` had no
  mapping at all for V6's `base_demand_rate`/`high_demand_rate` -- added both fields plus
  `applyDemandRates(...)`. `SpStore` had no `inventory_owner_code` mapping -- added
  read-only. `SpRebalanceRecommendation.routeId` had no post-construction setter -- added
  `assignRoute(Long)`. `SpAnalysisRun`'s only constructor hardcoded the MVP-1 legacy input
  snapshot version -- added a 3-arg overload for a real MVP-2 run. `SpRebalanceDecision`
  gained a static factory `createMvp2Decision(...)` (no existing constructor could build an
  MVP-2-shaped decision row). `SpRebalanceDecisionRepository` gained
  `findByDecisionRequestId`; `SpTransferDraftRepository` gained a donor+SKU active-quantity
  sum query.
- Completed (three bugs self-caught while writing, before any test run): (1) an early draft
  of `replayOrThrowConflict` unconditionally threw `IDEMPOTENCY_KEY_REUSED` regardless of
  whether the fingerprint actually matched -- fixed by exposing
  `ApprovalTransactionReader.toLookup` as `public` so the executor reuses the exact same
  reconstruction-and-compare logic instead of duplicating it. (2) The executor originally
  translated every `DataIntegrityViolationException` (including `UQ_SP_DEC_REQUEST_ID`)
  before the facade's dedicated retry-as-replay catch block could ever see it -- fixed by
  adding a specific untranslated-rethrow branch for that one constraint name, ahead of the
  generic translation branch. (3) `ApprovalRequestValidation.validate` can throw a raw
  `IllegalArgumentException` (its own documented contract, for a changed quantity or policy
  exception without a reason) which is not a `DataAccessException` and would have escaped
  the executor's catch blocks untranslated -- fixed by wrapping just that call site and
  re-throwing as `INVALID_DECISION_REQUEST`.
- Completed (unit tests, all passing): `IdempotencyFingerprintTest` (6),
  `ApprovalTransactionCommandTest` (11), `ApprovalBasisRecalculationTest` (8).
- Completed (Oracle IT, `ApprovalTransactionExecutorIT`, 9/9 passing): single HELD/
  REJECTED/APPROVED decisions, HELD-then-APPROVED append-only sequence 2 with basis+draft,
  terminal-recommendation rejection with no row written, stale-version-mismatch rejection
  with no row written, a quantity exceeding the recalculated donor-transferable ceiling
  rejected as stale with no row/basis written, same-key-same-payload replay without a new
  row, same-key-different-payload rejected as `IDEMPOTENCY_KEY_REUSED`. Hit one fixture bug
  mid-round: `ruleVersion = InventoryAnalysisRules.RULE_VERSION + "-APPROVAL-TX-IT" + suffix`
  exceeded Oracle's `VARCHAR2(32 CHAR)` limit for the two longest per-test suffixes
  (`ORA-12899`, actual 39 vs max 32) -- fixed by shortening the fixed infix to `-ATXIT`.
- Completed (real two-transaction concurrency IT, new `ApprovalTransactionConcurrencyIT`,
  4/4 passing, deliberately NOT `@Transactional` at the class level since it needs genuinely
  committed fixture data visible across separate real transactions/threads, with manual
  `finally`-block cleanup like `ApiGoldenScenarioIT`): (1) two threads calling the facade
  with the same key and the same command converge on one decision row and the same decision
  id (one `created=true`, one a replay); (2) two threads with the same key but different
  recommendations (and therefore different fingerprints) -- exactly one wins, the other is
  rejected `IDEMPOTENCY_KEY_REUSED`, with the Oracle log confirming the real
  `UQ_SP_DEC_REQUEST_ID` violation path actually fired (not just the pre-check short-circuit);
  (3) two receivers sharing one physical donor snapshot/metric row, each independently
  wanting 8 units against a donor with only 10 transferable -- the donor row's pessimistic
  lock serializes the two approvals, the first commits its draft, the second recalculates
  against that now-committed draft (transferable drops to 2) and is rejected
  `STALE_RECOMMENDATION`; (4) a `TransactionTemplate`-held donor-row lock from a separate
  thread, kept past 3 seconds, causes the waiting facade call to fail
  `APPROVAL_LOCK_TIMEOUT`, with the Oracle log confirming a real `ORA-00054`/`ORA-40097`.
- Validation: existing Oracle (always-on dev container, still at V13 -- no migration
  changed) -- `.\backend\gradlew.bat -p backend build --rerun-tasks` -- `BUILD SUCCESSFUL`,
  241/241 tests (up from 203), 0 skipped, 0 failures/errors. DB-free build: 241 total, 207
  passed, 34 Oracle-conditioned skips (up from 21), 0 failures/errors. The existing MVP-1
  REST regression (`ApiGoldenScenarioIT`, `InventoryAnalysisGoldenScenarioIT`) is included
  in and passed within this same Oracle run. `git diff --check` passed.
- Open: REST controller/DTO/`ProblemDetail` wiring for this application API is explicitly
  out of scope for this unit (per current-task.md) and remains unimplemented, along with
  MVP-2 Batch/React application wiring and a real LLM provider adapter.
- Next: Codex independently verifies transaction atomicity, lock order, the replay/conflict
  races, the basis recalculation, and the full regression. Only after approval does
  `implemented-state.md` move this section to "accepted".

## 2026-08-27 — Request approval transaction corrections

- Role: Codex verification/review
- Completed: Reviewed the application transaction, lock/replay paths, basis reload,
  persistence translation and Oracle tests against the confirmed contract.
- Validation: targeted approval/recalculation tests 38/38; Oracle full Backend build
  241/241(skip 0); DB-free build 241 total/207 passed/34 skip; failures/errors 0;
  `git diff --check` passed. The first sandboxed runs failed only on Gradle cache access,
  then the same commands passed outside that sandbox.
- Decisions: Approval is withheld. Route lane/version and donor metric/snapshot identity
  are not cross-validated; mapped constraint lookup occurs in the failed JPA transaction;
  required mid-write rollback and detailed persistence-translation tests are absent.
- Open: Three correction groups and their exact completion tests are owned by
  [`../state/current-task.md`](../state/current-task.md).
- Next: Claude fixes only those groups; Codex reruns the same targeted and full suites.

## 2026-08-27 — Fix the three approval transaction review findings

- Role: Claude implementation
- Completed (finding 1 -- missing cross-identity validation): `ApprovalTransactionExecutor
  .recalculate` trusted `SpRebalanceRecommendation`'s receiver/donor metric FKs and
  `route_id` FK without ever confirming they actually agree with each other or with the
  request -- the DB does not enforce that. Added checks, alongside the existing SKU/
  direction/route-active checks: the donor metric's own `SpAnalysisRun` id must equal the
  receiver's; both the receiver's and the donor's `SpInventorySnapshot.inputSnapshotVersion`
  must equal the command's; and the resolved route's `donorStoreId`/`receiverStoreId`/
  `inputSnapshotVersion` must equal the actual recalculation donor/receiver/command
  version. Any mismatch throws `STALE_RECOMMENDATION` with no row written, same as every
  other staleness check. Extended `ApprovalTransactionExecutorIT`'s fixture builder into a
  general `setUpCustomFixture(...)` (nullable/false-default overrides for route donor,
  route receiver, route input version, a separate donor analysis run, and per-snapshot
  input version), then added 6 new tests -- `wrongRouteDonorIsRejectedAsStale...`,
  `wrongRouteReceiverIsRejectedAsStale...`, `wrongRouteInputVersionIsRejectedAsStale...`,
  `donorMetricOnADifferentAnalysisRunIsRejectedAsStale...`,
  `receiverSnapshotWithAMismatchedInputVersionIsRejectedAsStale...`,
  `donorSnapshotWithAMismatchedInputVersionIsRejectedAsStale...` -- each corrupting exactly
  one identity fact and asserting `STALE_RECOMMENDATION` plus zero decision/basis rows
  (9 -> 15 tests in that file).
- Completed (finding 2 -- constraint translation inside the failed session):
  `ApprovalTransactionExecutor.execute` used to catch `DataIntegrityViolationException`
  after a flush failure and immediately call `PersistenceErrorTranslator.translate(e)` --
  which queries `sp_error_constraint_map` through a repository call -- inside that same,
  now-broken `@Transactional` persistence context, before the surrounding proxy had even
  finished rolling back. Changed the catch block to always rethrow every
  `DataIntegrityViolationException` raw (removing the old `UQ_SP_DEC_REQUEST_ID`-only
  special case, since every constraint now takes the same path), letting the
  `@Transactional` proxy complete its rollback first; `ApprovalTransactionFacade`'s
  existing winner-reread branch and its own `errorTranslator.translate(e)` call already ran
  after the executor's transaction was fully closed, in a separate, guaranteed-fresh
  transaction, so no facade change was needed. Lock-timeout classification stays inside the
  executor's own catch, since it is pure exception-type/message inspection with no DB round
  trip. Added `PersistenceErrorTranslatorTest` (9 tests, a mocked
  `SpErrorConstraintMapRepository`, no Spring context): schema-qualified constraint
  extraction from a realistic Oracle message, absent-constraint handling, a mapped
  constraint translating to its catalog code, an unmapped constraint falling back to
  `INTERNAL_SERVER_ERROR` without ever calling the map for a message with no constraint,
  known lock-exception types and an Oracle `ORA-00054` message pattern both classified as
  `APPROVAL_LOCK_TIMEOUT` before constraint classification, connection failures mapped to
  `PERSISTENCE_UNAVAILABLE`, and confirmation that the translated exception's own message
  never contains the raw `ORA-` text or constraint name.
- Completed (finding 3 -- untested failure atomicity and concurrency assertions): Added
  `ApprovalTransactionAtomicityIT` (2 tests) using `@MockitoSpyBean` to wrap the real
  `SpApprovalBasisRepository`/`SpTransferDraftRepository` beans and inject one failure at
  exactly one write step -- a genuine Spring bean substitution, no production test flag or
  branch. `draftSaveFailureRollsBackTheAlreadySavedDecisionAndBasis` throws a
  `DataIntegrityViolationException` carrying a real `UQ_SP_DEC_REC_SEQ` constraint message
  from the draft save (after decision and basis already succeeded), asserting the result is
  `DECISION_CONFLICT` -- not a raw exception -- and that decision/basis/draft are all
  absent afterward, which doubles as finding 2's required "rollback-then-translate, not a
  raw persistence exception" integration proof. `basisSaveFailureRollsBackTheAlreadySavedDecisionAndWritesNoDraft`
  fails at the basis save instead, expecting the translator's unmapped-constraint fallback
  `INTERNAL_SERVER_ERROR`, with the same all-rows-absent assertion. Hit and fixed one real
  test-design bug: with `@Transactional` at the class level (this file's first draft), the
  decision row's `GenerationType.IDENTITY` insert flushes to Oracle immediately, so it
  stayed visible to a same-transaction read right after the later save failed -- the
  physical `ROLLBACK` only happens when the *outermost* `@Transactional` boundary
  completes, and with the test method as that boundary, the assertions ran too early and
  failed. Fixed by removing the class-level `@Transactional` (matching
  `ApprovalTransactionConcurrencyIT`'s reasoning) so `ApprovalTransactionExecutor.execute`'s
  own `@Transactional` becomes the outermost boundary for the call under test, and adding
  manual fixture `finally`-block cleanup instead. Also strengthened all four existing
  `ApprovalTransactionConcurrencyIT` tests, which previously only counted successes/errors,
  with exact per-recommendation decision/basis/draft row counts and draft quantity sums --
  the winner has exactly 1/1/1 and the approved quantity, the loser has exactly 0/0/0, and
  (for the shared-donor test) the two recommendations' combined draft quantity equals only
  the single winner's amount, never both approvals' quantities added together.
- Validation: existing Oracle (always-on dev container, still at V13 -- no migration
  changed) -- `.\backend\gradlew.bat -p backend build --rerun-tasks` -- `BUILD SUCCESSFUL`,
  258/258 tests (up from 241), 0 skipped, 0 failures/errors. DB-free build: 258 total, 216
  passed, 42 Oracle-conditioned skips (up from 34), 0 failures/errors. Targeted reruns:
  `ApprovalTransactionExecutorIT` 15/15, `PersistenceErrorTranslatorTest` 9/9,
  `ApprovalTransactionAtomicityIT` 2/2, `ApprovalTransactionConcurrencyIT` 4/4 (all with the
  strengthened assertions). `git diff --check` passed.
- Open: REST controller/DTO/`ProblemDetail` wiring remains explicitly out of scope for this
  unit; MVP-2 Batch/React application wiring and a real LLM provider adapter are unrelated,
  still-unimplemented later work.
- Next: Codex re-reviews the same three findings and the full regression. Only after
  approval does `implemented-state.md` move this section to "accepted".

## 2026-08-27 — Approval transaction corrections accepted

- Role: Codex verification/review.
- Reviewed the corrected cross-identity checks, rollback-before-constraint-translation
  boundary, failure atomicity tests and strengthened concurrency assertions. The three
  prior findings are closed and no new actionable finding remains.
- Independent validation: targeted Oracle 30/30, existing Oracle full Backend build
  258/258(skip 0), DB-free build 258 total/216 passed/42 Oracle-conditioned skips,
  failures/errors 0, and `git diff --check` passed.
- Decision: the approval `@Transactional` application API is accepted. REST/
  `ProblemDetail`/React wiring remains outside this unit.
- State handoff: compacted `implemented-state.md` to final observable behavior and moved
  `current-task.md` to Codex planning/design for the `MANUAL` quantity-test contract.
- Next: specify and verify `MANUAL` quantity testing before any screen wiring.

## 2026-08-27 — Specify MANUAL quantity testing

- Role: Codex planning/design.
- Completed: Fixed the implementation-ready application contract for side-effect-free
  manual transfer-quantity testing in [`../state/current-task.md`](../state/current-task.md).
- Validation: Cross-checked the contract against the accepted approval transaction,
  pure scenario/approval calculations, current MVP-1 simulation API and V13 error catalog;
  `git diff --check` passed.
- Decisions: Input is quantity, never demand rate; no silent rounding; return every hard-
  constraint violation and a lower feasible suggestion; use the same short recommendation
  → donor lock order and basis loader as approval, but persist nothing and revalidate on approval.
- Open: Implementation and Oracle/DB-free verification; REST/ProblemDetail/React remain later.
- Next: Claude implements only the specified pure/application boundary and hands it to Codex.

## 2026-08-27 — Implement `MANUAL` quantity testing

- Role: Claude implementation
- Completed (shared extraction, no behavior change): Added pure `demand.TransferEffectProjection`,
  extracting the before/after available/coverage/risk formula out of
  `TransferScenarioSet.Sizing.build` so the four automatic scenarios and the new manual test
  never duplicate it; `TransferScenarioSet` now delegates to it and produces byte-identical
  output (confirmed by `TransferScenarioSetTest`'s existing 21 tests passing unchanged). Fixed
  `TransferScenarioType`'s stale Javadoc (it used to say a future `MANUAL` increment would reuse
  a user-supplied *rate* -- the actual contract is a user-supplied *quantity*). Added new
  `approval.CurrentApprovalBasisLoader`, extracting `ApprovalTransactionExecutor`'s former
  `recalculate`/`validateRecommendationIsCurrent` private methods verbatim: `validateCurrent`
  (version/current-run check) and `load` (donor snapshot lock, route/policy/inbound/open-transfer/
  draft queries, every identity cross-check) now live there and return a pure
  `LoadedApprovalBasis` record (primitives and `demand` types only, no JPA entity). Refactored
  `ApprovalTransactionExecutor` to call the loader instead of its own copies; its constructor
  dropped five repositories it no longer touches directly (`snapshotRepository`,
  `analysisRunRepository`, `routeRepository`, `policyRepository`, `openTransferRepository`,
  `inboundScheduleRepository`, `storeRepository` -- all now loader-only). Added
  `DecisionStatus.isTerminalForFurtherDecision()` (true for `APPROVED`/`REJECTED`/`EXPIRED`) so
  both executors share one definition of "terminal" instead of each re-declaring the same
  `PENDING`/`HELD`-allowed check. Added `ApprovalErrorCode.INVALID_REQUEST` (a V11-seeded
  `sp_error_catalog` code that had no Java constant yet).
- Completed (new pure calculation): Added `demand.ManualQuantityViolation` (6-value fixed-order
  enum: `CANDIDATE_INELIGIBLE`/`BELOW_ROUTE_MINIMUM`/`NOT_PACKAGE_MULTIPLE`/
  `EXCEEDS_DONOR_TRANSFERABLE`/`EXCEEDS_ROUTE_MAXIMUM`/`EXCEEDS_RECEIVER_CAPACITY`),
  `demand.ManualQuantityProjection` (before/after/coverage/risk/evidence, same shape as
  `TransferScenarioResult` minus its scenario-specific fields), and
  `demand.ManualQuantityEvaluation.calculate` -- calls the existing
  `ApprovalBasisRecalculation.calculate` directly (never duplicates its formula), checks the
  requested quantity against all six violations in fixed order, computes
  `hardCeiling -> floor to package -> zero if below minimum` for both
  `maximumFeasibleQuantity` and `suggestedQuantity` per the confirmed contract, and builds a
  `ManualQuantityProjection` via `TransferEffectProjection` only when the result is feasible.
- Completed (new application layer): `approval.ManualQuantityTestCommand` (recommendationId/
  analysisRunId/candidateVersion/requestedQuantity positive, inputSnapshotVersion 1..64/
  ruleVersion 1..32 NFC-normalized via the existing `IdempotencyFingerprint.normalize`, no actor/
  reason/status/policyException/idempotency-key -- shape violations throw `INVALID_REQUEST`),
  `approval.ManualQuantityTestResult` (full evaluation plus request identity,
  `approvalRevalidationRequired` always `true`, no decision/draft id), and
  `approval.ManualQuantityTestExecutor.test` (`@Transactional`, never saves or flushes): locks
  the recommendation, checks the latest decision isn't terminal, calls the loader's
  `validateCurrent` then `load` (the same version check and donor lock order approval uses), then
  `ManualQuantityEvaluation.calculate`. A `STALE_RECOMMENDATION`-triggering
  `IllegalArgumentException` from the pure layer is caught and translated the same way
  `ApprovalTransactionExecutor` already does; a `DataAccessException` (lock timeout) is
  translated via the shared `PersistenceErrorTranslator`; an ineligible candidate is a normal
  `feasible=false` result, never an exception.
- Completed (unit tests, all passing): `ManualQuantityEvaluationTest` (13 -- BASE-matching/
  smaller/larger valid quantities, all six violations individually plus one combined-violations
  case, package-multiple flooring for the auto-suggestion, `maximumFeasibleQuantity` forced to 0
  when the hard ceiling itself falls below the route minimum, candidate rejection reasons
  preserved independently of the numeric violations, exact before/after/coverage/risk arithmetic
  on a feasible result, non-positive quantity rejection) and `ManualQuantityTestCommandTest` (8).
  Every hand-computed expected value passed on the first run.
- Completed (Oracle IT, `ManualQuantityTestExecutorIT`, 5/5 passing, `@Transactional` rollback
  like `ApprovalTransactionExecutorIT` since every call in one test method shares one still-open
  transaction/connection): a shared-donor fixture (two receivers, one donor metric/snapshot)
  proves the manual test's `donorTransferableQuantity`/`recommendedBaseQuantity` correctly drop
  after approving the sibling receiver's request against the same donor (10 -> 2 transferable
  after an 8-unit approval); a terminal recommendation is rejected without adding to the
  legitimately-already-written decision/basis/draft row from the prior approval; a stale rule
  version and a wrong route donor are both rejected via the shared loader; an owner-mismatch
  candidate (achieved by a raw `UPDATE sp_store` inside the rolled-back transaction, no migration
  touched) returns a normal infeasible result with `CANDIDATE_INELIGIBLE` and
  `maximumFeasibleQuantity=0` rather than throwing. Also added one new test to
  `ApprovalTransactionConcurrencyIT` (5th test in that file, reusing its
  `TransactionTemplate`-held-lock infrastructure): a donor lock held by a separate genuine
  transaction past 3 seconds also times out a waiting `ManualQuantityTestExecutor.test` call with
  `APPROVAL_LOCK_TIMEOUT` (confirmed via a real Oracle `ORA-00054`), proving the shared lock path
  actually is shared.
- Hit and fixed two mid-development fixture bugs: (1) the first `ManualQuantityTestExecutorIT`
  fixtures used `applyDemandRates(ZERO, ZERO)`, which forced `recommendedBaseQuantity` to 0 and
  therefore `ApprovalBasisRecalculation.eligible()` to always `false` -- every `approve` call in
  the fixture setup failed `STALE_RECOMMENDATION` regardless of the requested quantity. Fixed by
  reusing the exact receiver=5-available/donor=10-available/rate=1 numeric shape already proven
  in `ApprovalTransactionConcurrencyIT`'s fixtures (recommendedBaseQuantity=8). (2) The terminal
  test originally asserted zero decision/basis/draft rows after the manual-test rejection, but
  the fixture's own prior (legitimate) approval had already written one of each -- fixed by
  capturing the row count *before* the manual-test call and asserting it is unchanged afterward,
  not asserting it is zero.
- Validation: existing Oracle (always-on dev container, still at V13 -- no migration changed)
  -- `.\backend\gradlew.bat -p backend build --rerun-tasks` -- `BUILD SUCCESSFUL`, 285/285 tests
  (up from 258), 0 skipped, 0 failures/errors. DB-free build: 285 total, 237 passed, 48
  Oracle-conditioned skips (up from 42), 0 failures/errors. Confirmed the `CurrentApprovalBasisLoader`/
  `TransferEffectProjection` extraction changed no existing behavior: all 48 existing approval-
  package tests (`ApprovalTransactionExecutorIT` 15, `ApprovalTransactionCommandTest` 11,
  `IdempotencyFingerprintTest` 6, `PersistenceErrorTranslatorTest` 9,
  `ApprovalTransactionAtomicityIT` 2, `ApprovalTransactionConcurrencyIT` 5) and
  `TransferScenarioSetTest` (21) passed unchanged. `git diff --check` passed.
- Open: REST controller/DTO/`ProblemDetail` wiring for both the approval and manual-test
  application APIs remains explicitly out of scope for this unit; MVP-2 Batch/React application
  wiring and a real LLM provider adapter are unrelated, still-unimplemented later work.
- Next: Codex independently verifies the `CurrentApprovalBasisLoader` extraction against the
  existing approval contract, the `ManualQuantityEvaluation` formula/violation-order/projection
  against the confirmed contract, and the Oracle IT's stale/terminal/ineligible/lock-timeout
  evidence. Only after approval does `implemented-state.md` move this section to "accepted".
