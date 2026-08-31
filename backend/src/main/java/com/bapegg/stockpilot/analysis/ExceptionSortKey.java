package com.bapegg.stockpilot.analysis;

/**
 * The MVP-2 run-bound exception list's server-side sort key, per
 * {@code knowledge/state/2026-08-30-allocator-workbench-redesign-spec.md} section 4.2.
 */
public enum ExceptionSortKey {
    WORK_PRIORITY,
    SALES_EXPOSURE,
    SHORTAGE_QUANTITY,
    COVERAGE_DAYS,
    STORE_PRODUCT
}
