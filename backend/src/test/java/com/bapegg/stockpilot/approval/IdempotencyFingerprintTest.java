package com.bapegg.stockpilot.approval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IdempotencyFingerprintTest {

    @Test
    void normalizeStripsSurroundingWhitespaceButPreservesInternalWhitespaceAndCase() {
        assertEquals("MVP-2 Run", IdempotencyFingerprint.normalize("  MVP-2 Run  "));
    }

    @Test
    void normalizeCollapsesToTheSameNfcFormRegardlessOfComposedOrDecomposedInput() {
        String composed = "é"; // é as a single code point
        String decomposed = "é"; // e + combining acute accent
        assertEquals(IdempotencyFingerprint.normalize(composed), IdempotencyFingerprint.normalize(decomposed));
    }

    @Test
    void normalizeTreatsNullAndBlankAsAbsent() {
        assertNull(IdempotencyFingerprint.normalize(null));
        assertNull(IdempotencyFingerprint.normalize(""));
        assertNull(IdempotencyFingerprint.normalize("   "));
    }

    @Test
    void computeIsDeterministicForTheSameInputs() {
        String first = compute(1L, 2L, "SNAP-1", "MVP-2", 1, "APPROVED", 5, false, null, null, "actor");
        String second = compute(1L, 2L, "SNAP-1", "MVP-2", 1, "APPROVED", 5, false, null, null, "actor");
        assertEquals(first, second);
    }

    @Test
    void computeChangesWhenAnyFieldChanges() {
        String base = compute(1L, 2L, "SNAP-1", "MVP-2", 1, "APPROVED", 5, false, null, null, "actor");

        assertNotEquals(base, compute(9L, 2L, "SNAP-1", "MVP-2", 1, "APPROVED", 5, false, null, null, "actor"));
        assertNotEquals(base, compute(1L, 9L, "SNAP-1", "MVP-2", 1, "APPROVED", 5, false, null, null, "actor"));
        assertNotEquals(base, compute(1L, 2L, "SNAP-9", "MVP-2", 1, "APPROVED", 5, false, null, null, "actor"));
        assertNotEquals(base, compute(1L, 2L, "SNAP-1", "MVP-3", 1, "APPROVED", 5, false, null, null, "actor"));
        assertNotEquals(base, compute(1L, 2L, "SNAP-1", "MVP-2", 9, "APPROVED", 5, false, null, null, "actor"));
        assertNotEquals(base, compute(1L, 2L, "SNAP-1", "MVP-2", 1, "REJECTED", 5, false, null, null, "actor"));
        assertNotEquals(base, compute(1L, 2L, "SNAP-1", "MVP-2", 1, "APPROVED", 9, false, null, null, "actor"));
        assertNotEquals(base, compute(1L, 2L, "SNAP-1", "MVP-2", 1, "APPROVED", 5, true, null, null, "actor"));
        assertNotEquals(base, compute(1L, 2L, "SNAP-1", "MVP-2", 1, "APPROVED", 5, false, "CODE", null, "actor"));
        assertNotEquals(base, compute(1L, 2L, "SNAP-1", "MVP-2", 1, "APPROVED", 5, false, null, "why", "actor"));
        assertNotEquals(base, compute(1L, 2L, "SNAP-1", "MVP-2", 1, "APPROVED", 5, false, null, null, "other-actor"));
    }

    @Test
    void computeDistinguishesNullSelectedQuantityFromAnyRealValue() {
        String withNull = compute(1L, 2L, "SNAP-1", "MVP-2", 1, "HELD", null, false, "CODE", "why", "actor");
        String withZeroish = compute(1L, 2L, "SNAP-1", "MVP-2", 1, "HELD", 0, false, "CODE", "why", "actor");
        assertNotEquals(withNull, withZeroish);
    }

    private static String compute(
            Long recommendationId, Long analysisRunId, String inputSnapshotVersion, String ruleVersion,
            int candidateVersion, String status, Integer selectedQuantity, boolean policyException,
            String reasonCode, String reason, String actorLabel) {
        return IdempotencyFingerprint.compute(recommendationId, analysisRunId, inputSnapshotVersion, ruleVersion,
                candidateVersion, status, selectedQuantity, policyException, reasonCode, reason, actorLabel);
    }
}
