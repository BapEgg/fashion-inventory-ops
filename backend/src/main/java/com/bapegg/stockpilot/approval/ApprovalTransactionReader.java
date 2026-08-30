package com.bapegg.stockpilot.approval;

import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.rebalance.DecisionStatus;
import com.bapegg.stockpilot.rebalance.SpApprovalBasis;
import com.bapegg.stockpilot.rebalance.SpApprovalBasisRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceDecision;
import com.bapegg.stockpilot.rebalance.SpRebalanceDecisionRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation;
import com.bapegg.stockpilot.rebalance.SpTransferDraft;
import com.bapegg.stockpilot.rebalance.SpTransferDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The read-only half of the approval transaction's idempotency check, per
 * {@code knowledge/business-rules.md} section 10: looking up an existing
 * {@code decision_request_id} and reconstructing its fingerprint runs in its own
 * {@code @Transactional(readOnly = true)} boundary, separate from
 * {@link ApprovalTransactionExecutor}'s write transaction -- both because the facade
 * must decide replay-vs-conflict-vs-proceed before ever acquiring a lock, and because a
 * plain (non-transactional) repository call cannot safely navigate this decision's lazy
 * associations once the call returns.
 */
@Service
public class ApprovalTransactionReader {

    private final SpRebalanceDecisionRepository decisionRepository;
    private final SpApprovalBasisRepository approvalBasisRepository;
    private final SpTransferDraftRepository transferDraftRepository;

    public ApprovalTransactionReader(
            SpRebalanceDecisionRepository decisionRepository,
            SpApprovalBasisRepository approvalBasisRepository,
            SpTransferDraftRepository transferDraftRepository) {
        this.decisionRepository = decisionRepository;
        this.approvalBasisRepository = approvalBasisRepository;
        this.transferDraftRepository = transferDraftRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ExistingKeyLookup> findExisting(String decisionRequestId) {
        return decisionRepository.findByDecisionRequestId(decisionRequestId).map(this::toLookup);
    }

    /**
     * Exposed (not just used internally by {@link #findExisting}) so
     * {@link ApprovalTransactionExecutor} can reuse the exact same reconstruction logic
     * when it re-checks the key after acquiring the recommendation lock -- at that point
     * it is already inside its own active transaction, so calling this directly (rather
     * than through {@link #findExisting}'s separate {@code @Transactional} boundary) is
     * safe and avoids a second, redundant transaction.
     */
    public ExistingKeyLookup toLookup(SpRebalanceDecision decision) {
        String fingerprint = reconstructFingerprint(decision);
        Long draftId = decision.getDecisionStatus() == DecisionStatus.APPROVED
                ? transferDraftRepository.findByDecision_DecisionId(decision.getDecisionId())
                        .map(SpTransferDraft::getTransferDraftId)
                        .orElse(null)
                : null;
        ApprovalTransactionResult replayResult = new ApprovalTransactionResult(
                decision.getDecisionId(), decision.getDecisionStatus(), decision.getDecisionSequence(), draftId, false);
        return new ExistingKeyLookup(fingerprint, replayResult);
    }

    /**
     * Rebuilds the normalized command a prior request must have submitted to produce
     * {@code decision}, per section 10: {@code APPROVED} reconstructs from the decision
     * plus its {@link SpApprovalBasis} (the approval-time version/limits actually used);
     * every other status reconstructs from the decision plus its recommendation and that
     * recommendation's analysis run.
     */
    private String reconstructFingerprint(SpRebalanceDecision decision) {
        if (decision.getDecisionStatus() == DecisionStatus.APPROVED) {
            SpApprovalBasis basis = approvalBasisRepository.findByDecision_DecisionId(decision.getDecisionId())
                    .orElseThrow(() -> new ApprovalTransactionException(ApprovalErrorCode.INTERNAL_SERVER_ERROR,
                            "APPROVED decision " + decision.getDecisionId() + " has no approval basis."));
            return IdempotencyFingerprint.compute(
                    decision.getRecommendation().getRecommendationId(),
                    basis.getAnalysisRun().getAnalysisRunId(),
                    basis.getInputSnapshotVersion(),
                    basis.getRuleVersion(),
                    basis.getCandidateVersion(),
                    decision.getDecisionStatus().name(),
                    decision.getSelectedQuantity(),
                    decision.isPolicyException(),
                    decision.getReasonCode(),
                    decision.getReason(),
                    decision.getActorLabel());
        }

        SpRebalanceRecommendation recommendation = decision.getRecommendation();
        SpAnalysisRun analysisRun = recommendation.getReceiverMetric().getAnalysisRun();
        return IdempotencyFingerprint.compute(
                recommendation.getRecommendationId(),
                analysisRun.getAnalysisRunId(),
                analysisRun.getInputSnapshotVersion(),
                analysisRun.getRuleVersion(),
                recommendation.getCandidateVersion(),
                decision.getDecisionStatus().name(),
                decision.getSelectedQuantity(),
                decision.isPolicyException(),
                decision.getReasonCode(),
                decision.getReason(),
                decision.getActorLabel());
    }

    public record ExistingKeyLookup(String fingerprint, ApprovalTransactionResult replayResult) {
    }
}
