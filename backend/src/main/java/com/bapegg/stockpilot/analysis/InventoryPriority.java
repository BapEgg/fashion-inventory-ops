package com.bapegg.stockpilot.analysis;

/**
 * Matches the {@code ck_sp_priority} check constraint on {@code sp_inventory_metric.priority}.
 * {@code null} means the record is not a stockout-risk priority target.
 */
public enum InventoryPriority {
    CRITICAL,
    HIGH
}
