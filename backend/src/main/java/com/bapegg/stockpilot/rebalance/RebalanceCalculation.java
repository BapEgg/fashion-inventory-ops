package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.analysis.InventoryAnalysisRules;

import java.util.Optional;

/**
 * Pure deterministic donor/receiver transfer calculation, per
 * {@code knowledge/business-rules.md} section 4. Caller is responsible for ensuring the
 * receiver and donor share the same SKU, belong to different stores and to the same
 * analysis result.
 * <p>
 * Per business-rules.md section 2, transfer calculations must use the unrounded sales
 * rate. This implementation therefore takes the raw 7-day sold quantity (an integer,
 * exactly as recorded) rather than a pre-divided {@code averageDailySales}: the target
 * and retained quantities reduce to an exact integer ceiling division
 * ({@code soldQuantityInWindow * coverageDays / observationWindowDays}), with no
 * intermediate BigDecimal rounding that could push a value that is exactly an integer
 * (e.g. {@code 1/7 * 7 = 1}) just over that integer and inflate the ceiling result.
 */
public record RebalanceCalculation(
        int receiverShortageQuantity,
        int donorTransferableQuantity,
        int recommendedQuantity
) {

    public static Optional<RebalanceCalculation> calculate(
            int receiverSoldQuantityInWindow,
            int receiverAvailableQuantity,
            int donorSoldQuantityInWindow,
            int donorAvailableQuantity
    ) {
        int receiverTargetQuantity = Math.toIntExact(
                ceilDiv((long) receiverSoldQuantityInWindow * InventoryAnalysisRules.RECEIVER_TARGET_COVERAGE_DAYS,
                        InventoryAnalysisRules.OBSERVATION_WINDOW_DAYS)
                        + InventoryAnalysisRules.SAFETY_STOCK_UNITS);
        int receiverShortageQuantity = Math.max(receiverTargetQuantity - receiverAvailableQuantity, 0);

        int donorRetainedQuantity = Math.toIntExact(
                ceilDiv((long) donorSoldQuantityInWindow * InventoryAnalysisRules.DONOR_RETAINED_COVERAGE_DAYS,
                        InventoryAnalysisRules.OBSERVATION_WINDOW_DAYS)
                        + InventoryAnalysisRules.SAFETY_STOCK_UNITS);
        int donorTransferableQuantity = Math.max(donorAvailableQuantity - donorRetainedQuantity, 0);

        int recommendedQuantity = Math.min(receiverShortageQuantity, donorTransferableQuantity);
        if (recommendedQuantity <= 0) {
            return Optional.empty();
        }
        return Optional.of(new RebalanceCalculation(receiverShortageQuantity, donorTransferableQuantity, recommendedQuantity));
    }

    /**
     * Exact ceiling division for a non-negative numerator and positive denominator,
     * kept in {@code long} so neither the division nor the caller's subsequent
     * {@code + SAFETY_STOCK_UNITS} can silently wrap a 32-bit {@code int}. The caller
     * converts to {@code int} exactly once, via {@link Math#toIntExact}, after that
     * addition — so an out-of-{@code int}-range target/retained quantity throws
     * rather than truncates.
     */
    private static long ceilDiv(long numerator, long denominator) {
        return (numerator + denominator - 1) / denominator;
    }
}
