package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryProjectionTest {

    @Test
    void computesCurrentAvailableAndBothProjections() {
        // GS-05: on_hand 2, reserved 0, a confirmed 50-unit inbound counted on both sides,
        // no open transfers or drafts.
        InventoryProjection projection = InventoryProjection.calculate(2, 0, 50, 0, 0, 50, 0);

        assertEquals(2, projection.currentAvailable());
        assertEquals(52, projection.projectedReceiverBeforeDemand());
        assertEquals(52, projection.projectedDonorAtDispatch());
        assertFalse(projection.isInputInvalid());
    }

    @Test
    void negativeProjectedReceiverBeforeDemandIsFlaggedNotClampedOrRejected() {
        // openTransferOutbound (10) exceeds available+inbound (5): section 6 says this is an
        // input error that must not be auto-corrected to zero -- the raw negative value is kept.
        InventoryProjection projection = InventoryProjection.calculate(5, 0, 0, 0, 10, 0, 0);

        assertEquals(-5, projection.projectedReceiverBeforeDemand());
        assertTrue(projection.isInputInvalid());
    }

    @Test
    void negativeProjectedDonorAtDispatchIsAlsoFlagged() {
        InventoryProjection projection = InventoryProjection.calculate(5, 0, 0, 0, 0, 0, 10);

        assertEquals(-5, projection.projectedDonorAtDispatch());
        assertTrue(projection.isInputInvalid());
    }

    @Test
    void negativeRawQuantityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> InventoryProjection.calculate(-1, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void reservedExceedingOnHandIsFlaggedNotThrown() {
        // Section 2: reserved > on-hand is one of two NON_ACTIONABLE input conditions, not a
        // programming error -- it must not throw, and currentAvailable is not clamped to zero.
        InventoryProjection projection = InventoryProjection.calculate(5, 6, 0, 0, 0, 0, 0);

        assertEquals(-1, projection.currentAvailable());
        assertTrue(projection.isInputInvalid());
    }

    @Test
    void summingLargeInRangeInputsThatWouldOverflowIntIsRejectedNotWrapped() {
        // Each input individually fits an int, but their sum (MAX_VALUE + 10) does not.
        // Math.toIntExact must throw rather than silently wrap to a negative projection.
        assertThrows(ArithmeticException.class,
                () -> InventoryProjection.calculate(Integer.MAX_VALUE, 0, 10, 0, 0, 0, 0));
    }

    @Test
    void donorSideSummationOverflowIsAlsoRejectedNotWrapped() {
        assertThrows(ArithmeticException.class,
                () -> InventoryProjection.calculate(Integer.MAX_VALUE, 0, 0, 0, 0, 10, 0));
    }

    @Test
    void receiverAtArrivalWithoutNewTransferCanGoNegative() {
        InventoryProjection projection = InventoryProjection.calculate(2, 0, 0, 0, 0, 0, 0);

        // ceil(3.0 * 1) = 3; 2 - 3 = -1.
        assertEquals(-1, projection.receiverAtArrivalWithoutNewTransfer(new BigDecimal("3.000000000000"), 1));
    }

    @Test
    void receiverTargetQuantityMatchesTheSection8Formula() {
        InventoryProjection projection = InventoryProjection.calculate(2, 0, 0, 0, 0, 0, 0);

        // ceil(3.0 * (1 + 7)) + 1 = 24 + 1 = 25.
        assertEquals(25, projection.receiverTargetQuantity(new BigDecimal("3.000000000000"), 1, 7, 1));
    }

    @Test
    void donorProtectedQuantityMatchesTheSection8Formula() {
        InventoryProjection projection = InventoryProjection.calculate(2, 0, 0, 0, 0, 0, 0);

        // ceil(3.0 * 14) + 1 + 2 = 42 + 3 = 45.
        assertEquals(45, projection.donorProtectedQuantity(new BigDecimal("3.000000000000"), 14, 1, 2));
    }

    @Test
    void ceilingRoundsAFractionalDemandUp() {
        InventoryProjection projection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);

        // 20/7 = 2.857142857143 (scale 12) * 1 day -> ceil = 3, not 2.
        assertEquals(3L, projection.receiverTargetQuantity(new BigDecimal("2.857142857143"), 1, 0, 0));
    }

    @Test
    void negativeLeadTimeIsRejectedInReceiverAtArrivalWithoutNewTransfer() {
        InventoryProjection projection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> projection.receiverAtArrivalWithoutNewTransfer(BigDecimal.ONE, -1));
    }

    @Test
    void negativeInputsAreRejectedInReceiverTargetQuantity() {
        InventoryProjection projection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> projection.receiverTargetQuantity(BigDecimal.ONE, -1, 7, 1));
        assertThrows(IllegalArgumentException.class,
                () -> projection.receiverTargetQuantity(BigDecimal.ONE, 1, -7, 1));
        assertThrows(IllegalArgumentException.class,
                () -> projection.receiverTargetQuantity(BigDecimal.ONE, 1, 7, -1));
    }

    @Test
    void negativeInputsAreRejectedInDonorProtectedQuantity() {
        InventoryProjection projection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> projection.donorProtectedQuantity(BigDecimal.ONE, -14, 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> projection.donorProtectedQuantity(BigDecimal.ONE, 14, -1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> projection.donorProtectedQuantity(BigDecimal.ONE, 14, 1, -2));
    }

    @Test
    void leadTimePlusTargetCoverageSumIsWidenedNotWrappedBeforeCeiling() {
        InventoryProjection projection = InventoryProjection.calculate(0, 0, 0, 0, 0, 0, 0);

        // A plain int sum of Integer.MAX_VALUE + 10 wraps to a large negative number; widened
        // to long first, the true sum (2147483657) is used, and ceil(1 * 2147483657) + 0 is
        // exactly that value, not a small or negative one.
        assertEquals(2147483657L,
                projection.receiverTargetQuantity(BigDecimal.ONE, Integer.MAX_VALUE, 10, 0));
    }

    @Test
    void canonicalConstructorRejectsAProjectedReceiverBeforeDemandInconsistentWithTheEvidence() {
        // currentAvailable 2 + inboundArrivingBeforeTransfer 50 = 52, not 999: bypassing
        // calculate() must not be able to fabricate a projection whose derived total disagrees
        // with the evidence a consumer like TransferScenarioResult trusts as provenance.
        assertThrows(IllegalArgumentException.class,
                () -> new InventoryProjection(2, 999, 52, 50, 0, 0, 0, 0));
    }

    @Test
    void canonicalConstructorRejectsAProjectedDonorAtDispatchInconsistentWithTheEvidence() {
        assertThrows(IllegalArgumentException.class,
                () -> new InventoryProjection(2, 52, 999, 50, 0, 0, 50, 0));
    }

    @Test
    void canonicalConstructorRejectsNegativeEvidenceFieldsDirectly() {
        assertThrows(IllegalArgumentException.class,
                () -> new InventoryProjection(2, 52, 2, -50, 0, 0, 50, 0));
    }

    @Test
    void canonicalConstructorAcceptsValuesThatMatchTheSection6Formula() {
        // The same shape calculate() would have produced, built directly: must not throw.
        InventoryProjection projection = new InventoryProjection(2, 52, 52, 50, 0, 0, 50, 0);

        assertEquals(52, projection.projectedReceiverBeforeDemand());
        assertEquals(52, projection.projectedDonorAtDispatch());
    }

    @Test
    void canonicalConstructorConsistencyCheckIsOverflowSafe() {
        // The true expected sum (Integer.MAX_VALUE + 10) does not fit in an int; the checked
        // conversion must throw ArithmeticException rather than silently wrap into a value that
        // coincidentally matches the (also out-of-range) projectedReceiverBeforeDemand argument.
        assertThrows(ArithmeticException.class,
                () -> new InventoryProjection(Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 10, 0, 0, 0, 0));
    }
}
