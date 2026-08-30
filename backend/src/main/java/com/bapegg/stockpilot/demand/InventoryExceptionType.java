package com.bapegg.stockpilot.demand;

/**
 * {@code inventory_exception_type}, per {@code knowledge/business-rules.md} section 6.
 * Exactly one is stored per metric.
 */
public enum InventoryExceptionType {
    STOCKOUT_RISK,
    OVERSTOCK,
    REVIEW_REQUIRED,
    NORMAL,
    NON_ACTIONABLE
}
