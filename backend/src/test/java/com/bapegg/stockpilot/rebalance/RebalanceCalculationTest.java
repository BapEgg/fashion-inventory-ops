package com.bapegg.stockpilot.rebalance;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden Scenario Hongdae -> Gangnam recommendation from
 * {@code knowledge/business-rules.md} section 8, plus a regression case for the
 * unrounded-sales-rate boundary defect found in review: reusing a rounded
 * {@code averageDailySales} across the {@code ceil} transfer boundary could inflate an
 * exact-integer target by one unit (e.g. {@code 1/7 * 7} rounding to just over
 * {@code 1.0}). Calculating directly from the raw integer sold quantity avoids that.
 */
class RebalanceCalculationTest {

    @Test
    void hongdaeToGangnamRecommendsTwentyFiveUnits() {
        // Gangnam (receiver): sold 28 in 7 days, available 5.
        // Hongdae (donor): sold 4 in 7 days, available 40.
        Optional<RebalanceCalculation> result = RebalanceCalculation.calculate(28, 5, 4, 40);

        assertTrue(result.isPresent());
        assertEquals(25, result.get().receiverShortageQuantity());
        assertEquals(30, result.get().donorTransferableQuantity());
        assertEquals(25, result.get().recommendedQuantity());
    }

    @Test
    void oneSaleInWindowDoesNotInflateReceiverTargetByOneUnit() {
        // receiverTarget = ceil(1/7 * 7) + 2 = 1 + 2 = 3, exactly - not 4.
        // A previous implementation rounded 1/7 to 10 decimals before multiplying by 7,
        // landing just above 1.0 and inflating ceil(...) to 2.
        Optional<RebalanceCalculation> result = RebalanceCalculation.calculate(1, 0, 100, 1000);

        assertTrue(result.isPresent());
        assertEquals(3, result.get().receiverShortageQuantity());
    }

    @Test
    void oneSaleInWindowDoesNotInflateDonorRetainedByOneUnit() {
        // donorRetained = ceil(1/7 * 14) + 2 = 2 + 2 = 4, exactly - not 5.
        // receiver has a real shortage (target 102, available 0) so the recommendation
        // is present and donorTransferableQuantity is observable.
        Optional<RebalanceCalculation> result = RebalanceCalculation.calculate(100, 0, 1, 10);

        assertTrue(result.isPresent());
        // donorTransferable = max(10 - 4, 0) = 6
        assertEquals(6, result.get().donorTransferableQuantity());
    }

    @Test
    void noRecommendationWhenDonorHasNoTransferableSurplus() {
        // donorRetained = ceil(2*14/7) + 2 = 4 + 2 = 6; with available=6, transferable = 0.
        Optional<RebalanceCalculation> result = RebalanceCalculation.calculate(4, 5, 2, 6);

        assertTrue(result.isEmpty());
    }

    @Test
    void noRecommendationWhenReceiverHasNoShortage() {
        // receiverTarget = ceil(7*7/7) + 2 = 7 + 2 = 9; with available=50, shortage = 0.
        Optional<RebalanceCalculation> result = RebalanceCalculation.calculate(7, 50, 3, 40);

        assertTrue(result.isEmpty());
    }
}
