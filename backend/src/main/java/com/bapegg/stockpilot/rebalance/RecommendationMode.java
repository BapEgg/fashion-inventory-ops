package com.bapegg.stockpilot.rebalance;

/**
 * Matches the {@code recommendation_mode} check on {@code sp_rebalance_recommendation}
 * (added by {@code V6}).
 */
public enum RecommendationMode {
    RECOMMENDED,
    COMPARISON_ONLY,
    NONE
}
