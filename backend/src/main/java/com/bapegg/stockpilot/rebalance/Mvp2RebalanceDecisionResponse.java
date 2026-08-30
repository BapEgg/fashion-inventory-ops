package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.approval.ApprovalTransactionResult;

/**
 * The MVP-2 {@code POST /api/rebalancing-decisions} success shape, per current-task.md section 2 --
 * identity plus the four fields a caller needs to confirm what happened
 * ({@code decisionSequence}, {@code transferDraftId}, {@code created}). Detailed audit values are
 * never duplicated here; the canonical detail lives behind
 * {@code GET /api/rebalancing-decisions/{recommendationId}}.
 */
public record Mvp2RebalanceDecisionResponse(
        Long decisionId,
        Long recommendationId,
        DecisionStatus decisionStatus,
        int decisionSequence,
        Long transferDraftId,
        boolean created
) {

    public static Mvp2RebalanceDecisionResponse from(Long recommendationId, ApprovalTransactionResult result) {
        return new Mvp2RebalanceDecisionResponse(
                result.decisionId(), recommendationId, result.status(), result.decisionSequence(),
                result.transferDraftId(), result.created());
    }
}
