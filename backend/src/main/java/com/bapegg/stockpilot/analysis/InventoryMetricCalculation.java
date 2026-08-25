package com.bapegg.stockpilot.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure deterministic calculation of availability, sales rate, coverage and classification
 * for a single store-SKU record, per {@code knowledge/business-rules.md} sections 2 and 3.
 */
public record InventoryMetricCalculation(
        int availableQuantity,
        BigDecimal averageDailySales,
        BigDecimal coverageDays,
        InventoryClassification classification,
        InventoryPriority priority
) {

    public static InventoryMetricCalculation calculate(
            int onHandQuantity, int reservedQuantity, int soldQuantityInWindow) {
        if (onHandQuantity < 0 || reservedQuantity < 0 || soldQuantityInWindow < 0) {
            throw new IllegalArgumentException("Quantities must not be negative.");
        }
        if (reservedQuantity > onHandQuantity) {
            throw new IllegalArgumentException("Reserved quantity must not exceed on-hand quantity.");
        }

        int availableQuantity = onHandQuantity - reservedQuantity;
        BigDecimal averageDailySales = BigDecimal.valueOf(soldQuantityInWindow)
                .divide(BigDecimal.valueOf(InventoryAnalysisRules.OBSERVATION_WINDOW_DAYS),
                        InventoryAnalysisRules.CALCULATION_SCALE, RoundingMode.HALF_UP);

        if (averageDailySales.signum() > 0) {
            BigDecimal coverageDays = BigDecimal.valueOf(availableQuantity)
                    .divide(averageDailySales, InventoryAnalysisRules.CALCULATION_SCALE, RoundingMode.HALF_UP);
            InventoryClassification classification = classifyByCoverage(coverageDays);
            InventoryPriority priority = classification == InventoryClassification.STOCKOUT_RISK
                    ? priorityByCoverage(coverageDays)
                    : null;
            return new InventoryMetricCalculation(
                    availableQuantity, averageDailySales, coverageDays, classification, priority);
        }

        if (availableQuantity > 0) {
            return new InventoryMetricCalculation(
                    availableQuantity, averageDailySales, null, InventoryClassification.OVERSTOCK, null);
        }

        return new InventoryMetricCalculation(
                availableQuantity, averageDailySales, null, InventoryClassification.NON_ACTIONABLE, null);
    }

    private static InventoryClassification classifyByCoverage(BigDecimal coverageDays) {
        if (coverageDays.compareTo(InventoryAnalysisRules.STOCKOUT_RISK_MAX_COVERAGE_DAYS) <= 0) {
            return InventoryClassification.STOCKOUT_RISK;
        }
        if (coverageDays.compareTo(InventoryAnalysisRules.OVERSTOCK_MIN_COVERAGE_DAYS) >= 0) {
            return InventoryClassification.OVERSTOCK;
        }
        return InventoryClassification.NORMAL;
    }

    private static InventoryPriority priorityByCoverage(BigDecimal coverageDays) {
        return coverageDays.compareTo(InventoryAnalysisRules.CRITICAL_MAX_COVERAGE_DAYS) <= 0
                ? InventoryPriority.CRITICAL
                : InventoryPriority.HIGH;
    }
}
