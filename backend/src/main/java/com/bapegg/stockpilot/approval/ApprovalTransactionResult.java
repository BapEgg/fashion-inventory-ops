package com.bapegg.stockpilot.approval;

import com.bapegg.stockpilot.rebalance.DecisionStatus;

/**
 * The outcome of one {@link ApprovalTransactionFacade#execute} call. {@code created}
 * distinguishes a freshly written decision ({@code true}) from an idempotency-key
 * replay of an already-committed one ({@code false}); {@code transferDraftId} is
 * non-null only when {@code status == APPROVED}.
 */
public record ApprovalTransactionResult(
        Long decisionId,
        DecisionStatus status,
        int decisionSequence,
        Long transferDraftId,
        boolean created
) {
}
