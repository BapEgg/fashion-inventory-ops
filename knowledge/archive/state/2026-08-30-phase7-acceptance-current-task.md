# Current Task

Status: MVP-2 Phase 7 final verification — Codex's 3 findings fixed, pending re-review
Current role: Codex review
Last updated: 2026-08-30

## Goal

Codex re-verifies the fixes below and either accepts the Phase 7/MVP-2 checkpoint or opens further
findings.

## Findings fixed this round

### P1 — full verifier could report a false Oracle success

`scripts/local.ps1 test` previously only checked Gradle's exit code, so a blank/missing `DB_URL`
could let every Oracle-only `@EnabledIfEnvironmentVariable` test skip silently while the script
still printed "passed". Fixed:

- `Assert-StockPilotOracleCredentialsPresent` now runs before the Backend Oracle suite and throws
  unless `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` are all non-empty (values are never printed, only
  presence is reported).
- `Get-StockPilotJUnitSummary` aggregates every real `backend/build/test-results/test/TEST-*.xml`
  file. `test` now requires `tests > 0` and `skipped = failures = errors = 0` from those real XML
  totals, not just Gradle's exit code, and prints the actual counts.
- `test-db-free` now also aggregates and prints its real total/pass/skip/failure/error summary
  (skips are expected there, so only `tests > 0` and zero failures/errors are enforced).
- Demonstrated the guard is real: copied the actual Oracle result XML to a temp, non-destructive
  directory, injected `skipped=3` into one file, and confirmed the exact `test`-command rejection
  rule throws against that synthetic corrupted copy (real unmodified copy: 520/0/0/0, passes;
  corrupted copy: correctly rejected). Full transcript is in the 2026-08-30 worklog entry.

### P2 — README candidate count was mislabeled

Changed `4개 실행 가능 후보` to `4개 후보(2개 적격/2개 탈락)`, matching the Oracle regression
(GS-01/GS-02 eligible, GS-05/GS-06 rejected) and the already-corrected wording in
`implemented-state.md`.

### P2 — README implied an activatable AI feature that doesn't exist

Replaced "활성화 시 ... 설명합니다" with an accurate description: the explanation endpoint always
returns one of `AI_DISABLED`/`AI_UNCONFIGURED`/`AI_PROVIDER_NOT_IMPLEMENTED` in the current
configuration (verified against `ExplanationService`'s actual code paths -- none of them return a
generated explanation today); core flows are unaffected by this state; a future adapter would still
be bound to explaining Java-computed facts only.

## Real re-verification this round (not copied from a prior round)

- Guard demonstration (synthetic, non-destructive): unmodified Oracle result copy summarized as
  520/0/0/0 and accepted; the same copy with one file's `skipped` set to 3 was correctly rejected by
  the exact `test`-command rule.
- `test-db-free`: `DB-free Backend results: tests=520 passed=378 skipped=142 failures=0 errors=0` --
  passed.
- `db-status`: Oracle container confirmed already running and healthy (untouched).
- `test` (full verifier): seed validation passed; Oracle health check passed; Oracle credential
  presence check passed (values not printed); `Oracle Backend results: tests=520 passed=520
  skipped=0 failures=0 errors=0`; Frontend `pnpm install --frozen-lockfile` + `pnpm test` (10
  files/97 tests) + `pnpm build` all passed; final line `Full local verification passed.`
- Root `git diff --check`: exit 0 (CRLF warnings only).
- No credential value appears in either background-task output log for this round (grepped for
  `DB_PASSWORD=`/`ORACLE_PASSWORD=`, zero matches).

## Non-negotiable boundaries (unchanged)

- No Backend Java, public API, Flyway/DB schema, business rule or the saved screenshot was changed.
- Oracle container/volume was never started, stopped, recreated or deleted by the verifier.

## Next verifiable action

Codex reproduces `test-db-free`, `db-status`, `test` and root `git diff --check`, reviews
`scripts/local.ps1`'s new guard functions and the two README passages, and either accepts the
MVP-2/Phase 7 checkpoint or opens further evidence-backed findings.
