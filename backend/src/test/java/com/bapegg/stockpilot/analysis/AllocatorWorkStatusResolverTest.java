package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.analysis.AllocatorWorkStatusResolver.ExecutableCandidate;
import com.bapegg.stockpilot.demand.InventoryExceptionType;
import com.bapegg.stockpilot.rebalance.DecisionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure unit tests for the {@link AllocatorWorkStatus} precedence, per
 * {@code knowledge/state/2026-08-30-allocator-workbench-redesign-spec.md} sections 4.1 and 12.1.
 * No Spring context, no Oracle.
 */
class AllocatorWorkStatusResolverTest {

    @Test
    void undecidedExecutableCandidateIsDecisionRequired() {
        AllocatorWorkStatus status = AllocatorWorkStatusResolver.resolve(
                InventoryExceptionType.STOCKOUT_RISK, List.of(new ExecutableCandidate(1L, null)));
        assertEquals(AllocatorWorkStatus.DECISION_REQUIRED, status);
    }

    @Test
    void onlyHeldExecutableCandidateIsOnHold() {
        AllocatorWorkStatus status = AllocatorWorkStatusResolver.resolve(
                InventoryExceptionType.STOCKOUT_RISK, List.of(new ExecutableCandidate(1L, DecisionStatus.HELD)));
        assertEquals(AllocatorWorkStatus.ON_HOLD, status);
    }

    @Test
    void onlyTerminalExecutableCandidatesIsCompleted() {
        AllocatorWorkStatus status = AllocatorWorkStatusResolver.resolve(
                InventoryExceptionType.STOCKOUT_RISK, List.of(new ExecutableCandidate(1L, DecisionStatus.APPROVED)));
        assertEquals(AllocatorWorkStatus.COMPLETED, status);
    }

    @Test
    void reviewOrNonActionableWithoutExecutableCandidateIsReviewInput() {
        assertEquals(AllocatorWorkStatus.REVIEW_INPUT,
                AllocatorWorkStatusResolver.resolve(InventoryExceptionType.REVIEW_REQUIRED, List.of()));
        assertEquals(AllocatorWorkStatus.REVIEW_INPUT,
                AllocatorWorkStatusResolver.resolve(InventoryExceptionType.NON_ACTIONABLE, List.of()));
    }

    @Test
    void stockoutOrOverstockWithoutExecutableCandidateIsNoTransferOption() {
        assertEquals(AllocatorWorkStatus.NO_TRANSFER_OPTION,
                AllocatorWorkStatusResolver.resolve(InventoryExceptionType.STOCKOUT_RISK, List.of()));
        assertEquals(AllocatorWorkStatus.NO_TRANSFER_OPTION,
                AllocatorWorkStatusResolver.resolve(InventoryExceptionType.OVERSTOCK, List.of()));
    }

    @Test
    void mixedCandidatesPreferUndecidedOverHeldOverTerminal() {
        AllocatorWorkStatus decisionRequired = AllocatorWorkStatusResolver.resolve(
                InventoryExceptionType.STOCKOUT_RISK,
                List.of(new ExecutableCandidate(1L, DecisionStatus.APPROVED),
                        new ExecutableCandidate(2L, DecisionStatus.HELD),
                        new ExecutableCandidate(3L, null)));
        assertEquals(AllocatorWorkStatus.DECISION_REQUIRED, decisionRequired);

        AllocatorWorkStatus onHold = AllocatorWorkStatusResolver.resolve(
                InventoryExceptionType.STOCKOUT_RISK,
                List.of(new ExecutableCandidate(1L, DecisionStatus.APPROVED),
                        new ExecutableCandidate(2L, DecisionStatus.HELD)));
        assertEquals(AllocatorWorkStatus.ON_HOLD, onHold);
    }

    @Test
    void logicalPendingDecisionRowBehavesLikeNoDecision() {
        AllocatorWorkStatus status = AllocatorWorkStatusResolver.resolve(
                InventoryExceptionType.STOCKOUT_RISK, List.of(new ExecutableCandidate(1L, DecisionStatus.PENDING)));
        assertEquals(AllocatorWorkStatus.DECISION_REQUIRED, status);
    }
}
