package com.bapegg.stockpilot.demand;

/**
 * One approval request, per {@code knowledge/business-rules.md} section 10. Immutable and
 * independent of Spring/JPA. Validates only the request's own shape (quantity nullability per
 * status, required reason for terminal non-approval statuses) -- comparing it against the
 * freshly recomputed current basis (staleness, recalculated limits, "was the quantity actually
 * changed") is {@link ApprovalRequestValidation#validate}'s job, since that needs the basis
 * this record does not carry.
 * <p>
 * {@code policyException} lets the caller declare an {@code APPROVED} decision as a policy
 * exception even when {@code selectedQuantity} happens to equal the recommended {@code BASE}
 * quantity -- section 10 requires a reason for "정책 예외 승인" independently of whether the
 * quantity itself changed, so that fact cannot be inferred from the quantity comparison alone.
 * Ignored for every other status.
 */
public record ApprovalRequest(
        String analysisRunId,
        String inputSnapshotVersion,
        String ruleVersion,
        int candidateVersion,
        DecisionStatus status,
        Integer selectedQuantity,
        boolean policyException,
        String reasonCode,
        String reason
) {

    public ApprovalRequest {
        if (isBlank(analysisRunId) || isBlank(inputSnapshotVersion) || isBlank(ruleVersion)) {
            throw new IllegalArgumentException("analysisRunId, inputSnapshotVersion and ruleVersion must not be blank.");
        }
        if (candidateVersion <= 0) {
            throw new IllegalArgumentException("candidateVersion must be positive.");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null.");
        }
        if (policyException && status != DecisionStatus.APPROVED) {
            throw new IllegalArgumentException("policyException can only be true when status is APPROVED.");
        }

        switch (status) {
            case PENDING -> {
                if (selectedQuantity != null) {
                    throw new IllegalArgumentException("selectedQuantity must be null when status is PENDING.");
                }
            }
            case APPROVED -> {
                if (selectedQuantity == null || selectedQuantity <= 0) {
                    throw new IllegalArgumentException("selectedQuantity must be positive when status is APPROVED.");
                }
            }
            case HELD, REJECTED, EXPIRED -> {
                if (selectedQuantity != null) {
                    throw new IllegalArgumentException(
                            "selectedQuantity must be null when status is HELD, REJECTED or EXPIRED.");
                }
                if (isBlank(reasonCode) || isBlank(reason)) {
                    throw new IllegalArgumentException(
                            "reasonCode and reason are required when status is HELD, REJECTED or EXPIRED.");
                }
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
