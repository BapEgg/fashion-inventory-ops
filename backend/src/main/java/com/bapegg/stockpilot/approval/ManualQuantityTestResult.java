package com.bapegg.stockpilot.approval;

import com.bapegg.stockpilot.demand.ManualQuantityProjection;
import com.bapegg.stockpilot.demand.ManualQuantityViolation;
import com.bapegg.stockpilot.demand.TransferCandidateRejectionReason;

import java.util.List;

/**
 * The outcome of one {@link ManualQuantityTestExecutor#test} call, per
 * {@code knowledge/business-rules.md} section 10's `MANUAL` quantity-test contract. Carries no
 * decision or draft id -- nothing is persisted. {@link #approvalRevalidationRequired()} is always
 * {@code true}: this result is a preview only, and an actual approval re-locks and recomputes
 * everything from scratch rather than trusting this snapshot.
 */
public record ManualQuantityTestResult(
        Long recommendationId,
        Long analysisRunId,
        String inputSnapshotVersion,
        String ruleVersion,
        int candidateVersion,
        long requestedQuantity,
        boolean feasible,
        boolean reasonRequired,
        long recommendedBaseQuantity,
        long maximumFeasibleQuantity,
        long suggestedQuantity,
        List<ManualQuantityViolation> violations,
        List<TransferCandidateRejectionReason> candidateRejectionReasons,
        int routeMinimumQuantity,
        int packageMultiple,
        int routeMaximumQuantity,
        long donorTransferableQuantity,
        long receiverCapacityRemaining,
        ManualQuantityProjection projection,
        boolean approvalRevalidationRequired
) {

    public ManualQuantityTestResult {
        violations = List.copyOf(violations);
        candidateRejectionReasons = List.copyOf(candidateRejectionReasons);
    }
}
