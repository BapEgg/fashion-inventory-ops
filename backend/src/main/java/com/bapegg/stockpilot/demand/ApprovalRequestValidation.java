package com.bapegg.stockpilot.demand;

/**
 * Pure deterministic approval-request validation for one decision, per
 * {@code knowledge/business-rules.md} section 10. Independent of Spring/JPA.
 * <p>
 * Returns only a verdict -- it never stores a decision row, a transfer draft, or changes
 * inventory. Section 10's donor row lock, re-fetch of the latest state, and append-only
 * persistence of an {@code APPROVED} decision plus its {@code SP_TRANSFER_DRAFT} are all a
 * future transactional layer's responsibility.
 */
public record ApprovalRequestValidation(ApprovalOutcome outcome) {

    public ApprovalRequestValidation {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null.");
        }
    }

    public boolean stale() {
        return outcome == ApprovalOutcome.STALE_RECOMMENDATION;
    }

    /**
     * @param request the submitted decision; already validated for its own status-shape by
     *                {@link ApprovalRequest}'s constructor
     * @param basis   the freshly recomputed current state to check {@code request} against,
     *                including {@link RecommendationBasis#candidateEligible()} -- a quantity can
     *                sit within every numeric limit below and still be stale for an unrelated
     *                section 7 reason (owner mismatch, an inactive route, lead time, inbound
     *                already covering the shortage, a pending transfer conflict), so that fact
     *                is checked independently of the quantity limits, not inferred from them
     * @throws IllegalArgumentException if {@code request} approves a quantity that differs from
     *                                  {@link RecommendationBasis#recommendedBaseQuantity()} (a
     *                                  changed quantity), or is explicitly flagged
     *                                  {@link ApprovalRequest#policyException()}, without a
     *                                  reason code and reason -- section 10: "수량 변경, ...,
     *                                  정책 예외 승인에는 reason code와 설명이 필수다." This is
     *                                  a caller-contract defect in the request itself, not a
     *                                  staleness outcome to branch on.
     */
    public static ApprovalRequestValidation validate(ApprovalRequest request, RecommendationBasis basis) {
        boolean versionMismatch = !request.analysisRunId().equals(basis.analysisRunId())
                || !request.inputSnapshotVersion().equals(basis.inputSnapshotVersion())
                || !request.ruleVersion().equals(basis.ruleVersion())
                || request.candidateVersion() != basis.candidateVersion();
        if (versionMismatch) {
            return new ApprovalRequestValidation(ApprovalOutcome.STALE_RECOMMENDATION);
        }

        if (request.status() == DecisionStatus.APPROVED) {
            // Ineligibility is checked before, and independently of, the numeric limits: a
            // section 7 rejection reason (owner mismatch, inactive route, lead time, inbound
            // already covering the shortage, pending transfer conflict) can apply even when the
            // requested quantity still fits every limit below, and no reason can excuse it.
            if (!basis.candidateEligible()) {
                return new ApprovalRequestValidation(ApprovalOutcome.STALE_RECOMMENDATION);
            }

            long selectedQuantity = request.selectedQuantity();
            boolean withinRecalculatedLimits = selectedQuantity >= basis.routeMinimumQuantity()
                    && selectedQuantity <= basis.routeMaximumQuantity()
                    && selectedQuantity <= basis.donorTransferableQuantity()
                    && selectedQuantity <= basis.receiverCapacityRemaining()
                    && selectedQuantity % basis.packageMultiple() == 0;
            if (!withinRecalculatedLimits) {
                return new ApprovalRequestValidation(ApprovalOutcome.STALE_RECOMMENDATION);
            }

            boolean approvingExactlyTheRecommendedBaseQuantity = selectedQuantity == basis.recommendedBaseQuantity();
            boolean needsReason = !approvingExactlyTheRecommendedBaseQuantity || request.policyException();
            if (needsReason && isBlankReason(request)) {
                throw new IllegalArgumentException("Approving a quantity that differs from the recommended BASE "
                        + "quantity, or that is explicitly a policy exception, requires a reason code and reason.");
            }
        }

        return new ApprovalRequestValidation(ApprovalOutcome.VALID);
    }

    private static boolean isBlankReason(ApprovalRequest request) {
        return request.reasonCode() == null || request.reasonCode().isBlank()
                || request.reason() == null || request.reason().isBlank();
    }
}
