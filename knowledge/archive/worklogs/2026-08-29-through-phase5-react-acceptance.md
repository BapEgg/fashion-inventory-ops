# Active Worklog — 2026-08

Earlier entries are preserved in
[`../archive/worklogs/2026-08-28-through-decision-rest-implementation.md`](../archive/worklogs/2026-08-28-through-decision-rest-implementation.md).

## 2026-08-28 — Review approval/decision REST implementation

- Role: Codex verification/review and hot-state budget checkpoint.
- Completed: independently compared POST/GET behavior and tests with the
  [owning specification](../archive/state/2026-08-28-approval-decision-rest-spec.md); preserved and
  compacted the oversized implemented state/worklog without promoting this unit to a milestone.
- Validation: DB-free target 22/22; Oracle target decision REST 19/19 plus API golden 6/6;
  DB-free full 510 total/373 passed/137 skip; Oracle full forced 510/510; `git diff --check` exit 0.
- Decisions: not accepted despite green tests.
- Open: common/legacy validation precedence is wrong; the legacy unique-sequence race maps to 500
  instead of `DECISION_CONFLICT`; required exact REST evidence is incomplete; stale future-service
  Javadocs remain.
- Next: Claude applies only the fixes in [current-task.md](../state/current-task.md), reruns target
  and full DB-free/Oracle suites, then returns for Codex re-review.

## 2026-08-29 — Re-review approval/decision REST fixes

- Role: Codex verification/review.
- Completed: verified common/legacy validation precedence and legacy constraint translation against
  the [owning specification](../archive/state/2026-08-28-approval-decision-rest-spec.md); inspected
  the strengthened REST assertions and updated Javadocs.
- Validation: DB-free target 23/23; Oracle target 30/30; legacy race five extra Oracle repetitions;
  DB-free full 520 total/378 passed/142 skip; Oracle full forced 520/520; `git diff --check` exit 0.
- Decisions: both production findings are closed; the unit remains not accepted.
- Open: recorded completion overstates the tests—APPROVED/replay exact fields, failing-call
  snapshot/metric immutability, full validation ProblemDetail and exact GET key sets remain partial;
  three Javadoc areas remain stale or inaccurate.
- Next: Claude makes only the test/Javadoc corrections in [current-task.md](../state/current-task.md)
  and returns for final Codex review.

## 2026-08-29 — Fix remaining REST-evidence and Javadoc findings

- Role: Claude implementation and Oracle verification (same local Docker Oracle, still connected).
- Completed: test-only and Javadoc-only, no production change. `RebalanceDecisionRestOracleIT` now
  asserts every field of the HELD/APPROVED/replay six-key MVP-2 response independently (including
  `decisionId`/`recommendationId`/`created`, and that APPROVED gets a genuinely new `decisionId`),
  applies `capture`/`assertUnchanged` snapshot/metric-value immutability to all eight
  persistence-sensitive failure paths (not just the success ones), and pins exact key sets for the
  GET response/decision item/approval-basis item/transfer-draft item plus the top-level
  `recommendationId` on both GET history tests. Split the ProblemDetail helper into a shared core
  plus a non-validation variant (asserts `fieldErrors` absent) and a validation variant (asserts
  exactly one matching `fieldErrors` entry), and routed the two non-positive-`recommendationId` POST
  tests and the GET path-variable test through the full contract instead of a bare 400 check.
  Corrected the three stale Javadoc areas: `DecisionStatus` now names the tuple-less legacy branch
  instead of calling the whole POST endpoint "the MVP-1 flow"; `SpRebalanceDecisionRepository`'s
  first two method comments no longer say "once/even after a real writer exists" now that
  `ApprovalTransactionExecutor` already is one; `AnalysisApiExceptionHandler` now correctly
  attributes `ApprovalTransactionException` to POST's MVP-2 branch only, noting that decision-history
  GET and POST's legacy branch throw `ApiException` instead (both resolve through the same catalog).
- Validation: `RebalanceDecisionRestOracleIT` **24/24 Oracle passed on the first run** (no new test
  methods, only strengthened assertions). DB-free unit tests unchanged: controller 17/17, history
  query 6/6. Full Oracle run: **520 total / 520 passed / skip 0 / failures/errors 0**. DB-free full:
  520 total/378 passed/142 conditional skip, failures/errors 0 (identical counts to the prior round,
  as expected since no tests were added or removed). `git diff --check` exit 0 (line-ending warnings
  only).
- Decisions: none beyond the findings -- no scope, contract, or schema change.
- Open: none from Claude's side; ready for Codex's final independent re-review.
- Next: Codex re-verifies the strengthened assertions and corrected Javadocs, then marks this unit
  accepted or opens any remaining finding.

## 2026-08-29 — Accept approval/decision REST

- Role: Codex final verification/review.
- Completed: verified the strengthened exact responses, failure immutability, full ProblemDetail,
  GET key sets and Javadocs against the
  [owning specification](../archive/state/2026-08-28-approval-decision-rest-spec.md). Corrected one
  remaining comment sentence about legacy constraint-error exception ownership; no behavior change.
- Validation: DB-free target 23/23; Oracle target 30/30; DB-free full 520 total/378 passed/142 skip;
  Oracle full forced 520/520; `git diff --check` exit 0.
- Decisions: accepted the MVP-2 approval/decision REST unit; no open finding remains.
- Open: none for this unit.
- Next: select the next unimplemented slice, then Codex writes its detailed specification before
  implementation.

## 2026-08-29 — Specify MVP-2 React application wiring

- Role: Codex planning/design and Claude implementation handoff.
- Completed: selected the final MVP-2 screen-connection slice, compared the MVP-1-only Frontend
  with all accepted Backend DTOs, and wrote the
  [implementation-ready specification](../archive/state/2026-08-29-mvp2-react-application-wiring-spec.md).
- Validation: current Frontend build reached TypeScript and failed at the pre-existing missing
  top-level `@types/node` prerequisite (`TS2580`); `git diff --check` exit 0 after documentation.
- Decisions: run-bound analysis-to-decision workbench; no Backend/schema change or React business
  calculation; feasible current MANUAL result gates approval UI; stable per-intent idempotency key;
  Korean operational presentation with coverage days at maximum one decimal.
- Open: React implementation and its tests/build have not started or been accepted.
- Next: Claude implements type/API/test setup first, then analysis, queue, detail, MANUAL and
  decision/history in the specification's fixed order and returns for Codex review.

## 2026-08-29 — Review MVP-2 React application wiring

- Role: Codex independent verification/review with rendered UI smell inspection.
- Completed: compared the React implementation and tests with the
  [owning specification](../archive/state/2026-08-29-mvp2-react-application-wiring-spec.md), ran the
  Frontend suite/build, and inspected the actual initial Vite render.
- Validation: `pnpm --dir frontend test` 9 files/69 tests passed; `pnpm --dir frontend build`
  succeeded; `git diff --check -- frontend` exit 0 (line-ending warnings only).
- Decisions: not accepted despite green tests/build.
- Open: previous context remains actionable during a new analysis; new workbench CSS is absent;
  detail uses legacy classification/priority; canonical terminal/stale recovery is incomplete;
  projection/audit evidence, network recovery and secondary evidence presentation remain partial.
- Next: Claude fixes only [current-task.md](../state/current-task.md), adds focused regressions and
  rendered desktop/narrow verification, then returns for Codex re-review.

## 2026-08-29 — Implement MVP-2 React application wiring

- Role: Claude implementation.
- Completed: implemented the full workbench per the
  [specification](../archive/state/2026-08-29-mvp2-react-application-wiring-spec.md) in its fixed
  order (types/API/labels/formatters, then analysis → queue → detail → candidate/scenario → MANUAL
  → decision/history), removed the obsolete `RecommendationPanel.tsx`, added the test toolchain
  (`vitest`/`jsdom`/`@testing-library/*`/`@types/node`), fixed the `vitest@2` vs `vite@8` peer
  conflict by moving to `vitest@^4`, and set `pnpm-workspace.yaml` `allowBuilds.esbuild: true`.
  Wrote 9 test files covering the required API/formatter and component/integration behavior.
- Decisions: fixed `api.isAbortError` to check `.name === 'AbortError'` instead of
  `instanceof Error` — a real aborted `fetch` rejects with a `DOMException`, which per spec does not
  extend `Error` (the abort-suppression tests caught this against jsdom's own `DOMException`); left
  every other implementation choice as specified.
- Validation: `pnpm --dir frontend test` — 9 files / 69 tests, all passed. `pnpm --dir frontend
  build` — `tsc -b && vite build` succeeded. `git diff --check` on `frontend/`: exit 0 (CRLF
  warnings only). Oracle/live-Backend integration: not executed (pure REST client, API mocked in
  tests). Diff confirms no Backend file changed in this slice.
- Open: none known from Claude's side; ready for Codex independent review.
- Next: Codex reviews the implementation against the specification and marks it accepted or opens
  findings.

## 2026-08-29 — Fix MVP-2 React application wiring review findings

- Role: Claude implementation.
- Completed: fixed all four P1 and three P2 findings from the "Review MVP-2 React application
  wiring" round above: `AnalysisContext.onRunStarting` + `App.handleRunStarting` retire the previous
  run/list/detail/decision context the instant a new launch begins, not only on completion; rewrote
  `frontend/src/styles.css` for the actual component class families with visible focus states and a
  responsive stack, verified in-browser at desktop/375px; `ExceptionDetail` now renders MVP-2
  `inventoryExceptionType`/`severity` as primary with legacy classification/priority as labeled
  secondary evidence, plus the full run identity; `DecisionPanel`'s terminal gate now derives from
  canonical history (not the stale `candidate` prop), and `STALE_RECOMMENDATION`/
  `DECISION_ALREADY_TERMINAL` invalidate the simulation and offer a real `onRequireDetailRefresh`
  action wired through `ExceptionDetail`; `api.ts` centrally normalizes raw `fetch` failures into a
  populated retryable `ApiError` (`toNetworkError`, exported); added retry for detail/history loads
  and a filter-reset action for the empty queue; `ObservationEvidence` no longer coerces missing
  values to zero (draws a path gap instead); related-evidence tables now show source/assumption/
  donor-receiver identity with translated `direction`; canonical history audit now renders the full
  approval-basis/transfer-draft contract; scenario/projection risk codes render in Korean.
- Decisions: none beyond the findings -- no Backend/schema/contract change, no new abstraction beyond
  what each finding required.
- Validation: `pnpm --dir frontend test` -- 9 files / 87 tests, all passed (was 69; +18 for the
  fixed findings). `pnpm --dir frontend build` -- `tsc -b && vite build` succeeded. `git diff --check
  -- frontend`: exit 0 (CRLF warnings only). Browser preview confirmed the new stylesheet at desktop
  and 375px widths and a visible 3px focus outline on a focused button. Diff confirms no Backend file
  changed in this slice. Oracle/live-Backend integration: not executed (pure REST client, API mocked
  in tests).
- Open: none known from Claude's side; ready for Codex re-review.
- Next: Codex re-verifies the fixes against the specification and marks the unit accepted or opens
  further findings.

## 2026-08-29 — Re-review MVP-2 React finding fixes

- Role: Codex verification/review
- Completed: Rechecked the prior findings against the approved React wiring specification, source,
  regression tests and desktop/375px browser render; production code was not changed.
- Validation: `pnpm --dir frontend test` — 9 files / 87 passed; `pnpm --dir frontend build` — passed;
  `git diff --check -- frontend` — exit 0 with CRLF warnings only.
- Decisions: Prior context-retirement, exception meaning, canonical-history gating, network error,
  chart-gap, evidence/risk and base visual-style fixes are retained, but Phase 5 is not accepted.
- Open: P1 terminal conflict is not fail-closed; P1 related-evidence tables lack local narrow-screen
  scrolling; P2 observation provenance, Korean run status and audit identifiers/versions are incomplete.
- Next: Claude fixes the five focused findings and adds regressions, then Codex re-reviews.

## 2026-08-29 — Fix the five reopened MVP-2 React findings

- Role: Claude implementation.
- Completed: fixed all 5 findings (2 P1, 3 P2) from the re-review above. `DecisionPanel` now sets a
  `forcedTerminal` flag the instant `DECISION_ALREADY_TERMINAL` arrives -- the form retires
  immediately on that authoritative signal instead of depending on the follow-up history GET, which
  could fail or race back a stale non-terminal status; the decision-error alert and its 상세 새로고침
  action were moved outside the actionable/terminal branch so they stay visible once `terminal` flips
  true in the same render. `ExceptionDetail`'s three `RelatedEvidence` tables are each wrapped in a
  local `related-evidence__scroll` container. `ObservationEvidence` now shows `inventorySourceType`
  and `salesSourceType` in two separate columns instead of `??`-dropping one. Added
  `labels.analysisRunStatusLabel` (exhaustive + raw-code fallback) and used it for `AnalysisContext`'s
  run-status field. The canonical audit view now also renders `approvalBasisId`,
  `recommendationVersion` and `decisionContractVersion`.
- Decisions: none beyond the findings -- no Backend/schema/contract change.
- Validation: `pnpm --dir frontend test` -- 10 files / 94 tests, all passed (was 87; +7, including
  two DECISION_ALREADY_TERMINAL fail-closed races and a structural local-scroll regression).
  `pnpm --dir frontend build` -- `tsc -b && vite build` succeeded. `git diff --check -- frontend`:
  exit 0 (CRLF warnings only). Diff confirms no Backend file changed in this slice. Caught and fixed
  a test-authoring bug of my own along the way: an unconsumed chained `mockResolvedValueOnce` on
  `api.decide` in an existing test leaked into the next test's mock queue, masking its intended
  rejection -- removed the unused chain rather than adding an unrelated `mockReset`.
- Open: none known from Claude's side; ready for Codex re-review.
- Next: Codex re-verifies the five fixes against the specification and marks the unit accepted or
  opens further findings.

## 2026-08-29 — Re-review five React follow-up fixes

- Role: Codex verification/review
- Completed: Confirmed all five prior findings in code and tests; reviewed terminal/idempotency
  transitions and the narrow-screen UI structure without changing production code.
- Validation: `pnpm --dir frontend test` — 10 files / 94 passed; `pnpm --dir frontend build` —
  passed; `git diff --check -- frontend` — exit 0 with CRLF warnings; 375px initial render passed.
- Decisions: The five prior fixes are accepted, but the React wiring phase remains unaccepted.
- Open: P1 successful APPROVED/REJECTED responses do not fail closed if canonical history fails or
  lags; P2 the named post-terminal history-failure test consumes its rejection on the initial GET.
- Next: Claude fixes both findings with correctly ordered GET mocks, then Codex re-reviews.

## 2026-08-29 — Fix the two reopened terminal fail-closed findings

- Role: Claude implementation.
- Completed: `DecisionPanel.submitBody`'s success path now sets `forcedTerminal` whenever the
  `decide()` response's own `decisionStatus` is itself terminal (APPROVED/REJECTED/EXPIRED), the same
  flag used for `DECISION_ALREADY_TERMINAL` -- the response is authoritative, so the form retires in
  the same render rather than depending on the follow-up history GET; HELD is explicitly excluded and
  stays actionable. Moved the `decisionResult` success message outside the actionable/terminal branch
  (next to `decisionError`, moved there last round) so it stays visible once the form retires instead
  of disappearing when the branch switches. Fixed the mock-ordering bug in both
  `DECISION_ALREADY_TERMINAL` fail-closed regressions: `getDecisionHistory`'s one-shot
  failure/non-terminal mock was queued before `render()`, so `DecisionPanel`'s own mount-time history
  fetch consumed it instead of the intended post-error refetch -- both tests now queue an initial
  successful response first, assert exactly two `getDecisionHistory` calls, and (for the failure
  case) assert the history load's own error/retry UI renders too. Added new regressions: successful
  APPROVED with a failing follow-up GET, successful REJECTED with a lagging non-terminal follow-up
  GET, and successful HELD confirming it stays actionable.
- Decisions: none beyond the findings -- no Backend/schema/contract change.
- Validation: `pnpm --dir frontend test` -- 10 files / 97 tests, all passed (was 94; +3). `pnpm
  --dir frontend build` -- `tsc -b && vite build` succeeded. `git diff --check -- frontend`: exit 0
  (CRLF warnings only). Diff confirms no Backend file changed in this slice.
- Open: none known from Claude's side; ready for Codex re-review.
- Next: Codex re-verifies the two fixes against the specification and marks the unit accepted or
  opens further findings.

## 2026-08-29 — Accept MVP-2 Phase 5 React application wiring

- Role: Codex verification/review
- Completed: Re-reviewed the two terminal-success fixes; successful APPROVED/REJECTED now retire
  the form from the authoritative POST response, HELD remains actionable, and success text remains
  visible. Corrected terminal-error tests now exercise the actual second history GET.
- Validation: `pnpm --dir frontend test` — 10 files / 97 passed; `pnpm --dir frontend build` —
  passed; `git diff --check -- frontend` — exit 0 with CRLF warnings only.
- Decisions: Accepted the full Phase 5 React application wiring; no open frontend finding remains.
- Open: Optional real LLM provider, operational recovery, authentication and external integration
  remain outside Phase 5. Provider selection requires user direction.
- Next: Checkpoint Phase 5, then choose optional Phase 6 AI or proceed to Phase 7 final verification.

