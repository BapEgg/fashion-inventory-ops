package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.api.error.ApiException;
import com.bapegg.stockpilot.approval.ApprovalErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The read-only side of {@code GET /api/rebalancing-decisions/{recommendationId}}, per
 * current-task.md section 4. Never locks anything -- {@link com.bapegg.stockpilot.approval.ApprovalTransactionExecutor}
 * and {@link com.bapegg.stockpilot.approval.ManualQuantityTestExecutor} own the recommendation/
 * donor-snapshot lock order, not this query.
 * <p>
 * Exactly four bulk statements at most, regardless of history length: recommendation existence,
 * the ordered decision list, one bulk basis lookup and one bulk draft lookup (the latter two
 * skipped entirely when there is no decision yet, so a {@code PENDING} recommendation costs only
 * two). No per-decision N+1 query is ever issued.
 */
@Service
public class Mvp2DecisionHistoryQueryService {

    private final SpRebalanceRecommendationRepository recommendationRepository;
    private final SpRebalanceDecisionRepository decisionRepository;
    private final SpApprovalBasisRepository approvalBasisRepository;
    private final SpTransferDraftRepository transferDraftRepository;

    public Mvp2DecisionHistoryQueryService(
            SpRebalanceRecommendationRepository recommendationRepository,
            SpRebalanceDecisionRepository decisionRepository,
            SpApprovalBasisRepository approvalBasisRepository,
            SpTransferDraftRepository transferDraftRepository) {
        this.recommendationRepository = recommendationRepository;
        this.decisionRepository = decisionRepository;
        this.approvalBasisRepository = approvalBasisRepository;
        this.transferDraftRepository = transferDraftRepository;
    }

    @Transactional(readOnly = true)
    public Mvp2DecisionHistoryResponse getHistory(Long recommendationId) {
        if (!recommendationRepository.existsById(recommendationId)) {
            throw new ApiException(ApprovalErrorCode.RECOMMENDATION_NOT_FOUND,
                    "No recommendation found for id " + recommendationId + ".");
        }

        List<SpRebalanceDecision> decisions = decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(recommendationId);
        if (decisions.isEmpty()) {
            return new Mvp2DecisionHistoryResponse(recommendationId, DecisionStatus.PENDING, List.of());
        }

        List<Long> decisionIds = decisions.stream().map(SpRebalanceDecision::getDecisionId).toList();
        Map<Long, SpApprovalBasis> basisByDecisionId = new HashMap<>();
        for (SpApprovalBasis basis : approvalBasisRepository.findWithAnalysisRunByDecisionIdIn(decisionIds)) {
            basisByDecisionId.put(basis.getDecision().getDecisionId(), basis);
        }
        Map<Long, SpTransferDraft> draftByDecisionId = new HashMap<>();
        for (SpTransferDraft draft : transferDraftRepository.findByDecision_DecisionIdIn(decisionIds)) {
            draftByDecisionId.put(draft.getDecision().getDecisionId(), draft);
        }

        List<Mvp2DecisionHistoryResponse.DecisionItem> items = decisions.stream()
                .map(decision -> toItem(decision, basisByDecisionId.get(decision.getDecisionId()),
                        draftByDecisionId.get(decision.getDecisionId())))
                .toList();

        DecisionStatus currentStatus = decisions.get(decisions.size() - 1).getDecisionStatus();
        return new Mvp2DecisionHistoryResponse(recommendationId, currentStatus, items);
    }

    /**
     * Enforces the section 4.5 corruption boundary before mapping: a physical {@code PENDING} row
     * can never exist, an MVP-2 {@code APPROVED} row must have both basis and draft, a non-approved
     * MVP-2 row must have neither, and an unrecognized {@code decisionContractVersion} is never
     * interpreted as an arbitrary shape. Any violation is {@code INTERNAL_SERVER_ERROR}, not a
     * partial response.
     */
    private Mvp2DecisionHistoryResponse.DecisionItem toItem(
            SpRebalanceDecision decision, SpApprovalBasis basis, SpTransferDraft draft) {
        if (decision.getDecisionStatus() == DecisionStatus.PENDING) {
            throw internalError("A physical PENDING decision row must never exist (decisionId="
                    + decision.getDecisionId() + ").");
        }

        String contractVersion = decision.getDecisionContractVersion();
        Mvp2DecisionHistoryResponse.ApprovalBasisItem basisItem = null;
        Mvp2DecisionHistoryResponse.TransferDraftItem draftItem = null;

        if ("MVP-2".equals(contractVersion)) {
            if (decision.getDecisionStatus() == DecisionStatus.APPROVED) {
                if (basis == null || draft == null) {
                    throw internalError("APPROVED MVP-2 decision " + decision.getDecisionId()
                            + " is missing its approval basis or transfer draft.");
                }
                basisItem = toBasisItem(basis);
                draftItem = toDraftItem(draft);
            } else if (basis != null || draft != null) {
                throw internalError("Non-approved MVP-2 decision " + decision.getDecisionId()
                        + " unexpectedly has an approval basis or transfer draft.");
            }
        } else if (!"MVP-1".equals(contractVersion)) {
            throw internalError("Unknown decisionContractVersion '" + contractVersion + "' for decision "
                    + decision.getDecisionId() + ".");
        }

        return new Mvp2DecisionHistoryResponse.DecisionItem(
                decision.getDecisionId(), decision.getDecisionSequence(), decision.getDecisionStatus(),
                decision.getSelectedQuantity(), decision.isPolicyException(), decision.getReasonCode(),
                decision.getReason(), decision.getActorLabel(), decision.getRecommendationVersion(),
                decision.getDecisionContractVersion(), decision.getDecidedAt(), basisItem, draftItem);
    }

    private static Mvp2DecisionHistoryResponse.ApprovalBasisItem toBasisItem(SpApprovalBasis basis) {
        return new Mvp2DecisionHistoryResponse.ApprovalBasisItem(
                basis.getApprovalBasisId(), basis.getAnalysisRun().getAnalysisRunId(),
                basis.getInputSnapshotVersion(), basis.getRuleVersion(), basis.getCandidateVersion(),
                basis.isCandidateEligible(), basis.getRecommendedBaseQuantity(), basis.getDonorTransferableQuantity(),
                basis.getRouteMinimumQuantity(), basis.getPackageMultiple(), basis.getRouteMaximumQuantity(),
                basis.getReceiverCapacityRemaining(), basis.getReceiverProjectedBeforeDemand(),
                basis.getDonorProjectedAtDispatch(), basis.getAlreadyApprovedDraftQuantity(),
                basis.getBasisContractVersion(), basis.getCreatedAt());
    }

    private static Mvp2DecisionHistoryResponse.TransferDraftItem toDraftItem(SpTransferDraft draft) {
        return new Mvp2DecisionHistoryResponse.TransferDraftItem(
                draft.getTransferDraftId(), draft.getDonorStoreId(), draft.getReceiverStoreId(), draft.getSkuId(),
                draft.getQuantity(), draft.getDraftStatus(), draft.getExternalReference(), draft.getPayloadVersion(),
                draft.getCreatedAt(), draft.getUpdatedAt());
    }

    private static ApiException internalError(String message) {
        return new ApiException(ApprovalErrorCode.INTERNAL_SERVER_ERROR, message);
    }
}
