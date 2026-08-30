package com.bapegg.stockpilot.rebalance;

/**
 * Matches the {@code candidate_status} check on {@code sp_rebalance_recommendation}
 * (added by {@code V6}).
 */
public enum CandidateStatus {
    ELIGIBLE,
    REJECTED
}
