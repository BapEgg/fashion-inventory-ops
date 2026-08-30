package com.bapegg.stockpilot.demand;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates {@link ApprovalRequest}'s own status-shape rules, per
 * {@code knowledge/business-rules.md} section 10 -- independent of any comparison against a
 * {@link RecommendationBasis}, which {@link ApprovalRequestValidationTest} covers.
 */
class ApprovalRequestTest {

    @Test
    void pendingWithNullQuantityIsValid() {
        new ApprovalRequest("RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.PENDING, null, false, null, null);
    }

    @Test
    void pendingWithAQuantityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.PENDING, 5, false, null, null));
    }

    @Test
    void approvedWithAPositiveQuantityIsValid() {
        new ApprovalRequest("RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 19, false, null, null);
    }

    @Test
    void approvedWithNullQuantityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, null, false, null, null));
    }

    @Test
    void approvedWithAZeroOrNegativeQuantityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 0, false, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, -1, false, null, null));
    }

    @Test
    void approvedCanBeFlaggedAsAPolicyExceptionAtTheRequestLevel() {
        // The flag is only acted upon by ApprovalRequestValidation.validate (it needs the basis
        // to know whether the quantity also changed) -- the constructor just carries it through.
        ApprovalRequest request = new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.APPROVED, 19, true, "MANAGER_OVERRIDE", "explanation");

        assertTrue(request.policyException());
    }

    @Test
    void onlyApprovedCanBeFlaggedAsAPolicyException() {
        assertThrows(IllegalArgumentException.class, () -> new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 1, DecisionStatus.PENDING, null, true, null, null));
        for (DecisionStatus status : new DecisionStatus[] {
                DecisionStatus.HELD, DecisionStatus.REJECTED, DecisionStatus.EXPIRED}) {
            assertThrows(IllegalArgumentException.class, () -> new ApprovalRequest(
                    "RUN-1", "SNAP-1", "MVP-2", 1, status, null, true, "CODE", "explanation"));
        }
    }

    @Test
    void heldRejectedAndExpiredRequireNullQuantityAndAReason() {
        for (DecisionStatus status : new DecisionStatus[] {DecisionStatus.HELD, DecisionStatus.REJECTED, DecisionStatus.EXPIRED}) {
            new ApprovalRequest("RUN-1", "SNAP-1", "MVP-2", 1, status, null, false, "CODE", "explanation");

            assertThrows(IllegalArgumentException.class, () -> new ApprovalRequest(
                    "RUN-1", "SNAP-1", "MVP-2", 1, status, 5, false, "CODE", "explanation"));
            assertThrows(IllegalArgumentException.class, () -> new ApprovalRequest(
                    "RUN-1", "SNAP-1", "MVP-2", 1, status, null, false, null, "explanation"));
            assertThrows(IllegalArgumentException.class, () -> new ApprovalRequest(
                    "RUN-1", "SNAP-1", "MVP-2", 1, status, null, false, "CODE", null));
            assertThrows(IllegalArgumentException.class, () -> new ApprovalRequest(
                    "RUN-1", "SNAP-1", "MVP-2", 1, status, null, false, " ", " "));
        }
    }

    @Test
    void blankIdentifiersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ApprovalRequest(
                " ", "SNAP-1", "MVP-2", 1, DecisionStatus.PENDING, null, false, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ApprovalRequest(
                "RUN-1", " ", "MVP-2", 1, DecisionStatus.PENDING, null, false, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ApprovalRequest(
                "RUN-1", "SNAP-1", " ", 1, DecisionStatus.PENDING, null, false, null, null));
    }

    @Test
    void nonPositiveCandidateVersionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ApprovalRequest(
                "RUN-1", "SNAP-1", "MVP-2", 0, DecisionStatus.PENDING, null, false, null, null));
    }
}
