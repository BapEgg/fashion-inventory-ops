package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.analysis.InventoryMetricCalculation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden Scenario Hongdae -> Gangnam recommendation from
 * {@code knowledge/business-rules.md} section 8.
 */
class RebalanceCalculationTest {

    @Test
    void hongdaeToGangnamRecommendsTwentyFiveUnits() {
        InventoryMetricCalculation gangnam = InventoryMetricCalculation.calculate(6, 1, 28);
        InventoryMetricCalculation hongdae = InventoryMetricCalculation.calculate(42, 2, 4);

        Optional<RebalanceCalculation> result = RebalanceCalculation.calculate(
                gangnam.averageDailySales(), gangnam.availableQuantity(),
                hongdae.averageDailySales(), hongdae.availableQuantity());

        assertTrue(result.isPresent());
        assertEquals(25, result.get().receiverShortageQuantity());
        assertEquals(30, result.get().donorTransferableQuantity());
        assertEquals(25, result.get().recommendedQuantity());
    }

    @Test
    void noRecommendationWhenDonorHasNoTransferableSurplus() {
        Optional<RebalanceCalculation> result = RebalanceCalculation.calculate(
                new BigDecimal("4.00"), 5,
                new BigDecimal("2.00"), 30);

        // donorRetained = ceil(2.00 * 14) + 2 = 30; donorTransferable = max(30-30,0) = 0
        assertTrue(result.isEmpty());
    }

    @Test
    void noRecommendationWhenReceiverHasNoShortage() {
        Optional<RebalanceCalculation> result = RebalanceCalculation.calculate(
                new BigDecimal("1.00"), 50,
                new BigDecimal("0.50"), 40);

        // receiverTarget = ceil(1.00*7)+2 = 9; receiverShortage = max(9-50,0) = 0
        assertTrue(result.isEmpty());
    }
}
