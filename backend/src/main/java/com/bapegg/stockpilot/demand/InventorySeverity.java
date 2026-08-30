package com.bapegg.stockpilot.demand;

/**
 * Review-priority severity, per {@code knowledge/business-rules.md} section 9. Not stored for
 * every exception type -- {@code NORMAL}, {@code OVERSTOCK} and {@code NON_ACTIONABLE} metrics
 * carry no severity.
 */
public enum InventorySeverity {
    CRITICAL,
    HIGH,
    REVIEW
}
