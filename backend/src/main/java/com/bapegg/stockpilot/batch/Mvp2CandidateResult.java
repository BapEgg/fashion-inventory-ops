package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.demand.TransferCandidateRejectionReason;
import com.bapegg.stockpilot.demand.TransferScenarioResult;
import com.bapegg.stockpilot.rebalance.CandidateStatus;
import com.bapegg.stockpilot.rebalance.RecommendationMode;

import java.util.List;

/**
 * One donor-receiver-SKU lane's candidate evaluation, per {@code data-model.md} section 6.
 * {@code rejectionReasons} preserves every applicable reason in
 * {@link TransferCandidateRejectionReason}'s declared order, never just a representative one.
 * {@code scenarios} is empty unless {@link #candidateStatus()} is {@link CandidateStatus#ELIGIBLE}
 * -- rejected candidates never get {@code NO_ACTION}/{@code CONSERVATIVE}/{@code BASE}/
 * {@code AGGRESSIVE} rows.
 */
public record Mvp2CandidateResult(
        String receiverStoreId,
        String donorStoreId,
        String skuId,
        Long routeId,
        CandidateStatus candidateStatus,
        int candidateVersion,
        RecommendationMode recommendationMode,
        Integer receiverShortageQuantity,
        Integer donorTransferableQuantity,
        Integer recommendedQuantity,
        Long projectedReceiverAtArrival,
        Long projectedDonorAtDispatch,
        Long receiverCapacityRemaining,
        List<TransferCandidateRejectionReason> rejectionReasons,
        int packageMultiple,
        List<TransferScenarioResult> scenarios
) {

    public Mvp2CandidateResult {
        rejectionReasons = List.copyOf(rejectionReasons);
        scenarios = List.copyOf(scenarios);
    }
}
