# MVP-2 — Inventory Rebalancing Decision Workbench

Status: Accepted
Checkpoint date: 2026-08-30

## Delivered scope and compatibility boundary

- Oracle/Flyway V1..V15 load a Korean synthetic fashion-retail dataset and persist analysis,
  candidates, quality evidence, scenarios, decisions, approval basis and transfer drafts.
- Deterministic Java and Spring Batch implement run launch/reuse, exception classification, demand
  metrics, donor eligibility, automatic scenarios, MANUAL quantity feasibility and approval.
- REST APIs expose run status, exception queue/detail, simulation, decisions and canonical history.
- The React workbench connects those APIs into one run-bound Korean workflow with safe errors,
  responsive evidence tables and an explicit synthetic-data/`ASSUMPTION` boundary.
- The README supplies the verified setup commands, architecture, workflow, real application
  screenshot and dated evidence.
- Compatibility stops at decision support and a CREATED transfer draft. The MVP does not mutate
  live inventory or integrate with ERP/WMS/TMS.

## Final observable behavior and durable invariants

- Java is the sole authority for quantities, feasibility, statuses and approval results; the UI
  only submits inputs and renders canonical responses.
- Approval normalizes requests, locks recommendation before donor state, revalidates current facts
  and atomically appends the decision, basis and draft. Replays are idempotent.
- Decisions and evidence are bound to an explicit completed analysis run. `APPROVED`,
  `REJECTED` and `EXPIRED` are terminal; `HELD` remains actionable.
- AI is explanation-only. With no provider adapter, the endpoint returns
  `AI_DISABLED`, `AI_UNCONFIGURED` or `AI_PROVIDER_NOT_IMPLEMENTED` and core flows continue.
- Demo policies and records are synthetic `ASSUMPTION` values, never claims about a specific
  company's internal rules or data.
- The full local verifier requires non-empty Oracle credentials and real JUnit XML with tests > 0
  and zero skips/failures/errors, preventing an all-skipped false green.

## Final executed validation evidence

- Codex independently reproduced the credential guard and injected-skip guard; both invalid cases
  were rejected without changing source test results.
- `scripts/local.ps1 test-db-free`: 520 total / 378 passed / 142 expected conditional skips /
  0 failures / 0 errors.
- `scripts/local.ps1 db-status`: existing Oracle container healthy and untouched.
- `scripts/local.ps1 test`: seed validation passed; Oracle Backend 520/520 with zero
  skips/failures/errors; Frontend 10 files / 97 tests passed; `tsc -b && vite build` passed.
- Root `git diff --check`: exit 0 with line-ending conversion warnings only.
- The live browser smoke exercised analysis reuse, queue/detail evidence, candidates, scenarios and
  a side-effect-free MANUAL rejection against Oracle data; no decision or inventory mutation was
  performed.
- Final Codex review found no open production finding.

## Deferred or unimplemented

- Real LLM provider adapter and provider-specific operating contract.
- Operational scheduler, stale `RUNNING` recovery and first-JobInstance race normalization.
- Authentication/authorization and external ERP/WMS/TMS integrations.

## Provenance

- [Phase 7 acceptance current-task snapshot](../archive/state/2026-08-30-phase7-acceptance-current-task.md)
- [Phase 7 acceptance implemented-state snapshot](../archive/state/2026-08-30-phase7-acceptance-implemented-state.md)
- [Raw worklog through Phase 7 acceptance](../archive/worklogs/2026-08-30-through-phase7-acceptance.md)
- [Phase 5 frontend checkpoint](MVP-2-Phase-5.md)
- [Phase 3 backend checkpoint](MVP-2-Phase-3.md)
