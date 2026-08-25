package com.bapegg.stockpilot.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Golden Scenario values from {@code knowledge/business-rules.md} section 8
 * (analysis date 2026-08-25, SKU-CAP-BLACK-FREE).
 */
class InventoryMetricCalculationTest {

    @Test
    void gangnamIsStockoutRiskWithHighPriority() {
        InventoryMetricCalculation result = InventoryMetricCalculation.calculate(6, 1, 28);

        assertEquals(5, result.availableQuantity());
        assertEquals(new BigDecimal("4.00"), display(result.averageDailySales()));
        assertEquals(new BigDecimal("1.25"), display(result.coverageDays()));
        assertEquals(InventoryClassification.STOCKOUT_RISK, result.classification());
        assertEquals(InventoryPriority.HIGH, result.priority());
    }

    @Test
    void hongdaeIsOverstock() {
        InventoryMetricCalculation result = InventoryMetricCalculation.calculate(42, 2, 4);

        assertEquals(40, result.availableQuantity());
        assertEquals(new BigDecimal("0.57"), display(result.averageDailySales()));
        assertEquals(new BigDecimal("70.00"), display(result.coverageDays()));
        assertEquals(InventoryClassification.OVERSTOCK, result.classification());
        assertNull(result.priority());
    }

    @Test
    void seongsuIsNormal() {
        InventoryMetricCalculation result = InventoryMetricCalculation.calculate(12, 1, 9);

        assertEquals(11, result.availableQuantity());
        assertEquals(new BigDecimal("1.29"), display(result.averageDailySales()));
        assertEquals(new BigDecimal("8.56"), display(result.coverageDays()));
        assertEquals(InventoryClassification.NORMAL, result.classification());
        assertNull(result.priority());
    }

    @Test
    void criticalPriorityWhenCoverageAtOrBelowOneDay() {
        // available=1, avg=1.0 -> coverage exactly 1.0 day
        InventoryMetricCalculation result = InventoryMetricCalculation.calculate(1, 0, 7);

        assertEquals(InventoryClassification.STOCKOUT_RISK, result.classification());
        assertEquals(InventoryPriority.CRITICAL, result.priority());
    }

    @Test
    void zeroSalesWithPositiveAvailableIsOverstockWithUndefinedCoverage() {
        InventoryMetricCalculation result = InventoryMetricCalculation.calculate(10, 0, 0);

        assertEquals(InventoryClassification.OVERSTOCK, result.classification());
        assertNull(result.coverageDays());
        assertNull(result.priority());
    }

    @Test
    void zeroAvailableAndZeroSalesIsNonActionable() {
        InventoryMetricCalculation result = InventoryMetricCalculation.calculate(0, 0, 0);

        assertEquals(InventoryClassification.NON_ACTIONABLE, result.classification());
        assertNull(result.coverageDays());
        assertNull(result.priority());
    }

    @Test
    void negativeQuantityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> InventoryMetricCalculation.calculate(-1, 0, 0));
    }

    @Test
    void reservedExceedingOnHandIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> InventoryMetricCalculation.calculate(5, 6, 0));
    }

    private static BigDecimal display(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
