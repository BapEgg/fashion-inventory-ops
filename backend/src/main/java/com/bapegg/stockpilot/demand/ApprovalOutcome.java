package com.bapegg.stockpilot.demand;

/**
 * Result of {@link ApprovalRequestValidation#validate}, per
 * {@code knowledge/business-rules.md} section 10. A request-shape defect (wrong
 * quantity/reason shape for the given status, or an approved quantity that changed from the
 * recommendation without a reason) is not modeled here -- those are caller-contract violations
 * rejected by {@link ApprovalRequest}'s or {@link ApprovalRequestValidation#validate}'s own
 * {@code IllegalArgumentException}, not a business outcome a caller branches on.
 */
public enum ApprovalOutcome {
    VALID,
    STALE_RECOMMENDATION
}
