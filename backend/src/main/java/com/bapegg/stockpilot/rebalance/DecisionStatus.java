package com.bapegg.stockpilot.rebalance;

/**
 * Matches the {@code ck_sp_dec_status} check constraint on
 * {@code sp_rebalance_decision.decision_status}. Per business-rules.md section 6, a
 * recommendation with no {@link SpRebalanceDecision} row is implicitly {@code PENDING};
 * these are the only two terminal states a decision can be persisted as.
 */
public enum DecisionStatus {
    APPROVED,
    REJECTED
}
