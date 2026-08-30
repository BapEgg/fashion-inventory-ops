# 2026-08 Active Worklog

## 2026-08-29 — Checkpoint MVP-2 Phase 5 React application wiring

- Role: Codex knowledge checkpoint
- Completed: Accepted Phase 5 with no open frontend finding, preserved the raw state/worklog and
  compacted hot state into [`MVP-2-Phase-5.md`](../milestones/MVP-2-Phase-5.md).
- Validation: frontend 10 files / 97 tests passed; production build passed; frontend diff check
  exited 0 with line-ending warnings only.
- Decisions: optional Phase 6 requires an explicit provider decision; Phase 7 is the recommended
  default when a live AI demonstration is not required.
- Open: select Phase 6 or Phase 7.
- Archive: [raw worklog through acceptance](../archive/worklogs/2026-08-29-through-phase5-react-acceptance.md).

## 2026-08-29 — Specify MVP-2 Phase 7 final verification

- Role: Codex planning/design
- Completed: Converted the user-selected Phase 7 path into an executable verification, browser
  smoke, README rewrite and documentation-reconciliation contract in
  [`current-task.md`](../state/current-task.md).
- Validation: repository scripts, Gradle/Vitest entry points, conditional Oracle ITs, migrations
  and current README claims were inspected; documentation execution has not started.
- Decisions: Phase 6 AI is deliberately skipped. `local.ps1 test` must become a fail-fast full
  Oracle/Frontend verifier and a separate `test-db-free` command must preserve conditional coverage.
- Open: Claude must execute the matrix, capture the real browser screenshot and record actual counts.
- Next: Claude implements the bounded Phase 7 verification contract, then returns it to Codex review.

## 2026-08-29 — Execute MVP-2 Phase 7 final verification

- Role: Claude implementation/verification.
- Completed: rewrote `scripts/local.ps1` (`test-db-free` + a truthful full-verifier `test`, per the
  Phase 7 contract), executed the real command matrix end to end, ran a live browser smoke pass
  with Backend+Frontend against real Oracle data, saved a real desktop screenshot, rewrote
  `README.md` to the verified-fact structure, and added `V14`/`V15` to
  [`data-model.md`](../data-model.md).
- Validation (all real, not copied from prior rounds): `seed-check` passed;
  `test-db-free` 520 total/378 passed/142 skip/0 failures; `db-up`/`db-status` confirmed Oracle
  already healthy; full `test` verifier passed -- Oracle Backend **520/520**, Frontend 10 files/97
  tests + build; repo-root `git diff --check` exit 0 (CRLF warnings only); no credential printed at
  any stage, `.env` stays untracked. Batch Golden Scenario (part of the 520/520) confirms 8 bulk
  statements, 12 metrics, 4 candidates, 1 quality flag, 3 rejection reasons, 8 scenarios.
- Decisions: the default backend port 8080 was occupied by an unrelated project's server on this
  machine -- confirmed via its command line before touching anything, left untouched, and the
  StockPilot backend was run on an alternate port for the smoke test only (vite proxy reverted to
  8080 afterward). No Backend Java/schema/business-rule change was made this round.
- Open: a self-caught mishap -- an early attempt to pass `.env` values inline to a shell command
  briefly echoed `DB_PASSWORD` into a local temp log and this session's background-task output file;
  both were deleted immediately on discovery, nothing was committed or pushed. Recorded transparently
  in [`current-task.md`](../state/current-task.md) rather than omitted.
- Next: Codex reproduces the matrix and reviews `scripts/local.ps1`/`README.md`/`data-model.md`/the
  screenshot, then accepts the MVP-2 checkpoint or opens evidence-backed findings.

## 2026-08-30 — Review MVP-2 Phase 7 final verification

- Role: Codex verification/review
- Completed: independently reproduced the exact DB-free/full verifier, Oracle Backend, Frontend,
  diff, README links/references, screenshot and repository secret checks.
- Validation: DB-free 520/378/142; Oracle 520/520 with zero skip/failure/error; Frontend 10 files/
  97 tests plus build; root diff check exit 0 with line-ending warnings only.
- Decisions: Phase 7 remains unaccepted pending the three findings in
  [`current-task.md`](../state/current-task.md); no Backend/API/DB/business-rule finding was opened.
- Open: enforce full-verifier zero-skip, correct the four-candidate semantics and remove the live-AI
  implication from README.
- Next: Claude fixes the bounded findings and returns the same matrix for Codex re-review.

## 2026-08-30 — Fix the three Phase 7 re-review findings

- Role: Claude implementation.
- Completed: `scripts/local.ps1` gained `Assert-StockPilotOracleCredentialsPresent` (throws unless
  `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` are non-empty; values never printed) and
  `Get-StockPilotJUnitSummary` (aggregates real `backend/build/test-results/test/TEST-*.xml`
  files). `test` now requires `tests > 0` and `skipped = failures = errors = 0` from the real XML
  totals -- not just Gradle's exit code -- before it can print "passed"; `test-db-free` now prints
  its own real total/pass/skip/failure/error summary too. Fixed README's two factual issues:
  `4개 실행 가능 후보` → `4개 후보(2개 적격/2개 탈락)`, and the AI bullet now states the explanation
  endpoint always returns `AI_DISABLED`/`AI_UNCONFIGURED`/`AI_PROVIDER_NOT_IMPLEMENTED` today
  (verified against `ExplanationService`'s actual code paths) instead of implying an activatable
  live-explanation feature.
- Validation: demonstrated the new guard against a synthetic, non-destructive copy of the real
  Oracle result XML -- unmodified copy summarized 520/0/0/0 and was accepted; the same copy with one
  file's `skipped` set to 3 was correctly rejected by the exact `test`-command rule. Re-ran the real
  matrix end to end: `test-db-free` -- `tests=520 passed=378 skipped=142 failures=0 errors=0`;
  `db-status` -- Oracle already healthy, untouched; `test` (full verifier) -- credential presence
  check passed, `Oracle Backend results: tests=520 passed=520 skipped=0 failures=0 errors=0`,
  Frontend 10 files/97 tests + build passed, ending `Full local verification passed.`; root
  `git diff --check` exit 0. Grepped both background-task output logs for `DB_PASSWORD=`/
  `ORACLE_PASSWORD=` -- zero matches.
- Decisions: none beyond the findings -- no Backend/API/DB/business-rule/screenshot change.
- Open: none known from Claude's side; ready for Codex re-review.
- Next: Codex reproduces `test-db-free`/`db-status`/`test`/root `git diff --check`, reviews the new
  guard functions and the two README passages, and accepts the MVP-2/Phase 7 checkpoint or opens
  further findings.

## 2026-08-30 — Accept MVP-2 Phase 7 final verification

- Role: Codex verification/review.
- Completed: independently reviewed the three fixes; the credential/JUnit guards and both README
  corrections match the implementation, and no open production finding remains.
- Validation: blank `DB_URL` and a copied JUnit result with `skipped=3` were both rejected;
  `test-db-free` produced 520 total/378 passed/142 skip/0 failures/0 errors; `db-status` found the
  existing Oracle container healthy; full `test` produced Oracle 520/520 with zero skip/failure/error,
  Frontend 97/97 and a successful production build; root `git diff --check` exited 0.
- Decisions: Phase 7 and MVP-2 are accepted; no code change was required during this review.
- Open: optional LLM provider adapter, scheduler/stale-run recovery, authentication/authorization
  and external ERP/WMS/TMS integrations remain deliberately deferred.
- Next: preserve this acceptance in the MVP-2 checkpoint and keep further scope user-directed.
