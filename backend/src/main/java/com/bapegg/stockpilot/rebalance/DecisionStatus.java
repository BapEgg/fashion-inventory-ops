package com.bapegg.stockpilot.rebalance;

/**
 * All five values {@code sp_rebalance_decision.decision_status} can physically hold,
 * per {@code V6}'s {@code ck_sp_dec_mvp2_shape}: {@code PENDING} (logical only -- no row,
 * still never legal as a physical row), {@code HELD}, {@code APPROVED}, {@code REJECTED},
 * {@code EXPIRED}. {@code ApprovalTransactionExecutor} (the append-only writer behind
 * {@code POST /api/rebalancing-decisions}' MVP-2 path) only ever produces {@code HELD}/
 * {@code APPROVED}/{@code REJECTED} -- {@code EXPIRED} is mapped so this entity can read a
 * row in that state, but nothing in this codebase writes one yet. This is why this enum is
 * not restricted to the two MVP-1 terminal states the way it used to be.
 * <p>
 * {@code POST /api/rebalancing-decisions}' tuple-less legacy branch is still restricted to
 * {@link #APPROVED}/{@link #REJECTED} only -- {@link RebalanceDecisionService#decide}
 * rejects the other three explicitly, since business-rules.md section 6 has no MVP-1
 * concept of {@code PENDING}/{@code HELD}/{@code EXPIRED}. Widening this enum for
 * persistence must not silently widen what that branch accepts; the same endpoint's MVP-2
 * branch (a complete version tuple plus {@code Idempotency-Key}) does accept {@link #HELD}.
 */
public enum DecisionStatus {
    PENDING,
    HELD,
    APPROVED,
    REJECTED,
    EXPIRED;

    /**
     * Per {@code knowledge/business-rules.md} section 10: only {@link #PENDING} (no decision
     * row yet) and {@link #HELD} accept a further decision or a manual quantity test.
     * {@link #APPROVED}/{@link #REJECTED}/{@link #EXPIRED} are all terminal. Shared by
     * {@code approval.ApprovalTransactionExecutor} and {@code approval.ManualQuantityTestExecutor}
     * so the two use cases can never silently disagree on what counts as terminal.
     */
    public boolean isTerminalForFurtherDecision() {
        return this != PENDING && this != HELD;
    }
}
