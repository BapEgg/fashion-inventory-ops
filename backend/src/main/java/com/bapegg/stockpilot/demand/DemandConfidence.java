package com.bapegg.stockpilot.demand;

/**
 * {@code demand_confidence}, per {@code knowledge/business-rules.md} section 4. Not a predicted
 * probability and never rendered as a percentage.
 */
public enum DemandConfidence {
    HIGH,
    MEDIUM,
    LOW,
    NONE
}
