# Current Task

Status: MVP-2 React application wiring — two follow-up findings fixed, pending re-review
Current role: Codex review
Last updated: 2026-08-29

## Goal

Codex re-verifies the two fixes below against the
[`MVP-2 React application wiring specification`](../archive/state/2026-08-29-mvp2-react-application-wiring-spec.md)
and either accepts the unit or opens further findings.

## Findings fixed this round

1. **P1 — successful terminal decisions must also fail closed**: `DecisionPanel.submitBody`'s
   success path now checks the `decide()` response's own `decisionStatus` and sets the same
   `forcedTerminal` flag used for `DECISION_ALREADY_TERMINAL` whenever it is itself terminal
   (APPROVED/REJECTED/EXPIRED) -- the response is authoritative, so the form retires in the same
   render, not after (or contingent on) the follow-up history GET. `HELD` is never terminal, so it
   is explicitly left actionable. The `decisionResult` success message was moved outside the
   actionable/terminal branch (alongside `decisionError`, moved there in the prior round) so it
   stays visible once the form retires instead of being hidden by the branch switch. Added
   regressions for a successful APPROVED whose follow-up history GET then fails, a successful
   REJECTED whose follow-up GET races back a non-terminal status, and a successful HELD confirming
   it stays actionable.
2. **P2 — the terminal-error follow-up-failure regression mocked the wrong GET**: both
   `DECISION_ALREADY_TERMINAL` fail-closed regressions queued their `getDecisionHistory` mock
   *before* rendering, so the initial mount fetch (fired by `DecisionPanel`'s own candidate-selection
   effect) consumed it instead of the intended post-error refetch. Both tests now queue an initial
   successful response first, then the intended failure/non-terminal response second, assert exactly
   two `getDecisionHistory` calls, and (for the failure case) assert the history load's own
   error/retry UI renders alongside the retired form.

## What Codex should verify

- That the success-path `forcedTerminal` set is keyed only on the response's own `decisionStatus`
  (never assumed) and that HELD genuinely stays actionable end-to-end.
- That moving `decisionResult` outside the actionable/terminal branch did not change its content or
  the `created`/replay wording, only where it renders.
- That the corrected mock ordering in the two DECISION_ALREADY_TERMINAL regressions now actually
  exercises the post-error refetch, not the mount fetch.
- The 97 real `pnpm --dir frontend test` results and the real `pnpm --dir frontend build` result
  actually match what is recorded in `implemented-state.md`.

## Non-negotiable boundaries (unchanged)

- Do not change Backend Java, public API, Flyway or DB schema for this unit.
- Do not invent an enterprise reason-code catalog or present `actorLabel` as an authenticated user.
- Preserve user-owned unrelated worktree changes.

## Next verifiable action

Codex re-reviews the two fixes against the specification and either marks the unit accepted (and
selects the next unimplemented slice) or opens further findings for Claude to fix.

