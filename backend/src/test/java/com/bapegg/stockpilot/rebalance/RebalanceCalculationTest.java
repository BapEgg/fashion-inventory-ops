package com.bapegg.stockpilot.rebalance;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void largeSoldQuantityDoesNotOverflowDuringCeilingCalculation() {
        // donorSold * DONOR_RETAINED_COVERAGE_DAYS = 200_000_000 * 14 = 2_800_000_000,
        // which overflows a 32-bit int (wraps to a negative number) if the multiplication
        // is not widened to long before ceilDiv. donorRetained = ceilDiv(2_800_000_000, 7)
        // + 2 = 400_000_002 exactly; donorTransferable = 500_000_000 - 400_000_002 =
        // 99_999_998. A wrapped/negative intermediate would corrupt this result.
        Optional<RebalanceCalculation> result = RebalanceCalculation.calculate(
                1, 0, 200_000_000, 500_000_000);

        assertTrue(result.isPresent());
        assertEquals(99_999_998, result.get().donorTransferableQuantity());
        assertEquals(3, result.get().receiverShortageQuantity());
        assertEquals(3, result.get().recommendedQuantity());
    }

    @Test
    void receiverTargetOverflowAfterAddingSafetyStockIsRejectedNotWrapped() {
        // ceilDiv(MAX_VALUE * 7 / 7) = MAX_VALUE exactly (fits in int), but adding
        // SAFETY_STOCK_UNITS (2) pushes the true target to MAX_VALUE + 2, which does
        // not fit in an int. A previous implementation added SAFETY_STOCK_UNITS in
        // plain int arithmetic after ceilDiv already returned int, wrapping to a
        // negative target instead of failing loudly.
        assertThrows(ArithmeticException.class,
                () -> RebalanceCalculation.calculate(Integer.MAX_VALUE, 0, 1, 10));
    }

    @Test
    void donorRetainedOverflowAfterAddingSafetyStockIsRejectedNotWrapped() {
        // donorRetained's true value (MAX_VALUE * 2 + 2) is far outside int range.
        assertThrows(ArithmeticException.class,
                () -> RebalanceCalculation.calculate(1, 10, Integer.MAX_VALUE, 0));
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
