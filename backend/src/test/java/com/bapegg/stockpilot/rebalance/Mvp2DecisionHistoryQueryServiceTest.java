package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.api.error.ApiException;
import com.bapegg.stockpilot.approval.ApprovalErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link Mvp2DecisionHistoryQueryService}'s PENDING/not-found shortcuts and
 * the section 4.5 corruption boundary, per current-task.md section 7.1. No Spring context, no
 * Oracle -- every repository is mocked. The DB-computed bulk basis/draft mapping for a real
 * MVP-2 {@code APPROVED} history entry is covered by the Oracle IT instead, since it depends on
 * generated ids this suite cannot fabricate without persisting.
 */
class Mvp2DecisionHistoryQueryServiceTest {

    private final SpRebalanceRecommendationRepository recommendationRepository = mock(SpRebalanceRecommendationRepository.class);
    private final SpRebalanceDecisionRepository decisionRepository = mock(SpRebalanceDecisionRepository.class);
    private final SpApprovalBasisRepository approvalBasisRepository = mock(SpApprovalBasisRepository.class);
    private final SpTransferDraftRepository transferDraftRepository = mock(SpTransferDraftRepository.class);
    private final Mvp2DecisionHistoryQueryService service = new Mvp2DecisionHistoryQueryService(
            recommendationRepository, decisionRepository, approvalBasisRepository, transferDraftRepository);

    @Test
    void unknownRecommendationIsRejectedAsNotFound() {
        when(recommendationRepository.existsById(1L)).thenReturn(false);

        ApiException e = assertThrows(ApiException.class, () -> service.getHistory(1L));

        assertEquals(ApprovalErrorCode.RECOMMENDATION_NOT_FOUND, e.code());
    }

    @Test
    void noDecisionRowsReturnPendingWithAnEmptyArray() {
        when(recommendationRepository.existsById(1L)).thenReturn(true);
        when(decisionRepository.findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(1L))
                .thenReturn(List.of());

        Mvp2DecisionHistoryResponse response = service.getHistory(1L);

        assertEquals(1L, response.recommendationId());
        assertEquals(DecisionStatus.PENDING, response.currentStatus());
        assertEquals(List.of(), response.decisions());
    }

    @Test
    void anMvp1DecisionMapsWithNullBasisAndDraft() {
        SpRebalanceDecision legacyDecision =
                new SpRebalanceDecision(null, DecisionStatus.APPROVED, 5, "legacy reason", "legacy actor");
        when(recommendationRepository.existsById(1L)).thenReturn(true);
        when(decisionRepository.findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(1L))
                .thenReturn(List.of(legacyDecision));
        when(approvalBasisRepository.findWithAnalysisRunByDecisionIdIn(any())).thenReturn(List.of());
        when(transferDraftRepository.findByDecision_DecisionIdIn(any())).thenReturn(List.of());

        Mvp2DecisionHistoryResponse response = service.getHistory(1L);

        assertEquals(DecisionStatus.APPROVED, response.currentStatus());
        assertEquals(1, response.decisions().size());
        Mvp2DecisionHistoryResponse.DecisionItem item = response.decisions().get(0);
        assertEquals("MVP-1", item.decisionContractVersion());
        assertEquals(5, item.selectedQuantity());
        assertNull(item.approvalBasis());
        assertNull(item.transferDraft());
    }

    @Test
    void aPhysicalPendingRowIsRejectedAsInternalServerError() {
        SpRebalanceDecision corruptPending = SpRebalanceDecision.createMvp2Decision(
                null, 1, DecisionStatus.PENDING, null, null, null, "actor", 1, "REQ-1", false);
        when(recommendationRepository.existsById(1L)).thenReturn(true);
        when(decisionRepository.findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(1L))
                .thenReturn(List.of(corruptPending));
        when(approvalBasisRepository.findWithAnalysisRunByDecisionIdIn(any())).thenReturn(List.of());
        when(transferDraftRepository.findByDecision_DecisionIdIn(any())).thenReturn(List.of());

        ApiException e = assertThrows(ApiException.class, () -> service.getHistory(1L));

        assertEquals(ApprovalErrorCode.INTERNAL_SERVER_ERROR, e.code());
    }

    @Test
    void anMvp2HeldDecisionWithABasisRowIsRejectedAsInternalServerError() {
        SpRebalanceDecision held = SpRebalanceDecision.createMvp2Decision(
                null, 1, DecisionStatus.HELD, null, "NEEDS_REVIEW", "awaiting sign-off", "actor", 1, "REQ-1", false);
        SpApprovalBasis unexpectedBasis = mock(SpApprovalBasis.class);
        when(unexpectedBasis.getDecision()).thenReturn(held);
        when(recommendationRepository.existsById(1L)).thenReturn(true);
        when(decisionRepository.findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(1L))
                .thenReturn(List.of(held));
        when(approvalBasisRepository.findWithAnalysisRunByDecisionIdIn(any()))
                .thenReturn(List.of(unexpectedBasis));
        when(transferDraftRepository.findByDecision_DecisionIdIn(any())).thenReturn(List.of());

        ApiException e = assertThrows(ApiException.class, () -> service.getHistory(1L));

        assertEquals(ApprovalErrorCode.INTERNAL_SERVER_ERROR, e.code());
    }

    @Test
    void anMvp2ApprovedDecisionMissingItsBasisIsRejectedAsInternalServerError() {
        SpRebalanceDecision approved = SpRebalanceDecision.createMvp2Decision(
                null, 1, DecisionStatus.APPROVED, 8, null, null, "actor", 1, "REQ-1", false);
        when(recommendationRepository.existsById(1L)).thenReturn(true);
        when(decisionRepository.findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(1L))
                .thenReturn(List.of(approved));
        when(approvalBasisRepository.findWithAnalysisRunByDecisionIdIn(any())).thenReturn(List.of());
        when(transferDraftRepository.findByDecision_DecisionIdIn(any())).thenReturn(List.of());

        ApiException e = assertThrows(ApiException.class, () -> service.getHistory(1L));

        assertEquals(ApprovalErrorCode.INTERNAL_SERVER_ERROR, e.code());
    }
}
