package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the candidate-rejection half of {@code data/seed/mvp2}'s GS-06, per
 * {@code knowledge/business-rules.md} section 7.
 */
class TransferCandidateEvaluationTest {

    private static final BigDecimal RATE_THREE = new BigDecimal("3.000000000000");
    private static final BigDecimal RATE_ONE = new BigDecimal("1.000000000000");
    private static final String SKU = "SKU-MVP2-GS06-ROUTE";
    private static final String RECEIVER_STORE_ID = "STORE-MVP2-RECEIVER-A";
    private static final String DONOR_STORE_ID = "STORE-MVP2-DONOR-B";

    @Test
    void gs06OwnerMismatchAndLeadTimeTooLongBothApply() {
        // Receiver STORE-MVP2-RECEIVER-A (owner OWNER-DEMO-A): on_hand 2, base rate 3.0/day.
        // Donor STORE-MVP2-DONOR-B (owner OWNER-DEMO-B): on_hand 90, high rate ~1.0/day.
        // Route DONOR-B->RECEIVER-A: active, no owner override, lead time 10 days.
        InventoryProjection receiverProjection = InventoryProjection.calculate(2, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(90, 0, 0, 0, 0, 0, 0);
        TransferRoute route = new TransferRoute(true, false, 10, 1, 1, 50);

        TransferCandidateEvaluation result = TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-B", route,
                receiverProjection, RATE_THREE, 100,
                donorProjection, RATE_ONE, 14, 1, 2,
                false, false);

        assertFalse(result.eligible());
        assertEquals(
                java.util.Set.of(TransferCandidateRejectionReason.OWNER_MISMATCH,
                        TransferCandidateRejectionReason.LEAD_TIME_TOO_LONG),
                result.reasons());
        assertEquals(TransferCandidateRejectionReason.OWNER_MISMATCH, result.representativeReason().orElseThrow());
    }

    @Test
    void reasonsSetIsUnmodifiable() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(2, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(90, 0, 0, 0, 0, 0, 0);
        TransferRoute route = new TransferRoute(true, false, 10, 1, 1, 50);

        TransferCandidateEvaluation result = TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-B", route,
                receiverProjection, RATE_THREE, 100,
                donorProjection, RATE_ONE, 14, 1, 2,
                false, false);

        assertThrows(UnsupportedOperationException.class,
                () -> result.reasons().add(TransferCandidateRejectionReason.CAPACITY_EXCEEDED));
        assertThrows(UnsupportedOperationException.class,
                () -> result.reasons().remove(TransferCandidateRejectionReason.OWNER_MISMATCH));
    }

    @Test
    void reasonsSetIsUnmodifiableEvenWhenEligible() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(50, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 50);

        TransferCandidateEvaluation result = TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-A", route,
                receiverProjection, RATE_ONE, 100,
                donorProjection, RATE_ONE, 14, 1, 2,
                false, false);

        assertThrows(UnsupportedOperationException.class,
                () -> result.reasons().add(TransferCandidateRejectionReason.CAPACITY_EXCEEDED));
    }

    @Test
    void sameStoreForReceiverAndDonorIsRejected() {
        InventoryProjection projection = InventoryProjection.calculate(50, 0, 0, 0, 0, 0, 0);
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 50);

        assertThrows(IllegalArgumentException.class, () -> TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", RECEIVER_STORE_ID, "OWNER-DEMO-A", route,
                projection, RATE_ONE, 100,
                projection, RATE_ONE, 14, 1, 2,
                false, false));
    }

    @Test
    void blankSkuOrStoreIdIsRejected() {
        InventoryProjection projection = InventoryProjection.calculate(50, 0, 0, 0, 0, 0, 0);
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 50);

        assertThrows(IllegalArgumentException.class, () -> TransferCandidateEvaluation.evaluate(
                " ", RECEIVER_STORE_ID, "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-A", route,
                projection, RATE_ONE, 100, projection, RATE_ONE, 14, 1, 2, false, false));
        assertThrows(IllegalArgumentException.class, () -> TransferCandidateEvaluation.evaluate(
                SKU, " ", "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-A", route,
                projection, RATE_ONE, 100, projection, RATE_ONE, 14, 1, 2, false, false));
        assertThrows(IllegalArgumentException.class, () -> TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", " ", "OWNER-DEMO-A", route,
                projection, RATE_ONE, 100, projection, RATE_ONE, 14, 1, 2, false, false));
    }

    @Test
    void arrivalExactlyOnTheStockoutDayIsNotLeadTimeTooLong() {
        // atArrival = projectedReceiverBeforeDemand - ceil(rate*leadTimeDays) = 0 exactly:
        // arriving precisely on the expected stockout day is "not later than" it, so this must
        // not be rejected (only a strictly negative atArrival -- already stocked out before
        // arrival -- is too late).
        InventoryProjection receiverProjection = InventoryProjection.calculate(3, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 50);

        TransferCandidateEvaluation result = TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-A", route,
                receiverProjection, RATE_THREE, 300,
                donorProjection, RATE_ONE, 14, 1, 2,
                false, false);

        assertFalse(result.reasons().contains(TransferCandidateRejectionReason.LEAD_TIME_TOO_LONG));
    }

    @Test
    void arrivalOneUnitPastTheStockoutDayIsLeadTimeTooLong() {
        // Same as above but on_hand 2 instead of 3: atArrival = 2 - ceil(3*1) = -1.
        InventoryProjection receiverProjection = InventoryProjection.calculate(2, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 50);

        TransferCandidateEvaluation result = TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-A", route,
                receiverProjection, RATE_THREE, 300,
                donorProjection, RATE_ONE, 14, 1, 2,
                false, false);

        assertTrue(result.reasons().contains(TransferCandidateRejectionReason.LEAD_TIME_TOO_LONG));
    }

    @Test
    void ownerOverrideOnTheRouteWaivesTheOwnerMismatch() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        TransferRoute route = new TransferRoute(true, true, 1, 1, 1, 50);

        TransferCandidateEvaluation result = TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-B", route,
                receiverProjection, RATE_ONE, 300,
                donorProjection, RATE_ONE, 14, 1, 2,
                false, false);

        assertTrue(result.eligible());
    }

    @Test
    void missingRouteIsRouteNotAllowedAndSkipsRouteDependentChecks() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(2, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);

        TransferCandidateEvaluation result = TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-A", null,
                receiverProjection, RATE_THREE, 300,
                donorProjection, RATE_ONE, 14, 1, 2,
                false, false);

        assertEquals(java.util.Set.of(TransferCandidateRejectionReason.ROUTE_NOT_ALLOWED), result.reasons());
    }

    @Test
    void inactiveRouteIsRouteNotAllowed() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        TransferRoute inactiveRoute = new TransferRoute(false, false, 1, 1, 1, 50);

        TransferCandidateEvaluation result = TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-A", inactiveRoute,
                receiverProjection, RATE_ONE, 300,
                donorProjection, RATE_ONE, 14, 1, 2,
                false, false);

        assertTrue(result.reasons().contains(TransferCandidateRejectionReason.ROUTE_NOT_ALLOWED));
    }

    @Test
    void noTransferableStockWhenDonorHasNoSurplusBeyondProtection() {
        // donorProtected = ceil(1*14) + 1 + 2 = 17; available 17 leaves transferable 0.
        InventoryProjection donorProjection = InventoryProjection.calculate(17, 0, 0, 0, 0, 0, 0);
        InventoryProjection receiverProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 50);

        TransferCandidateEvaluation result = TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-A", route,
                receiverProjection, RATE_ONE, 300,
                donorProjection, RATE_ONE, 14, 1, 2,
                false, false);

        assertTrue(result.reasons().contains(TransferCandidateRejectionReason.NO_TRANSFERABLE_STOCK));
    }

    @Test
    void confirmedInboundAlreadyCoveringTheShortageIsPassedThrough() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 50);

        TransferCandidateEvaluation result = TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-A", route,
                receiverProjection, RATE_ONE, 300,
                donorProjection, RATE_ONE, 14, 1, 2,
                true, false);

        assertEquals(java.util.Set.of(TransferCandidateRejectionReason.INBOUND_ALREADY_COVERS), result.reasons());
    }

    @Test
    void pendingTransferConflictIsPassedThrough() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 50);

        TransferCandidateEvaluation result = TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-A", route,
                receiverProjection, RATE_ONE, 300,
                donorProjection, RATE_ONE, 14, 1, 2,
                false, true);

        assertEquals(java.util.Set.of(TransferCandidateRejectionReason.PENDING_TRANSFER_CONFLICT), result.reasons());
    }

    @Test
    void capacityExceededWhenReceiverHasNoRemainingRoom() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(100, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 50);

        TransferCandidateEvaluation result = TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-A", route,
                receiverProjection, RATE_ONE, 100, // maximumCapacity == projected available -> zero headroom
                donorProjection, RATE_ONE, 14, 1, 2,
                false, false);

        assertTrue(result.reasons().contains(TransferCandidateRejectionReason.CAPACITY_EXCEEDED));
        assertTrue(result.reasons().contains(TransferCandidateRejectionReason.DISPLAY_MINIMUM_VIOLATION));
    }

    @Test
    void displayMinimumViolationWhenTheFeasibleShipmentCannotReachTheRouteMinimum() {
        // Route requires at least 10 units, but receiver capacity headroom is only 5.
        InventoryProjection receiverProjection = InventoryProjection.calculate(95, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        TransferRoute route = new TransferRoute(true, false, 1, 10, 1, 50);

        TransferCandidateEvaluation result = TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-A", route,
                receiverProjection, RATE_ONE, 100,
                donorProjection, RATE_ONE, 14, 1, 2,
                false, false);

        assertTrue(result.reasons().contains(TransferCandidateRejectionReason.DISPLAY_MINIMUM_VIOLATION));
        assertFalse(result.reasons().contains(TransferCandidateRejectionReason.CAPACITY_EXCEEDED));
    }

    @Test
    void eligibleCandidateHasNoReasons() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(50, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(200, 0, 0, 0, 0, 0, 0);
        TransferRoute route = new TransferRoute(true, false, 1, 1, 1, 50);

        TransferCandidateEvaluation result = TransferCandidateEvaluation.evaluate(
                SKU, RECEIVER_STORE_ID, "OWNER-DEMO-A", DONOR_STORE_ID, "OWNER-DEMO-A", route,
                receiverProjection, RATE_ONE, 100,
                donorProjection, RATE_ONE, 14, 1, 2,
                false, false);

        assertTrue(result.eligible());
        assertTrue(result.representativeReason().isEmpty());
    }

    @Test
    void routeRejectsNegativeLeadTime() {
        assertThrows(IllegalArgumentException.class, () -> new TransferRoute(true, false, -1, 1, 1, 50));
    }

    @Test
    void routeRejectsNonPositiveMinimumOrPackageMultiple() {
        assertThrows(IllegalArgumentException.class, () -> new TransferRoute(true, false, 1, 0, 1, 50));
        assertThrows(IllegalArgumentException.class, () -> new TransferRoute(true, false, 1, 1, 0, 50));
    }

    @Test
    void routeRejectsMaximumBelowMinimum() {
        assertThrows(IllegalArgumentException.class, () -> new TransferRoute(true, false, 1, 10, 1, 5));
    }
}
