package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.demand.DemandAnalysisRules;

/**
 * One store-SKU's effective policy for a Batch run -- either a real {@code SP_STORE_SKU_POLICY}
 * row, or {@link #defaults()} when that row is absent, per
 * {@code knowledge/business-rules.md} section 1. Always fully populated; callers never need to
 * branch on presence.
 */
public record Mvp2Policy(
        int displayMinimum,
        int safetyStock,
        int maximumCapacity,
        int targetCoverageDays,
        int retainedDays
) {

    public static Mvp2Policy defaults() {
        return new Mvp2Policy(
                DemandAnalysisRules.DEFAULT_DISPLAY_MINIMUM,
                DemandAnalysisRules.DEFAULT_SAFETY_STOCK,
                DemandAnalysisRules.DEFAULT_MAXIMUM_CAPACITY,
                DemandAnalysisRules.DEFAULT_TARGET_COVERAGE_DAYS,
                DemandAnalysisRules.DEFAULT_RETAINED_DAYS);
    }
}
