package com.bapegg.stockpilot.rebalance;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The {@code GET /api/rebalancing-decisions/{recommendationId}} shape, per current-task.md
 * section 4. {@code currentStatus} is {@code PENDING} with an empty {@code decisions} array when
 * no decision row exists yet; otherwise it is the highest-{@code decisionSequence} row's status
 * and {@code decisions} is the full append-only history, ascending. Neither
 * {@code decisionRequestId} (the Idempotency-Key) nor its fingerprint is ever exposed here.
 */
public record Mvp2DecisionHistoryResponse(
        Long recommendationId,
        DecisionStatus currentStatus,
        List<DecisionItem> decisions
) {

    /**
     * One append-only history row. {@code approvalBasis}/{@code transferDraft} are non-null only
     * for an MVP-2 {@code APPROVED} row -- {@code HELD}/{@code REJECTED}/{@code EXPIRED} and every
     * legacy MVP-1 decision carry {@code null} for both.
     */
    public record DecisionItem(
            Long decisionId,
            int decisionSequence,
            DecisionStatus decisionStatus,
            Integer selectedQuantity,
            boolean policyException,
            String reasonCode,
            String reason,
            String actorLabel,
            int recommendationVersion,
            String decisionContractVersion,
            OffsetDateTime decidedAt,
            ApprovalBasisItem approvalBasis,
            TransferDraftItem transferDraft
    ) {
    }

    /** A one-to-one read of the stored {@code sp_approval_basis} row backing one APPROVED decision. */
    public record ApprovalBasisItem(
            Long approvalBasisId,
            Long analysisRunId,
            String inputSnapshotVersion,
            String ruleVersion,
            int candidateVersion,
            boolean candidateEligible,
            long recommendedBaseQuantity,
            long donorTransferableQuantity,
            int routeMinimumQuantity,
            int packageMultiple,
            int routeMaximumQuantity,
            long receiverCapacityRemaining,
            long receiverProjectedBeforeDemand,
            long donorProjectedAtDispatch,
            long alreadyApprovedDraftQuantity,
            String basisContractVersion,
            OffsetDateTime createdAt
    ) {
    }

    /**
     * A read of the stored {@code sp_transfer_draft} row's current state -- this is a read model
     * only, never a trigger for a {@code READY} transition or an ERP call.
     */
    public record TransferDraftItem(
            Long transferDraftId,
            String donorStoreId,
            String receiverStoreId,
            String skuId,
            Integer quantity,
            DraftStatus draftStatus,
            String externalReference,
            String payloadVersion,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
