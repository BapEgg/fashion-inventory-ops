package com.bapegg.stockpilot.rebalance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test for {@link SpRebalanceRecommendation#createMvp2Candidate}'s route invariant,
 * per the Codex foundation-layer review: an {@code ELIGIBLE} candidate with a {@code null}
 * {@code routeId} can never be approved ({@code approval.CurrentApprovalBasisLoader} always
 * resolves the active route from this id), so it must be rejected at construction time rather
 * than persisted as a normal-looking but unreachable candidate.
 */
class SpRebalanceRecommendationTest {

    @Test
    void eligibleCandidateWithNullRouteIsRejected() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                SpRebalanceRecommendation.createMvp2Candidate(
                        null, null, null, CandidateStatus.ELIGIBLE, 1, RecommendationMode.RECOMMENDED,
                        20, 30, 20, 5L, 40L, 95L));
        assertTrue(exception.getMessage().contains("routeId"));
    }

    @Test
    void eligibleCandidateWithANonNullRouteIsAllowed() {
        assertDoesNotThrow(() -> SpRebalanceRecommendation.createMvp2Candidate(
                null, null, 42L, CandidateStatus.ELIGIBLE, 1, RecommendationMode.RECOMMENDED,
                20, 30, 20, 5L, 40L, 95L));
    }

    @Test
    void rejectedCandidateWithNullRouteIsStillAllowed() {
        assertDoesNotThrow(() -> SpRebalanceRecommendation.createMvp2Candidate(
                null, null, null, CandidateStatus.REJECTED, 1, RecommendationMode.NONE,
                null, null, null, null, null, null));
    }
}
