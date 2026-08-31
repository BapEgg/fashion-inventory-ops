package com.bapegg.stockpilot.analysis;

import java.math.BigDecimal;

/**
 * The MVP-2 run-wide, filter-independent work summary, per
 * {@code knowledge/state/2026-08-30-allocator-workbench-redesign-spec.md} section 4.4. Computed
 * once per {@code analysisRunId} over every {@code inventoryExceptionType <> NORMAL} metric in that
 * run, unaffected by the caller's current page or filters -- identical on every page of the same
 * run/filter-less request.
 */
public record AllocatorWorkSummary(
        long totalReviewTargets,
        long criticalCount,
        long decisionRequiredCount,
        long onHoldCount,
        long reviewInputCount,
        long noTransferOptionCount,
        long completedCount,
        BigDecimal estimatedSalesExposureTotal,
        long estimatedSalesExposureUnknownCount
) {
}
