package com.bapegg.stockpilot.analysis;

/**
 * Matches the {@code ck_sp_classification} check constraint on {@code sp_inventory_metric.classification}.
 */
public enum InventoryClassification {
    STOCKOUT_RISK,
    OVERSTOCK,
    NORMAL,
    NON_ACTIONABLE
}
