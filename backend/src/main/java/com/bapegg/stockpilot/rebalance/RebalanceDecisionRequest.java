package com.bapegg.stockpilot.rebalance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * {@code recommendationId} is required and positive for both legacy and MVP-2 requests alike --
 * a common-field {@code VALIDATION_ERROR} Bean Validation catches before either path ever runs,
 * per the Codex review's P2 finding ("common/legacy input validation occurs in the wrong layer
 * and order"). {@code decisionStatus}/{@code actorLabel} stay required unconditionally too.
 * {@code selectedQuantity} and {@code reason} are validated conditionally instead -- MVP-2's
 * {@code HELD}/{@code REJECTED} require {@code selectedQuantity=null}, which a static
 * {@code @NotNull} could never express -- so {@link RebalanceDecisionController} checks the
 * legacy branch's required-ness itself, before any repository access, and the MVP-2 branch's
 * shape rules all come from
 * {@link com.bapegg.stockpilot.approval.ApprovalTransactionCommand}'s own canonical constructor.
 * The five additive MVP-2 fields below are optional here -- {@link RebalanceDecisionController}
 * enforces the "all four version-tuple fields plus exactly one Idempotency-Key header, or none of
 * the MVP-2 signals at all" cross-field rule itself, per current-task.md section 1.
 */
public record RebalanceDecisionRequest(
        @NotNull @Positive Long recommendationId,
        @NotNull DecisionStatus decisionStatus,
        Integer selectedQuantity,
        String reason,
        @NotBlank String actorLabel,
        Long analysisRunId,
        String inputSnapshotVersion,
        String ruleVersion,
        Integer candidateVersion,
        Boolean policyException,
        String reasonCode
) {
}
