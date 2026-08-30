package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces section 10's stale-version, recalculated-limit and required-reason rules, using
 * the same GS-02 scenario numbers as {@code TransferScenarioSetTest}
 * (recommended BASE quantity 19, donor transferable 61, route maximum 50, receiver capacity
 * remaining 94) for a realistic basis.
 */
class ApprovalRequestValidationTest {

    private static final RecommendationBasis BASIS = new RecommendationBasis(
            "RUN-1", "SNAP-1", "MVP-2", 1, true,
            19, 61, 1, 1, 50, 94);

    @Test
    void matchingVersionsAndTheExactRecommendedQuantityIsValidWithNoReason() {
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 19, false, null, null);

        ApprovalRequestValidation result = ApprovalRequestValidation.validate(request, BASIS);

        assertEquals(ApprovalOutcome.VALID, result.outcome());
        assertFalse(result.stale());
    }

    @Test
    void mismatchedAnalysisRunIdIsStale() {
        ApprovalRequest request = new ApprovalRequest(
                "RUN-2", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 19, false, null, null);

        assertTrue(ApprovalRequestValidation.validate(request, BASIS).stale());
    }

    @Test
    void mismatchedInputSnapshotVersionIsStale() {
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-2", "MVP-2", 1, DecisionStatus.APPROVED, 19, false, null, null);

        assertTrue(ApprovalRequestValidation.validate(request, BASIS).stale());
    }

    @Test
    void mismatchedRuleVersionIsStale() {
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-3", 1, DecisionStatus.APPROVED, 19, false, null, null);

        assertTrue(ApprovalRequestValidation.validate(request, BASIS).stale());
    }

    @Test
    void mismatchedCandidateVersionIsStale() {
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 2, DecisionStatus.APPROVED, 19, false, null, null);

        assertTrue(ApprovalRequestValidation.validate(request, BASIS).stale());
    }

    @Test
    void changingTheApprovedQuantityWithoutAReasonIsRejected() {
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 20, false, null, null);

        assertThrows(IllegalArgumentException.class, () -> ApprovalRequestValidation.validate(request, BASIS));
    }

    @Test
    void changingTheApprovedQuantityWithAReasonIsValid() {
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 20, false,
                "MANAGER_OVERRIDE", "Proactive stocking ahead of a known local event.");

        ApprovalRequestValidation result = ApprovalRequestValidation.validate(request, BASIS);

        assertEquals(ApprovalOutcome.VALID, result.outcome());
    }

    @Test
    void approvedQuantityExceedingDonorTransferableIsStaleEvenWithAReason() {
        // 62 > donorTransferableQuantity 61: a real limit breach, not merely "changed" -- a
        // reason must not paper over an actually-impossible quantity.
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 62, false, "CODE", "explanation");

        assertTrue(ApprovalRequestValidation.validate(request, BASIS).stale());
    }

    @Test
    void approvedQuantityExceedingRouteMaximumIsStale() {
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 51, false, "CODE", "explanation");

        assertTrue(ApprovalRequestValidation.validate(request, BASIS).stale());
    }

    @Test
    void approvedQuantityExceedingReceiverCapacityRemainingIsStale() {
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 95, false, "CODE", "explanation");

        assertTrue(ApprovalRequestValidation.validate(request, BASIS).stale());
    }

    @Test
    void approvedQuantityBelowRouteMinimumIsStale() {
        RecommendationBasis basisWithHigherMinimum = new RecommendationBasis(
                "RUN-1", "SNAP-1", "MVP-2", 1, true, 19, 61, 10, 1, 50, 94);
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 5, false, "CODE", "explanation");

        assertTrue(ApprovalRequestValidation.validate(request, basisWithHigherMinimum).stale());
    }

    @Test
    void approvedQuantityNotAMultipleOfThePackageIsStale() {
        RecommendationBasis basisWithPackageMultiple = new RecommendationBasis(
                "RUN-1", "SNAP-1", "MVP-2", 1, true, 20, 61, 5, 5, 50, 94);
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 22, false, "CODE", "explanation");

        assertTrue(ApprovalRequestValidation.validate(request, basisWithPackageMultiple).stale());
    }

    @Test
    void heldDecisionIsValidWhenVersionsMatch() {
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.HELD, null, false, "NEEDS_REVIEW", "Awaiting manager sign-off.");

        assertEquals(ApprovalOutcome.VALID, ApprovalRequestValidation.validate(request, BASIS).outcome());
    }

    @Test
    void rejectedDecisionIsStaleWhenTheRuleVersionChanged() {
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-3", 1, DecisionStatus.REJECTED, null, false, "NOT_NEEDED", "Store no longer carries this SKU.");

        assertTrue(ApprovalRequestValidation.validate(request, BASIS).stale());
    }

    @Test
    void pendingDecisionIsValidWhenVersionsMatch() {
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.PENDING, null, false, null, null);

        assertEquals(ApprovalOutcome.VALID, ApprovalRequestValidation.validate(request, BASIS).outcome());
    }

    @Test
    void recommendationBasisRejectsNegativeOrNonPositiveInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new RecommendationBasis("RUN-1", "SNAP-1", "MVP-2", 1, true, -1, 61, 1, 1, 50, 94));
        assertThrows(IllegalArgumentException.class,
                () -> new RecommendationBasis("RUN-1", "SNAP-1", "MVP-2", 1, true, 19, -1, 1, 1, 50, 94));
        assertThrows(IllegalArgumentException.class,
                () -> new RecommendationBasis("RUN-1", "SNAP-1", "MVP-2", 1, true, 19, 61, 0, 1, 50, 94));
        assertThrows(IllegalArgumentException.class,
                () -> new RecommendationBasis("RUN-1", "SNAP-1", "MVP-2", 1, true, 19, 61, 1, 0, 50, 94));
        assertThrows(IllegalArgumentException.class,
                () -> new RecommendationBasis("RUN-1", "SNAP-1", "MVP-2", 1, true, 19, 61, 10, 1, 5, 94));
    }

    // --- Section 10 correctness-finding regression tests ---

    @Test
    void ineligibleCandidateIsStaleEvenWhenTheQuantityFitsEveryNumericLimit() {
        // Same numeric limits as BASIS (19 sits comfortably inside all of them), but the caller's
        // freshly recomputed TransferCandidateEvaluation.eligible() is now false -- e.g. an
        // OWNER_MISMATCH, an inactive route, or a PENDING_TRANSFER_CONFLICT discovered since the
        // recommendation was produced. No numeric limit alone can detect this.
        RecommendationBasis basisWithIneligibleCandidate = new RecommendationBasis(
                "RUN-1", "SNAP-1", "MVP-2", 1, false, 19, 61, 1, 1, 50, 94);
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 19, false, null, null);

        assertTrue(ApprovalRequestValidation.validate(request, basisWithIneligibleCandidate).stale());
    }

    @Test
    void policyExceptionAtTheExactRecommendedQuantityRequiresAReason() {
        ApprovalRequest requestWithoutReason = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 19, true, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> ApprovalRequestValidation.validate(requestWithoutReason, BASIS));

        ApprovalRequest requestWithReason = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 19, true,
                "MANAGER_OVERRIDE", "Approved as a deliberate policy exception.");

        assertEquals(ApprovalOutcome.VALID, ApprovalRequestValidation.validate(requestWithReason, BASIS).outcome());
    }

    @Test
    void policyExceptionWithAReasonCannotOverrideAHardLimitViolation() {
        // 62 > donorTransferableQuantity 61: policyException=true plus a reason still cannot make
        // an actually-impossible quantity approvable.
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 62, true,
                "MANAGER_OVERRIDE", "Approved as a deliberate policy exception.");

        assertTrue(ApprovalRequestValidation.validate(request, BASIS).stale());
    }

    @Test
    void directConstructorRejectsANullOutcome() {
        assertThrows(IllegalArgumentException.class, () -> new ApprovalRequestValidation(null));
    }
}
