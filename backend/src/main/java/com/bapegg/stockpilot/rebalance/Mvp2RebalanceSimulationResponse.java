package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.approval.ManualQuantityTestResult;
import com.bapegg.stockpilot.demand.ManualQuantityProjection;
import com.bapegg.stockpilot.demand.ManualQuantityViolation;
import com.bapegg.stockpilot.demand.TransferCandidateRejectionReason;

import java.util.List;

/**
 * The MVP-2 {@code MANUAL} quantity-test REST shape, per current-task.md section 3 -- a one-to-one
 * mapping of {@link ManualQuantityTestResult}. A constraint violation is a normal test outcome
 * ({@code feasible=false}), never an HTTP error: this always returns 200. {@code projection} is
 * {@code null} exactly when {@code feasible} is {@code false}.
 */
public record Mvp2RebalanceSimulationResponse(
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
        boolean approvalRevalidationRequired,
        AssumptionNotice assumption
) {

    private static final String ASSUMPTION_NOTICE =
            "수량 시험 결과는 MVP-2 데모 가정이며 실제 승인 시 최신 근거로 다시 검증합니다.";

    public static Mvp2RebalanceSimulationResponse from(ManualQuantityTestResult result) {
        return new Mvp2RebalanceSimulationResponse(
                result.recommendationId(), result.analysisRunId(), result.inputSnapshotVersion(),
                result.ruleVersion(), result.candidateVersion(), result.requestedQuantity(),
                result.feasible(), result.reasonRequired(), result.recommendedBaseQuantity(),
                result.maximumFeasibleQuantity(), result.suggestedQuantity(),
                result.violations(), result.candidateRejectionReasons(),
                result.routeMinimumQuantity(), result.packageMultiple(), result.routeMaximumQuantity(),
                result.donorTransferableQuantity(), result.receiverCapacityRemaining(),
                result.projection(), result.approvalRevalidationRequired(),
                new AssumptionNotice("ASSUMPTION", ASSUMPTION_NOTICE));
    }

    public record AssumptionNotice(String type, String notice) {
    }
}
