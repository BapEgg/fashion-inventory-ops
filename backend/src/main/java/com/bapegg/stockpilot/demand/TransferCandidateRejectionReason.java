package com.bapegg.stockpilot.demand;

/**
 * Candidate rejection reason codes, per {@code knowledge/business-rules.md} section 7.
 * Declared in the document's fixed priority order, since {@link TransferCandidateEvaluation}
 * relies on that declaration order to pick a representative reason without discarding the rest.
 */
public enum TransferCandidateRejectionReason {
    OWNER_MISMATCH,
    ROUTE_NOT_ALLOWED,
    LEAD_TIME_TOO_LONG,
    INBOUND_ALREADY_COVERS,
    NO_TRANSFERABLE_STOCK,
    DISPLAY_MINIMUM_VIOLATION,
    CAPACITY_EXCEEDED,
    PENDING_TRANSFER_CONFLICT
}
