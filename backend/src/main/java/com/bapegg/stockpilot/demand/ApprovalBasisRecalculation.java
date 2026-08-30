package com.bapegg.stockpilot.demand;

import java.math.BigDecimal;

/**
 * Pure deterministic re-derivation of one donor-receiver-route candidate's approval
 * basis at approval time, per {@code knowledge/business-rules.md} section 10. Combines
 * {@link InventoryProjection}, {@link TransferCandidateEvaluation} and route/policy
 * limits into exactly the fields {@link RecommendationBasis} needs, so a fresh
 * {@link ApprovalRequestValidation#validate} can run against the current state instead
 * of the stale recommendation-time numbers.
 * <p>
 * The BASE quantity formula below (need -&gt; min against donor/route/capacity -&gt; floor
 * to package multiple -&gt; zero if below route minimum) is the same one
 * {@link TransferScenarioSet}'s {@code BASE} scenario already uses; it is re-derived here
 * rather than reusing that class directly, since {@code TransferScenarioSet} computes
 * all four scenarios at once and needs event-uplift/confidence inputs this narrower,
 * single-candidate recalculation does not.
 */
public record ApprovalBasisRecalculation(
        TransferCandidateEvaluation candidateEvaluation,
        long recommendedBaseQuantity,
        long donorTransferableQuantity,
        int routeMinimumQuantity,
        int packageMultiple,
        int routeMaximumQuantity,
        long receiverCapacityRemaining,
        long receiverProjectedBeforeDemand,
        long donorProjectedAtDispatch,
        long alreadyApprovedDraftQuantity
) {

    /**
     * Per business-rules.md section 10: an ineligible candidate or a non-positive BASE
     * quantity can never be approved, regardless of the caller's requested quantity.
     */
    public boolean eligible() {
        return candidateEvaluation.eligible() && recommendedBaseQuantity > 0;
    }

    public static ApprovalBasisRecalculation calculate(
            String skuId,
            String receiverStoreId,
            String receiverOwnerCode,
            String donorStoreId,
            String donorOwnerCode,
            TransferRoute route,
            InventoryProjection receiverProjection,
            BigDecimal receiverBaseRate,
            int receiverTargetCoverageDays,
            int receiverDisplayMinimum,
            int receiverMaximumCapacity,
            InventoryProjection donorProjection,
            BigDecimal donorHighRate,
            int donorRetainedDays,
            int donorDisplayMinimum,
            int donorSafetyStock,
            boolean receiverHasConfirmedInbound,
            boolean pendingTransferConflict) {
        if (route == null) {
            throw new IllegalArgumentException("route must not be null.");
        }
        if (receiverMaximumCapacity <= 0) {
            throw new IllegalArgumentException("receiverMaximumCapacity must be positive.");
        }

        long donorProtected = donorProjection.donorProtectedQuantity(
                donorHighRate, donorRetainedDays, donorDisplayMinimum, donorSafetyStock);
        long donorTransferableQuantity = Math.max(donorProjection.projectedDonorAtDispatch() - donorProtected, 0);
        long receiverCapacityRemaining =
                Math.max(receiverMaximumCapacity - receiverProjection.projectedReceiverBeforeDemand(), 0);

        long targetQuantity = receiverProjection.receiverTargetQuantity(
                receiverBaseRate, route.leadTimeDays(), receiverTargetCoverageDays, receiverDisplayMinimum);
        long receiverNeed = Math.max(targetQuantity - receiverProjection.projectedReceiverBeforeDemand(), 0);
        long rawQuantity = Math.min(
                Math.min(receiverNeed, donorTransferableQuantity),
                Math.min((long) route.maximumQuantity(), receiverCapacityRemaining));
        long flooredQuantity = (rawQuantity / route.packageMultiple()) * route.packageMultiple();
        long baseQuantity = flooredQuantity >= route.minimumQuantity() ? flooredQuantity : 0;

        // Confirmed inbound already fed into projectedReceiverBeforeDemand above; if that alone
        // reduced the target need to zero, the shortage this candidate would exist to solve is
        // already resolved without a transfer -- the INBOUND_ALREADY_COVERS reason, not a
        // structural rejection like OWNER_MISMATCH.
        boolean confirmedInboundAlreadyCoversShortage = receiverHasConfirmedInbound && receiverNeed == 0;

        TransferCandidateEvaluation candidateEvaluation = TransferCandidateEvaluation.evaluate(
                skuId, receiverStoreId, receiverOwnerCode, donorStoreId, donorOwnerCode,
                route, receiverProjection, receiverBaseRate, receiverMaximumCapacity,
                donorProjection, donorHighRate, donorRetainedDays, donorDisplayMinimum, donorSafetyStock,
                confirmedInboundAlreadyCoversShortage, pendingTransferConflict);

        return new ApprovalBasisRecalculation(
                candidateEvaluation,
                baseQuantity,
                donorTransferableQuantity,
                route.minimumQuantity(),
                route.packageMultiple(),
                route.maximumQuantity(),
                receiverCapacityRemaining,
                receiverProjection.projectedReceiverBeforeDemand(),
                donorProjection.projectedDonorAtDispatch(),
                donorProjection.alreadyApprovedDraftQuantity());
    }
}
