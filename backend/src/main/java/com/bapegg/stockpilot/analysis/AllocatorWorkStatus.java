package com.bapegg.stockpilot.analysis;

/**
 * The allocator-facing derived work status for one inventory-exception row, per
 * {@code knowledge/state/2026-08-30-allocator-workbench-redesign-spec.md} section 4.1. Computed
 * from the metric's executable candidates (recommendations with
 * {@code candidateStatus=ELIGIBLE} and {@code recommendationMode=RECOMMENDED}) and their latest
 * decisions -- never stored, never chosen by AI. {@link AllocatorWorkStatusResolver} is the single
 * Java source of this derivation; the repository layer computes the same precedence directly in
 * SQL for filtering/sorting the full result set, and the two are kept in sync by test, not by a
 * single shared code path.
 */
public enum AllocatorWorkStatus {
    DECISION_REQUIRED,
    ON_HOLD,
    REVIEW_INPUT,
    NO_TRANSFER_OPTION,
    COMPLETED
}
