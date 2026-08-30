package com.bapegg.stockpilot.approval;

import com.bapegg.stockpilot.rebalance.DecisionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApprovalTransactionCommandTest {

    @Test
    void approvedShapeIsValid() {
        ApprovalTransactionCommand command = new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 20, false, null, null, "actor");
        assertEquals(20, command.selectedQuantity());
    }

    @Test
    void heldAndRejectedShapesAreValid() {
        new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.HELD, null, false, "CODE", "why", "actor");
        new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.REJECTED, null, false, "CODE", "why", "actor");
    }

    @Test
    void pendingAndExpiredStatusesAreRejected() {
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.PENDING, null, false, null, null, "actor"));
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.EXPIRED, null, false, null, null, "actor"));
    }

    @Test
    void nonPositiveRecommendationIdAnalysisRunIdOrCandidateVersionAreRejected() {
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                0L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 5, false, null, null, "actor"));
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, -1L, "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 5, false, null, null, "actor"));
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 0, DecisionStatus.APPROVED, 5, false, null, null, "actor"));
    }

    @Test
    void blankOrOversizedVersionStringsAreRejected() {
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, " ", "MVP-2", 1, DecisionStatus.APPROVED, 5, false, null, null, "actor"));
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", " ", 1, DecisionStatus.APPROVED, 5, false, null, null, "actor"));
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "x".repeat(65), "MVP-2", 1, DecisionStatus.APPROVED, 5, false, null, null, "actor"));
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "x".repeat(33), 1, DecisionStatus.APPROVED, 5, false, null, null, "actor"));
    }

    @Test
    void reasonCodeAndReasonLengthLimitsAreEnforced() {
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.HELD, null, false, "x".repeat(41), "why", "actor"));
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.HELD, null, false, "CODE", "x".repeat(1001), "actor"));
    }

    @Test
    void actorLabelMustBeNonBlankAndAtMostOneHundredCharacters() {
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 5, false, null, null, " "));
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 5, false, null, null, "x".repeat(101)));
    }

    @Test
    void approvedRequiresAPositiveSelectedQuantity() {
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, null, false, null, null, "actor"));
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 0, false, null, null, "actor"));
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, -1, false, null, null, "actor"));
    }

    @Test
    void heldAndRejectedRequireNullQuantityAndNonBlankReasonCodeAndReason() {
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.HELD, 5, false, "CODE", "why", "actor"));
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.HELD, null, false, null, "why", "actor"));
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.REJECTED, null, false, "CODE", null, "actor"));
    }

    @Test
    void policyExceptionIsOnlyLegalWhenApproved() {
        assertThrows(ApprovalTransactionException.class, () -> new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.HELD, null, true, "CODE", "why", "actor"));
        new ApprovalTransactionCommand(
                1L, 2L, "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 5, true, "CODE", "why", "actor");
    }

    @Test
    void normalizationStripsSurroundingWhitespaceFromStringFields() {
        ApprovalTransactionCommand command = new ApprovalTransactionCommand(
                1L, 2L, "  SNAP-1  ", "MVP-2", 1, DecisionStatus.APPROVED, 5, false, null, null, "  actor  ");
        assertEquals("SNAP-1", command.inputSnapshotVersion());
        assertEquals("actor", command.actorLabel());
    }
}
