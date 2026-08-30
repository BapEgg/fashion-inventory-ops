package com.bapegg.stockpilot.approval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManualQuantityTestCommandTest {

    @Test
    void validShapeIsAccepted() {
        ManualQuantityTestCommand command = new ManualQuantityTestCommand(1L, 2L, "SNAP-1", "MVP-2", 1, 20);
        assertEquals(20, command.requestedQuantity());
    }

    @Test
    void nullOrNonPositiveRecommendationIdIsRejected() {
        assertThrows(ApprovalTransactionException.class, () -> new ManualQuantityTestCommand(null, 2L, "SNAP-1", "MVP-2", 1, 20));
        assertThrows(ApprovalTransactionException.class, () -> new ManualQuantityTestCommand(0L, 2L, "SNAP-1", "MVP-2", 1, 20));
        assertThrows(ApprovalTransactionException.class, () -> new ManualQuantityTestCommand(-1L, 2L, "SNAP-1", "MVP-2", 1, 20));
    }

    @Test
    void nullOrNonPositiveAnalysisRunIdIsRejected() {
        assertThrows(ApprovalTransactionException.class, () -> new ManualQuantityTestCommand(1L, null, "SNAP-1", "MVP-2", 1, 20));
        assertThrows(ApprovalTransactionException.class, () -> new ManualQuantityTestCommand(1L, 0L, "SNAP-1", "MVP-2", 1, 20));
        assertThrows(ApprovalTransactionException.class, () -> new ManualQuantityTestCommand(1L, -1L, "SNAP-1", "MVP-2", 1, 20));
    }

    @Test
    void nonPositiveCandidateVersionIsRejected() {
        assertThrows(ApprovalTransactionException.class, () -> new ManualQuantityTestCommand(1L, 2L, "SNAP-1", "MVP-2", 0, 20));
        assertThrows(ApprovalTransactionException.class, () -> new ManualQuantityTestCommand(1L, 2L, "SNAP-1", "MVP-2", -1, 20));
    }

    @Test
    void nonPositiveRequestedQuantityIsRejected() {
        assertThrows(ApprovalTransactionException.class, () -> new ManualQuantityTestCommand(1L, 2L, "SNAP-1", "MVP-2", 1, 0));
        assertThrows(ApprovalTransactionException.class, () -> new ManualQuantityTestCommand(1L, 2L, "SNAP-1", "MVP-2", 1, -5));
    }

    @Test
    void blankOrOversizedVersionStringsAreRejected() {
        assertThrows(ApprovalTransactionException.class, () -> new ManualQuantityTestCommand(1L, 2L, " ", "MVP-2", 1, 20));
        assertThrows(ApprovalTransactionException.class, () -> new ManualQuantityTestCommand(1L, 2L, "SNAP-1", " ", 1, 20));
        assertThrows(ApprovalTransactionException.class,
                () -> new ManualQuantityTestCommand(1L, 2L, "x".repeat(65), "MVP-2", 1, 20));
        assertThrows(ApprovalTransactionException.class,
                () -> new ManualQuantityTestCommand(1L, 2L, "SNAP-1", "x".repeat(33), 1, 20));
    }

    @Test
    void invalidShapeUsesTheInvalidRequestCode() {
        ApprovalTransactionException exception = assertThrows(ApprovalTransactionException.class,
                () -> new ManualQuantityTestCommand(1L, 2L, "SNAP-1", "MVP-2", 1, 0));
        assertEquals(ApprovalErrorCode.INVALID_REQUEST, exception.code());
    }

    @Test
    void normalizationStripsSurroundingWhitespaceAndAppliesNfc() {
        String composed = "é"; // e with acute, single code point
        String decomposed = "é"; // e + combining acute accent
        ManualQuantityTestCommand command = new ManualQuantityTestCommand(
                1L, 2L, "  SNAP-1  ", "MVP-2" + decomposed, 1, 20);
        assertEquals("SNAP-1", command.inputSnapshotVersion());
        assertEquals("MVP-2" + composed, command.ruleVersion());
    }
}
