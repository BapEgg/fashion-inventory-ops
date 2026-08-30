package com.bapegg.stockpilot.demand;

import java.math.BigDecimal;

/**
 * ASSUMPTION values approved in {@code knowledge/business-rules.md} sections 1-3
 * (rule version MVP-2). These are demo constants, not real company policy.
 */
public final class DemandAnalysisRules {

    public static final String RULE_VERSION = "MVP-2";

    public static final int OBSERVATION_WINDOW_DAYS = 28;
    public static final int FIXED_WEEK_COUNT = 4;
    public static final int DAYS_PER_WEEK = 7;

    public static final int MINIMUM_OBSERVABLE_DAYS = 14;
    public static final int MINIMUM_LAUNCH_DAYS = 14;

    /** Internal precision for demand rates and related decimals before storage rounding. */
    public static final int RATE_SCALE = 12;
    public static final int SALES_DAY_RATIO_SCALE = 6;

    public static final BigDecimal STABLE_REPEAT_MAX_WEEKLY_CV = new BigDecimal("0.35");
    public static final int STABLE_REPEAT_MINIMUM_ACTIVE_WEEKS = 3;

    public static final int INTERMITTENT_MAXIMUM_ACTIVE_WEEKS = 2;
    public static final BigDecimal INTERMITTENT_MAXIMUM_SALES_DAY_RATIO = new BigDecimal("0.25");

    public static final BigDecimal SPIKE_ABSOLUTE_MINIMUM = BigDecimal.valueOf(5);
    public static final BigDecimal SPIKE_MAD_MULTIPLIER = BigDecimal.valueOf(3);
    public static final BigDecimal SPIKE_MINIMUM_MAD = BigDecimal.ONE;
    public static final BigDecimal SPIKE_WINDOW_SHARE_MINIMUM = new BigDecimal("0.35");

    public static final BigDecimal BULK_TRANSACTION_MINIMUM_QUANTITY = BigDecimal.valueOf(5);
    public static final BigDecimal BULK_TRANSACTION_SHARE_MINIMUM = new BigDecimal("0.70");

    /** business-rules.md section 3: flat plan-horizon length when a store-SKU has no active route. */
    public static final int PLAN_HORIZON_NO_ROUTE_FALLBACK_DAYS = 7;

    /** business-rules.md section 5: below this many valid weekly rates, send to REVIEW_REQUIRED. */
    public static final int MINIMUM_VALID_WEEKLY_RATES = 3;
    public static final BigDecimal LOW_DEMAND_RATE_PERCENTILE = new BigDecimal("0.25");
    public static final BigDecimal BASE_DEMAND_RATE_PERCENTILE = new BigDecimal("0.50");
    public static final BigDecimal HIGH_DEMAND_RATE_PERCENTILE = new BigDecimal("0.75");

    /**
     * business-rules.md section 1: demo defaults used only when a store-SKU has no
     * {@code SP_STORE_SKU_POLICY} row for the requested input version. A route's own min/
     * package/max are never defaulted this way -- V6 requires those columns {@code NOT NULL}
     * within any row that exists at all, so a missing value there is impossible; a missing
     * route row itself is {@code ROUTE_NOT_ALLOWED}, never a fallback.
     */
    public static final int DEFAULT_DISPLAY_MINIMUM = 1;
    public static final int DEFAULT_SAFETY_STOCK = 2;
    public static final int DEFAULT_MAXIMUM_CAPACITY = 100;
    public static final int DEFAULT_TARGET_COVERAGE_DAYS = 7;
    public static final int DEFAULT_RETAINED_DAYS = 14;

    private DemandAnalysisRules() {
    }
}
