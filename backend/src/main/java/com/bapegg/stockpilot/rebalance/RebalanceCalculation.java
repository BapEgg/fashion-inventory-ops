package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.analysis.InventoryAnalysisRules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Pure deterministic donor/receiver transfer calculation, per
 * {@code knowledge/business-rules.md} section 4. Caller is responsible for ensuring the
 * receiver and donor share the same SKU, belong to different stores and to the same
 * analysis result.
 */
public record RebalanceCalculation(
        int receiverShortageQuantity,
        int donorTransferableQuantity,
        int recommendedQuantity
) {

    public static Optional<RebalanceCalculation> calculate(
            BigDecimal receiverAverageDailySales,
            int receiverAvailableQuantity,
            BigDecimal donorAverageDailySales,
            int donorAvailableQuantity
    ) {
        int receiverTargetQuantity = ceilToUnits(receiverAverageDailySales, InventoryAnalysisRules.RECEIVER_TARGET_COVERAGE_DAYS)
                + InventoryAnalysisRules.SAFETY_STOCK_UNITS;
        int receiverShortageQuantity = Math.max(receiverTargetQuantity - receiverAvailableQuantity, 0);

        int donorRetainedQuantity = ceilToUnits(donorAverageDailySales, InventoryAnalysisRules.DONOR_RETAINED_COVERAGE_DAYS)
                + InventoryAnalysisRules.SAFETY_STOCK_UNITS;
        int donorTransferableQuantity = Math.max(donorAvailableQuantity - donorRetainedQuantity, 0);

        int recommendedQuantity = Math.min(receiverShortageQuantity, donorTransferableQuantity);
        if (recommendedQuantity <= 0) {
            return Optional.empty();
        }
        return Optional.of(new RebalanceCalculation(receiverShortageQuantity, donorTransferableQuantity, recommendedQuantity));
    }

    private static int ceilToUnits(BigDecimal averageDailySales, int coverageDays) {
        return averageDailySales.multiply(BigDecimal.valueOf(coverageDays))
                .setScale(0, RoundingMode.CEILING)
                .intValueExact();
    }
}
