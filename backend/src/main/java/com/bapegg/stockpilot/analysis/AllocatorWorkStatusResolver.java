package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.demand.InventoryExceptionType;
import com.bapegg.stockpilot.rebalance.DecisionStatus;

import java.util.List;

/**
 * The single Java implementation of the {@link AllocatorWorkStatus} precedence, per
 * {@code knowledge/state/2026-08-30-allocator-workbench-redesign-spec.md} section 4.1. Applied
 * once per metric to that metric's executable candidates only -- callers must pre-filter to
 * {@code candidateStatus=ELIGIBLE && recommendationMode=RECOMMENDED} before building
 * {@link ExecutableCandidate} values; this class does not re-check candidate/mode itself.
 */
public final class AllocatorWorkStatusResolver {

    private AllocatorWorkStatusResolver() {
    }

    /**
     * One executable candidate's latest decision, or {@code null} when no decision row exists yet
     * (the logical {@code PENDING} state -- {@link DecisionStatus} never persists a physical
     * {@code PENDING} row).
     */
    public record ExecutableCandidate(Long recommendationId, DecisionStatus latestDecisionStatus) {
    }

    public static AllocatorWorkStatus resolve(
            InventoryExceptionType exceptionType, List<ExecutableCandidate> executableCandidates) {
        boolean anyUndecidedOrPending = executableCandidates.stream()
                .anyMatch(c -> c.latestDecisionStatus() == null || c.latestDecisionStatus() == DecisionStatus.PENDING);
        if (anyUndecidedOrPending) {
            return AllocatorWorkStatus.DECISION_REQUIRED;
        }
        boolean anyHeld = executableCandidates.stream()
                .anyMatch(c -> c.latestDecisionStatus() == DecisionStatus.HELD);
        if (anyHeld) {
            return AllocatorWorkStatus.ON_HOLD;
        }
        if (!executableCandidates.isEmpty()) {
            return AllocatorWorkStatus.COMPLETED;
        }
        if (exceptionType == InventoryExceptionType.REVIEW_REQUIRED || exceptionType == InventoryExceptionType.NON_ACTIONABLE) {
            return AllocatorWorkStatus.REVIEW_INPUT;
        }
        return AllocatorWorkStatus.NO_TRANSFER_OPTION;
    }
}
