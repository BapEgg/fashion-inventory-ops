package com.bapegg.stockpilot.demand;

/**
 * {@code decision_status}, per {@code knowledge/business-rules.md} section 10.
 */
public enum DecisionStatus {
    PENDING,
    HELD,
    APPROVED,
    REJECTED,
    EXPIRED
}
