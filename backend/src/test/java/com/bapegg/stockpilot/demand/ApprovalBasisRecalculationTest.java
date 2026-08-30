package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalBasisRecalculationTest {

    private static final String SKU = "SKU-1";
    private static final String RECEIVER = "STORE-R";
    private static final String DONOR = "STORE-D";
    private static final String OWNER = "OWNER-A";

    @Test
    void computesTheFlooredBaseQuantityWithinAllLimits() {
        TransferRoute route = new TransferRoute(true, false, 4, 1, 5, 100);
        InventoryProjection receiverProjection = InventoryProjection.calculate(6, 1, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(100, 0, 0, 0, 0, 0, 0);
        BigDecimal receiverRate = BigDecimal.ONE;

        long expectedTarget = receiverProjection.receiverTargetQuantity(receiverRate, route.leadTimeDays(), 7, 2);
        long expectedNeed = Math.max(expectedTarget - receiverProjection.projectedReceiverBeforeDemand(), 0);
        long expectedFloored = (expectedNeed / route.packageMultiple()) * route.packageMultiple();

        ApprovalBasisRecalculation recalculation = ApprovalBasisRecalculation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route,
                receiverProjection, receiverRate, 7, 2, 1000,
                donorProjection, BigDecimal.ONE, 7, 2, 3,
                false, false);

        assertEquals(expectedFloored, recalculation.recommendedBaseQuantity());
        assertTrue(recalculation.eligible());
        assertTrue(recalculation.candidateEvaluation().eligible());
    }

    @Test
    void flooredQuantityBelowRouteMinimumBecomesZeroAndIneligible() {
        TransferRoute route = new TransferRoute(true, false, 0, 50, 1, 100);
        InventoryProjection receiverProjection = InventoryProjection.calculate(6, 1, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(100, 0, 0, 0, 0, 0, 0);

        ApprovalBasisRecalculation recalculation = ApprovalBasisRecalculation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route,
                receiverProjection, BigDecimal.ONE, 7, 2, 1000,
                donorProjection, BigDecimal.ONE, 7, 2, 3,
                false, false);

        assertEquals(0, recalculation.recommendedBaseQuantity());
        assertFalse(recalculation.eligible());
    }

    @Test
    void donorTransferableQuantityCapsTheBaseQuantity() {
        TransferRoute route = new TransferRoute(true, false, 0, 1, 1, 1000);
        InventoryProjection receiverProjection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(10, 0, 0, 0, 0, 0, 0);

        ApprovalBasisRecalculation recalculation = ApprovalBasisRecalculation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route,
                receiverProjection, BigDecimal.TEN, 30, 0, 100000,
                donorProjection, BigDecimal.ZERO, 0, 0, 3,
                false, false);

        assertEquals(7, recalculation.donorTransferableQuantity());
        assertEquals(7, recalculation.recommendedBaseQuantity());
    }

    @Test
    void receiverCapacityCapsTheBaseQuantity() {
        TransferRoute route = new TransferRoute(true, false, 0, 1, 1, 1000);
        InventoryProjection receiverProjection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(1000, 0, 0, 0, 0, 0, 0);

        ApprovalBasisRecalculation recalculation = ApprovalBasisRecalculation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route,
                receiverProjection, BigDecimal.TEN, 30, 0, 4,
                donorProjection, BigDecimal.ZERO, 0, 0, 0,
                false, false);

        assertEquals(4, recalculation.receiverCapacityRemaining());
        assertEquals(4, recalculation.recommendedBaseQuantity());
    }

    @Test
    void confirmedInboundCoveringTheEntireNeedIsReportedToTheCandidateEvaluation() {
        TransferRoute route = new TransferRoute(true, false, 0, 1, 1, 1000);
        InventoryProjection receiverProjection = InventoryProjection.calculate(0, 0, 1000, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(100, 0, 0, 0, 0, 0, 0);

        ApprovalBasisRecalculation recalculation = ApprovalBasisRecalculation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route,
                receiverProjection, BigDecimal.ONE, 7, 2, 1000,
                donorProjection, BigDecimal.ONE, 0, 0, 0,
                true, false);

        assertEquals(0, recalculation.recommendedBaseQuantity());
        assertTrue(recalculation.candidateEvaluation().reasons()
                .contains(TransferCandidateRejectionReason.INBOUND_ALREADY_COVERS));
        assertFalse(recalculation.eligible());
    }

    @Test
    void pendingTransferConflictIsReportedToTheCandidateEvaluation() {
        TransferRoute route = new TransferRoute(true, false, 0, 1, 1, 1000);
        InventoryProjection receiverProjection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(100, 0, 0, 0, 0, 0, 0);

        ApprovalBasisRecalculation recalculation = ApprovalBasisRecalculation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route,
                receiverProjection, BigDecimal.ONE, 7, 2, 1000,
                donorProjection, BigDecimal.ONE, 0, 0, 0,
                false, true);

        assertTrue(recalculation.candidateEvaluation().reasons()
                .contains(TransferCandidateRejectionReason.PENDING_TRANSFER_CONFLICT));
        assertFalse(recalculation.eligible());
    }

    @Test
    void ownerMismatchMakesTheCandidateIneligibleEvenWithAPositiveBaseQuantity() {
        TransferRoute route = new TransferRoute(true, false, 0, 1, 1, 1000);
        InventoryProjection receiverProjection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(100, 0, 0, 0, 0, 0, 0);

        ApprovalBasisRecalculation recalculation = ApprovalBasisRecalculation.calculate(
                SKU, RECEIVER, "OWNER-A", DONOR, "OWNER-B", route,
                receiverProjection, BigDecimal.ONE, 7, 2, 1000,
                donorProjection, BigDecimal.ONE, 0, 0, 0,
                false, false);

        assertTrue(recalculation.candidateEvaluation().reasons()
                .contains(TransferCandidateRejectionReason.OWNER_MISMATCH));
        assertTrue(recalculation.recommendedBaseQuantity() > 0);
        assertFalse(recalculation.eligible());
    }

    @Test
    void rejectsANullRouteOrNonPositiveReceiverMaximumCapacity() {
        InventoryProjection receiverProjection = InventoryProjection.calculate(6, 1, 0, 0, 0, 0, 0);
        InventoryProjection donorProjection = InventoryProjection.calculate(100, 0, 0, 0, 0, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> ApprovalBasisRecalculation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, null,
                receiverProjection, BigDecimal.ONE, 7, 2, 100,
                donorProjection, BigDecimal.ONE, 0, 0, 0, false, false));

        TransferRoute route = new TransferRoute(true, false, 0, 1, 1, 1000);
        assertThrows(IllegalArgumentException.class, () -> ApprovalBasisRecalculation.calculate(
                SKU, RECEIVER, OWNER, DONOR, OWNER, route,
                receiverProjection, BigDecimal.ONE, 7, 2, 0,
                donorProjection, BigDecimal.ONE, 0, 0, 0, false, false));
    }
}
