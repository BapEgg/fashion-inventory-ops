package com.bapegg.stockpilot.demand;

/**
 * {@code primary_demand_signal_type}, per {@code knowledge/business-rules.md} section 3.
 * Exactly one is stored per metric; quality flags are tracked separately.
 */
public enum DemandSignalType {
    DATA_INSUFFICIENT,
    KNOWN_EVENT,
    UNEXPLAINED_SPIKE,
    INTERMITTENT,
    STABLE_REPEAT,
    VARIABLE
}
