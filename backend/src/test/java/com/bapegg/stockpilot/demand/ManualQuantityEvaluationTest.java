package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualQuantityEvaluationTest {

    private static final String SKU = "SKU-1";
    private static final String RECEIVER = "STORE-R";
    private static final String DONOR = "STORE-D";
    private static final String OWNER = "OWNER-A";
    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 1, 1);

    @Test
    void requestedQuantityMatchingRecommendedBaseIsFeasibleAndNeedsNoReason() {
        ManualQuantityEvaluation evaluation = calculateFixtureA(8);

        assertEquals(8, evaluation.recommendedBaseQuantity());
        assertTrue(evaluation.feasible());
        assertFalse(evaluation.reasonRequired());
        assertTrue(evaluation.violations().isEmpty());
        assertEquals(90, evaluation.maximumFeasibleQuantity());
        assertEquals(8, evaluation.suggestedQuantity());
    }

    @Test
    void requestedQuantitySmallerThanBaseIsFeasibleAndNeedsAReason() {
        ManualQuantityEvaluation evaluation = calculateFixtureA(3);

        assertTrue(evaluation.feasible());
        assertTrue(evaluation.reasonRequired());
        assertEquals(3, evaluation.suggestedQuantity());
    }

    @Test
    void requestedQuantityLargerThanBaseButWithinLimitsIsFeasibleAndNeedsAReason() {
        ManualQuantityEvaluation evaluation = calculateFixtureA(20);

        assertTrue(evaluation.feasible());
        assertTrue(evaluation.reasonRequired());
        assertEquals(20, evaluation.suggestedQuantity());
    }

    @Test
    void feasibleResultProjectionMatchesExactBeforeAfterCoverageAndRisk() {
        ManualQuantityEvaluation evaluation = calculateFixtureA(8);

        ManualQuantityProjection projection = evaluation.projection();
        assertEquals(5, projection.receiverBeforeAvailable());
        assertEquals(13, projection.receiverAfterAvailable());
        assertEquals(0, new BigDecimal("5").compareTo(projection.receiverBeforeCoverageDays()));
        assertEquals(0, new BigDecimal("13").compareTo(projection.receiverAfterCoverageDays()));
        assertEquals(InventoryExceptionType.NORMAL, projection.receiverRiskCode());
        assertEquals(100, projection.donorBeforeAvailable());
        assertEquals(92, projection.donorAfterAvailable());
        assertEquals(0, new BigDecimal("100").compareTo(projection.donorBeforeCoverageDays()));
        assertEquals(0, new BigDecimal("92").compareTo(projection.donorAfterCoverageDays()));
        assertEquals(InventoryExceptionType.OVERSTOCK, projection.donorRiskCode());
        assertEquals(4, projection.leadTimeDays());
        assertEquals(ANALYSIS_DATE.plusDays(4), projection.expectedArrivalDate());
    }

    @Test
    void belowRouteMinimumViolationForcesTheSuggestionToZero() {
        TransferRoute route = new TransferRoute(true, false, 0, 50, 1, 100);
        InventoryProjection receiverProjection = InventoryProjection.calculate(6, 1, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(100, 0, 0, 0, 0, 0, 0);

        ManualQuantityEvaluation evaluation = ManualQuantityEvaluation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route, ANALYSIS_DATE,
                receiverProjection, BigDecimal.ONE, 7, 2, 1000,
                donorProjection, BigDecimal.ONE, BigDecimal.ONE, 7, 0, 3,
                false, false, 10);

        // recommendedBaseQuantity is itself 0 here (receiverNeed=4 floors below the route's
        // minimum of 50), so per the eligibility-parity fix this is CANDIDATE_INELIGIBLE too,
        // alongside the requested-quantity-specific BELOW_ROUTE_MINIMUM violation.
        assertEquals(0, evaluation.recommendedBaseQuantity());
        assertEquals(
                List.of(ManualQuantityViolation.CANDIDATE_INELIGIBLE, ManualQuantityViolation.BELOW_ROUTE_MINIMUM),
                evaluation.violations());
        assertFalse(evaluation.feasible());
        assertEquals(0, evaluation.suggestedQuantity());
        assertNull(evaluation.projection());
    }

    @Test
    void notPackageMultipleViolationSuggestsTheFlooredQuantity() {
        TransferRoute route = new TransferRoute(true, false, 4, 1, 5, 100);
        InventoryProjection receiverProjection = InventoryProjection.calculate(6, 1, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(100, 0, 0, 0, 0, 0, 0);

        ManualQuantityEvaluation evaluation = ManualQuantityEvaluation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route, ANALYSIS_DATE,
                receiverProjection, BigDecimal.ONE, 7, 2, 1000,
                donorProjection, BigDecimal.ONE, BigDecimal.ONE, 7, 0, 3,
                false, false, 7);

        assertEquals(5, evaluation.recommendedBaseQuantity());
        assertEquals(List.of(ManualQuantityViolation.NOT_PACKAGE_MULTIPLE), evaluation.violations());
        assertFalse(evaluation.feasible());
        assertEquals(5, evaluation.suggestedQuantity());
        assertTrue(evaluation.reasonRequired());
        assertNull(evaluation.projection());
    }

    @Test
    void exceedsDonorTransferableIsAViolationWithNoProjection() {
        TransferRoute route = new TransferRoute(true, false, 0, 1, 1, 1000);
        InventoryProjection receiverProjection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(10, 0, 0, 0, 0, 0, 0);

        ManualQuantityEvaluation evaluation = ManualQuantityEvaluation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route, ANALYSIS_DATE,
                receiverProjection, BigDecimal.TEN, 30, 0, 100000,
                donorProjection, BigDecimal.ONE, BigDecimal.ZERO, 0, 0, 3,
                false, false, 9);

        assertEquals(7, evaluation.donorTransferableQuantity());
        assertEquals(List.of(ManualQuantityViolation.EXCEEDS_DONOR_TRANSFERABLE), evaluation.violations());
        assertFalse(evaluation.feasible());
        assertEquals(7, evaluation.suggestedQuantity());
        assertNull(evaluation.projection());
    }

    @Test
    void exceedsRouteMaximumIsAViolation() {
        TransferRoute route = new TransferRoute(true, false, 0, 1, 1, 10);
        InventoryProjection receiverProjection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(1000, 0, 0, 0, 0, 0, 0);

        ManualQuantityEvaluation evaluation = ManualQuantityEvaluation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route, ANALYSIS_DATE,
                receiverProjection, BigDecimal.TEN, 30, 0, 100000,
                donorProjection, BigDecimal.ONE, BigDecimal.ZERO, 0, 0, 0,
                false, false, 15);

        assertEquals(List.of(ManualQuantityViolation.EXCEEDS_ROUTE_MAXIMUM), evaluation.violations());
        assertFalse(evaluation.feasible());
        assertEquals(10, evaluation.suggestedQuantity());
    }

    @Test
    void exceedsReceiverCapacityIsAViolation() {
        TransferRoute route = new TransferRoute(true, false, 0, 1, 1, 1000);
        InventoryProjection receiverProjection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(1000, 0, 0, 0, 0, 0, 0);

        ManualQuantityEvaluation evaluation = ManualQuantityEvaluation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route, ANALYSIS_DATE,
                receiverProjection, BigDecimal.TEN, 30, 0, 4,
                donorProjection, BigDecimal.ONE, BigDecimal.ZERO, 0, 0, 0,
                false, false, 6);

        assertEquals(4, evaluation.receiverCapacityRemaining());
        assertEquals(List.of(ManualQuantityViolation.EXCEEDS_RECEIVER_CAPACITY), evaluation.violations());
        assertFalse(evaluation.feasible());
        assertEquals(4, evaluation.suggestedQuantity());
    }

    @Test
    void candidateIneligibleViaOwnerMismatchIsAViolationEvenAtTheRecommendedBaseQuantity() {
        TransferRoute route = new TransferRoute(true, false, 4, 1, 1, 100);
        InventoryProjection receiverProjection = InventoryProjection.calculate(6, 1, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(100, 0, 0, 0, 0, 0, 0);

        ManualQuantityEvaluation evaluation = ManualQuantityEvaluation.calculate(
                SKU, RECEIVER, "OWNER-A", DONOR, "OWNER-B", route, ANALYSIS_DATE,
                receiverProjection, BigDecimal.ONE, 7, 2, 1000,
                donorProjection, BigDecimal.ONE, BigDecimal.ONE, 7, 0, 3,
                false, false, 8);

        assertEquals(8, evaluation.recommendedBaseQuantity());
        assertFalse(evaluation.reasonRequired(), "Matches the recommended BASE quantity exactly.");
        assertEquals(List.of(ManualQuantityViolation.CANDIDATE_INELIGIBLE), evaluation.violations());
        assertTrue(evaluation.candidateRejectionReasons().contains(TransferCandidateRejectionReason.OWNER_MISMATCH));
        assertFalse(evaluation.feasible());
        assertEquals(0, evaluation.maximumFeasibleQuantity());
        assertEquals(0, evaluation.suggestedQuantity());
        assertNull(evaluation.projection());
    }

    @Test
    void multipleIndependentViolationsAreAllReturnedTogetherInFixedOrder() {
        TransferRoute route = new TransferRoute(true, false, 0, 1, 5, 10);
        InventoryProjection receiverProjection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(1000, 0, 0, 0, 0, 0, 0);

        ManualQuantityEvaluation evaluation = ManualQuantityEvaluation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route, ANALYSIS_DATE,
                receiverProjection, BigDecimal.TEN, 30, 0, 100000,
                donorProjection, BigDecimal.ONE, BigDecimal.ZERO, 0, 0, 0,
                false, false, 13);

        assertEquals(
                List.of(ManualQuantityViolation.NOT_PACKAGE_MULTIPLE, ManualQuantityViolation.EXCEEDS_ROUTE_MAXIMUM),
                evaluation.violations());
        assertFalse(evaluation.feasible());
        assertEquals(10, evaluation.suggestedQuantity());
    }

    @Test
    void maximumFeasibleIsZeroWhenTheHardCeilingFallsBelowRouteMinimum() {
        TransferRoute route = new TransferRoute(true, false, 0, 50, 1, 1000);
        InventoryProjection receiverProjection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(10, 0, 0, 0, 0, 0, 0);

        ManualQuantityEvaluation evaluation = ManualQuantityEvaluation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route, ANALYSIS_DATE,
                receiverProjection, BigDecimal.TEN, 30, 0, 100000,
                donorProjection, BigDecimal.ONE, BigDecimal.ZERO, 0, 0, 0,
                false, false, 5);

        assertEquals(0, evaluation.recommendedBaseQuantity());
        assertEquals(0, evaluation.maximumFeasibleQuantity());
        assertEquals(0, evaluation.suggestedQuantity());
        assertEquals(
                List.of(ManualQuantityViolation.CANDIDATE_INELIGIBLE, ManualQuantityViolation.BELOW_ROUTE_MINIMUM),
                evaluation.violations());
        assertTrue(evaluation.candidateRejectionReasons()
                .contains(TransferCandidateRejectionReason.DISPLAY_MINIMUM_VIOLATION));
    }

    @Test
    void zeroReceiverNeedWithNoStructuralRejectionReasonIsStillCandidateIneligible() {
        // Receiver already holds far more than its target quantity, so receiverNeed == 0 and
        // recommendedBaseQuantity == 0, yet nothing about the candidate itself is structurally
        // rejected (owner match, route active/open, donor has ample transferable stock, no
        // capacity/lead-time/package-floor problem) -- candidateEvaluation().reasons() is empty.
        // Approval's own eligible() requires recommendedBaseQuantity > 0 too, so a real approval
        // attempt at this basis is STALE_RECOMMENDATION; the manual preview must reach the same
        // CANDIDATE_INELIGIBLE/infeasible conclusion instead of reporting requestedQuantity=1 as
        // feasible.
        TransferRoute route = new TransferRoute(true, false, 0, 1, 1, 100);
        InventoryProjection receiverProjection = InventoryProjection.calculate(20, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(100, 0, 0, 0, 0, 0, 0);

        ManualQuantityEvaluation evaluation = ManualQuantityEvaluation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route, ANALYSIS_DATE,
                receiverProjection, BigDecimal.ONE, 7, 2, 1000,
                donorProjection, BigDecimal.ONE, BigDecimal.ONE, 0, 0, 0,
                false, false, 1);

        assertEquals(0, evaluation.recommendedBaseQuantity());
        assertTrue(evaluation.candidateRejectionReasons().isEmpty(),
                "No structural candidate rejection reason -- ineligibility comes only from BASE == 0.");
        assertEquals(List.of(ManualQuantityViolation.CANDIDATE_INELIGIBLE), evaluation.violations());
        assertFalse(evaluation.feasible());
        assertEquals(0, evaluation.maximumFeasibleQuantity());
        assertEquals(0, evaluation.suggestedQuantity());
        assertNull(evaluation.projection());
    }

    @Test
    void rejectsANonPositiveRequestedQuantity() {
        TransferRoute route = new TransferRoute(true, false, 4, 1, 1, 100);
        InventoryProjection receiverProjection = InventoryProjection.calculate(6, 1, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(100, 0, 0, 0, 0, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> ManualQuantityEvaluation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route, ANALYSIS_DATE,
                receiverProjection, BigDecimal.ONE, 7, 2, 1000,
                donorProjection, BigDecimal.ONE, BigDecimal.ONE, 7, 0, 3,
                false, false, 0));
        assertThrows(IllegalArgumentException.class, () -> ManualQuantityEvaluation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route, ANALYSIS_DATE,
                receiverProjection, BigDecimal.ONE, 7, 2, 1000,
                donorProjection, BigDecimal.ONE, BigDecimal.ONE, 7, 0, 3,
                false, false, -3));
    }

    private static ManualQuantityEvaluation calculateFixtureA(long requestedQuantity) {
        TransferRoute route = new TransferRoute(true, false, 4, 1, 1, 100);
        InventoryProjection receiverProjection = InventoryProjection.calculate(6, 1, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(100, 0, 0, 0, 0, 0, 0);

        return ManualQuantityEvaluation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route, ANALYSIS_DATE,
                receiverProjection, BigDecimal.ONE, 7, 2, 1000,
                donorProjection, BigDecimal.ONE, BigDecimal.ONE, 7, 0, 3,
                false, false, requestedQuantity);
    }
}
