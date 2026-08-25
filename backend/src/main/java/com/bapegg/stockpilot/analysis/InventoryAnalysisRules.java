package com.bapegg.stockpilot.analysis;

import java.math.BigDecimal;

/**
 * ASSUMPTION values approved in {@code knowledge/business-rules.md} (rule version MVP-1).
 * These are demo constants, not real company policy.
 */
public final class InventoryAnalysisRules {

    public static final String RULE_VERSION = "MVP-1";

    public static final int OBSERVATION_WINDOW_DAYS = 7;

    /** Internal precision for average daily sales and coverage days before storage rounding. */
    public static final int CALCULATION_SCALE = 10;

    public static final BigDecimal STOCKOUT_RISK_MAX_COVERAGE_DAYS = BigDecimal.valueOf(3);
    public static final BigDecimal OVERSTOCK_MIN_COVERAGE_DAYS = BigDecimal.valueOf(21);
    public static final BigDecimal CRITICAL_MAX_COVERAGE_DAYS = BigDecimal.valueOf(1);

    public static final int RECEIVER_TARGET_COVERAGE_DAYS = 7;
    public static final int DONOR_RETAINED_COVERAGE_DAYS = 14;
    public static final int SAFETY_STOCK_UNITS = 2;

    private InventoryAnalysisRules() {
    }
}
