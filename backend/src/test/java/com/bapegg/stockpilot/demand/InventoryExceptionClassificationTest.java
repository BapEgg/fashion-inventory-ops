package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reproduces the exception/severity half of {@code data/seed/mvp2}'s GS-05, per
 * {@code knowledge/business-rules.md} sections 4, 6 and 9. Candidate/route rejection reasons
 * (section 7, e.g. {@code INBOUND_ALREADY_COVERS}) are order item 5's responsibility and are
 * not exercised here.
 */
class InventoryExceptionClassificationTest {

    private static final DemandRateCalculation STABLE_RATE = new DemandRateCalculation(
            List.of(new BigDecimal("3.000000000000")),
            new BigDecimal("3.000000000000"), new BigDecimal("3.000000000000"), new BigDecimal("3.000000000000"),
            false);

    @Test
    void criticalStockoutRiskWithoutTheConfirmedInbound() {
        // GS-05's receiver (base rate 3.0/day, fastest arrival 1 day) with on_hand 2 and no
        // inbound reflected yet: BASE projected stock at the earliest arrival is negative.
        // hasActionableCandidate=true here specifically to prove CRITICAL wins over HIGH: an
        // already-past-earliest-arrival stockout is CRITICAL regardless of candidate
        // availability (section 9), not merely CRITICAL-when-no-candidate-exists.
        InventoryProjection projection = InventoryProjection.calculate(2, 0, 0, 0, 0, 0, 0);

        InventoryExceptionClassification result = InventoryExceptionClassification.classify(
                projection, DemandConfidence.HIGH, STABLE_RATE, 1, 7, 14, 1, 2, true);

        assertEquals(InventoryExceptionType.STOCKOUT_RISK, result.exceptionType());
        assertEquals(InventorySeverity.CRITICAL, result.severity());
    }

    @Test
    void gs05ConfirmedInboundResolvesTheShortage() {
        // Same store-SKU as above, with the real GS-05 confirmed 50-unit inbound reflected on
        // both projections. This removes the STOCKOUT_RISK entirely (projected 52 clears both
        // the non-positive-at-arrival and target-coverage checks); the exact demo policy
        // numbers (retained 14 days at rate 3.0, display 1, safety 2 -> protect 45) leave a
        // small 7-unit transferable surplus, so this specific input lands on OVERSTOCK rather
        // than NORMAL -- that is an honest consequence of the chosen 50-unit quantity, not a
        // forced or adjusted result.
        InventoryProjection projection = InventoryProjection.calculate(2, 0, 50, 0, 0, 50, 0);

        InventoryExceptionClassification result = InventoryExceptionClassification.classify(
                projection, DemandConfidence.HIGH, STABLE_RATE, 1, 7, 14, 1, 2, false);

        assertEquals(InventoryExceptionType.OVERSTOCK, result.exceptionType());
        assertNull(result.severity());
    }

    @Test
    void nonActionableWhenProjectionIsNegativeRegardlessOfConfidenceOrRate() {
        InventoryProjection projection = InventoryProjection.calculate(5, 0, 0, 0, 10, 0, 0);

        InventoryExceptionClassification result = InventoryExceptionClassification.classify(
                projection, DemandConfidence.HIGH, STABLE_RATE, 1, 7, 14, 1, 2, true);

        assertEquals(InventoryExceptionType.NON_ACTIONABLE, result.exceptionType());
        assertNull(result.severity());
    }

    @Test
    void nonActionableWhenReservedExceedsOnHandRegardlessOfConfidenceOrRate() {
        // Section 2's other NON_ACTIONABLE input condition: reserved > on-hand. This must not
        // throw at the projection boundary and must win over an otherwise-healthy signal.
        InventoryProjection projection = InventoryProjection.calculate(0, 5, 0, 0, 0, 0, 0);

        InventoryExceptionClassification result = InventoryExceptionClassification.classify(
                projection, DemandConfidence.HIGH, STABLE_RATE, 1, 7, 14, 1, 2, false);

        assertEquals(-5, projection.currentAvailable());
        assertEquals(InventoryExceptionType.NON_ACTIONABLE, result.exceptionType());
        assertNull(result.severity());
    }

    @Test
    void noneConfidenceIsReviewRequired() {
        InventoryProjection projection = InventoryProjection.calculate(20, 0, 0, 0, 0, 0, 0);

        InventoryExceptionClassification result = InventoryExceptionClassification.classify(
                projection, DemandConfidence.NONE, STABLE_RATE, 1, 7, 14, 1, 2, false);

        assertEquals(InventoryExceptionType.REVIEW_REQUIRED, result.exceptionType());
        assertEquals(InventorySeverity.REVIEW, result.severity());
    }

    @Test
    void lowConfidenceIsReviewRequiredEvenForAnOtherwiseStableRepeatSignal() {
        // Section 4: any quality flag (e.g. OOS_CENSORED) downgrades confidence to LOW even for
        // STABLE_REPEAT. Classification must key off the actual computed confidence, not a
        // partial list of "risky" signal types, so this otherwise-healthy-looking metric still
        // routes to REVIEW_REQUIRED.
        InventoryProjection projection = InventoryProjection.calculate(20, 0, 0, 0, 0, 0, 0);

        InventoryExceptionClassification result = InventoryExceptionClassification.classify(
                projection, DemandConfidence.LOW, STABLE_RATE, 1, 7, 14, 1, 2, false);

        assertEquals(InventoryExceptionType.REVIEW_REQUIRED, result.exceptionType());
        assertEquals(InventorySeverity.REVIEW, result.severity());
    }

    @Test
    void reviewRequiredRatesForceReviewRequiredEvenForHighConfidence() {
        InventoryProjection projection = InventoryProjection.calculate(20, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation reviewRequiredRates =
                new DemandRateCalculation(List.of(BigDecimal.ZERO, BigDecimal.ZERO), null, null, null, true);

        InventoryExceptionClassification result = InventoryExceptionClassification.classify(
                projection, DemandConfidence.HIGH, reviewRequiredRates, 1, 7, 14, 1, 2, true);

        assertEquals(InventoryExceptionType.REVIEW_REQUIRED, result.exceptionType());
    }

    @Test
    void stockoutRiskFromTargetCoverageShortfallWithoutAnActionableCandidateHasUndeterminedSeverity() {
        // atArrival = 10 - ceil(2*1) = 8 > 0 (not CRITICAL), but 10 < target(17): still
        // STOCKOUT_RISK. Without an actionable candidate, section 9's HIGH does not apply, so
        // severity stays undetermined (null) rather than guessed.
        InventoryProjection projection = InventoryProjection.calculate(10, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rate = new DemandRateCalculation(
                List.of(new BigDecimal("2.000000000000")),
                new BigDecimal("2.000000000000"), new BigDecimal("2.000000000000"), new BigDecimal("2.000000000000"),
                false);

        InventoryExceptionClassification result = InventoryExceptionClassification.classify(
                projection, DemandConfidence.HIGH, rate, 1, 7, 14, 1, 2, false);

        assertEquals(InventoryExceptionType.STOCKOUT_RISK, result.exceptionType());
        assertNull(result.severity());
    }

    @Test
    void stockoutRiskFromTargetCoverageShortfallWithAnActionableCandidateIsHigh() {
        // Same shortfall as above, but an actionable candidate exists this time: section 9's
        // HIGH ("목표 커버리지보다 부족하고 실행 가능한 조치가 있음") now applies.
        InventoryProjection projection = InventoryProjection.calculate(10, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rate = new DemandRateCalculation(
                List.of(new BigDecimal("2.000000000000")),
                new BigDecimal("2.000000000000"), new BigDecimal("2.000000000000"), new BigDecimal("2.000000000000"),
                false);

        InventoryExceptionClassification result = InventoryExceptionClassification.classify(
                projection, DemandConfidence.HIGH, rate, 1, 7, 14, 1, 2, true);

        assertEquals(InventoryExceptionType.STOCKOUT_RISK, result.exceptionType());
        assertEquals(InventorySeverity.HIGH, result.severity());
    }

    @Test
    void overstockWithAClearSurplus() {
        InventoryProjection projection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rate = new DemandRateCalculation(
                List.of(BigDecimal.ONE), new BigDecimal("1.000000000000"),
                new BigDecimal("1.000000000000"), new BigDecimal("1.000000000000"), false);

        InventoryExceptionClassification result = InventoryExceptionClassification.classify(
                projection, DemandConfidence.HIGH, rate, 1, 7, 14, 1, 2, true);

        assertEquals(InventoryExceptionType.OVERSTOCK, result.exceptionType());
        assertNull(result.severity());
    }

    @Test
    void negativePolicyInputIsRejectedEvenOnTheNonActionablePath() {
        // The projection itself is invalid (would otherwise short-circuit to NON_ACTIONABLE
        // before ever reaching InventoryProjection's own validating methods) -- the negative
        // safetyStock must still be caught, not silently ignored because this branch never
        // calls donorProtectedQuantity.
        InventoryProjection invalidProjection = InventoryProjection.calculate(5, 0, 0, 0, 10, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> InventoryExceptionClassification.classify(
                invalidProjection, DemandConfidence.HIGH, STABLE_RATE, 1, 7, 14, 1, -1, false));
    }

    @Test
    void negativePolicyInputIsRejectedEvenOnTheReviewRequiredPath() {
        // confidence == NONE short-circuits to REVIEW_REQUIRED before ever reaching
        // InventoryProjection's own validating methods -- the negative retainedDays must
        // still be caught here too.
        InventoryProjection projection = InventoryProjection.calculate(20, 0, 0, 0, 0, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> InventoryExceptionClassification.classify(
                projection, DemandConfidence.NONE, STABLE_RATE, 1, 7, -14, 1, 2, false));
    }

    @Test
    void normalWhenNeitherThresholdIsCrossed() {
        // target = ceil(2*8)+1 = 17; donorProtected = ceil(2*14)+1+2 = 31; projected = 25 sits
        // strictly between the two, so neither STOCKOUT_RISK nor OVERSTOCK applies.
        InventoryProjection projection = InventoryProjection.calculate(25, 0, 0, 0, 0, 0, 0);
        DemandRateCalculation rate = new DemandRateCalculation(
                List.of(new BigDecimal("2.000000000000")),
                new BigDecimal("2.000000000000"), new BigDecimal("2.000000000000"), new BigDecimal("2.000000000000"),
                false);

        InventoryExceptionClassification result = InventoryExceptionClassification.classify(
                projection, DemandConfidence.HIGH, rate, 1, 7, 14, 1, 2, true);

        assertEquals(InventoryExceptionType.NORMAL, result.exceptionType());
        assertNull(result.severity());
    }
}
