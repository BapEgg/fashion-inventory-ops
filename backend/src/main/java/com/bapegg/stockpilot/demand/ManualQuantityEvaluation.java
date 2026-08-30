package com.bapegg.stockpilot.demand;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure deterministic evaluation of one manually requested transfer quantity against the current
 * recommendation basis, per {@code knowledge/business-rules.md} section 10's `MANUAL`
 * quantity-test contract (a {@code MVP-2} demo {@code ASSUMPTION}, not real enterprise policy):
 * the input is a positive integer transfer **quantity**, never a demand rate; nothing is
 * silently rounded or substituted; every applicable hard-constraint violation is returned
 * alongside a lower feasible suggestion.
 * <p>
 * Never persisted and never itself re-derives the numeric limits or candidate eligibility --
 * both come from calling the same {@link ApprovalBasisRecalculation#calculate} the approval
 * transaction uses, so a manual test and a real approval can never silently disagree about what
 * is currently feasible. {@link #projection()} is the one calculation unique to this contract:
 * the hypothetical before/after position {@code requestedQuantity} would produce, built via the
 * same {@link TransferEffectProjection} the automatic scenarios use, present only when
 * {@link #feasible()}.
 */
public record ManualQuantityEvaluation(
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
        ManualQuantityProjection projection
) {

    public ManualQuantityEvaluation {
        violations = List.copyOf(violations);
        candidateRejectionReasons = List.copyOf(candidateRejectionReasons);
    }

    /**
     * @param analysisDate    this analysis run's reference date, used with the route's lead time
     *                        for {@link ManualQuantityProjection#expectedArrivalDate()}
     * @param donorBaseRate   the donor's current BASE demand rate, nullable (V6 allows a null BASE
     *                        rate), used only for the donor's own reported coverage-days display,
     *                        which is null when this is null -- donor protection/transferable
     *                        quantity still uses {@code donorHighRate}, unchanged from
     *                        {@link ApprovalBasisRecalculation}
     * @param requestedQuantity the manually entered transfer quantity; must be positive
     */
    public static ManualQuantityEvaluation calculate(
            String skuId,
            String receiverStoreId,
            String receiverOwnerCode,
            String donorStoreId,
            String donorOwnerCode,
            TransferRoute route,
            LocalDate analysisDate,
            InventoryProjection receiverProjection,
            BigDecimal receiverBaseRate,
            int receiverTargetCoverageDays,
            int receiverDisplayMinimum,
            int receiverMaximumCapacity,
            InventoryProjection donorProjection,
            BigDecimal donorBaseRate,
            BigDecimal donorHighRate,
            int donorRetainedDays,
            int donorDisplayMinimum,
            int donorSafetyStock,
            boolean receiverHasConfirmedInbound,
            boolean pendingTransferConflict,
            long requestedQuantity) {
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("requestedQuantity must be positive.");
        }

        ApprovalBasisRecalculation recalculation = ApprovalBasisRecalculation.calculate(
                skuId, receiverStoreId, receiverOwnerCode, donorStoreId, donorOwnerCode, route,
                receiverProjection, receiverBaseRate, receiverTargetCoverageDays, receiverDisplayMinimum,
                receiverMaximumCapacity, donorProjection, donorHighRate, donorRetainedDays, donorDisplayMinimum,
                donorSafetyStock, receiverHasConfirmedInbound, pendingTransferConflict);

        // Uses ApprovalBasisRecalculation.eligible() (not just candidateEvaluation().eligible()) so a
        // structurally-eligible candidate whose recommendedBaseQuantity is zero -- which approval
        // rejects as STALE_RECOMMENDATION -- is reported here as CANDIDATE_INELIGIBLE too, keeping the
        // two paths' feasibility conclusion identical for the same basis.
        boolean eligible = recalculation.eligible();
        List<ManualQuantityViolation> violations = new ArrayList<>();
        if (!eligible) {
            violations.add(ManualQuantityViolation.CANDIDATE_INELIGIBLE);
        }
        if (requestedQuantity < recalculation.routeMinimumQuantity()) {
            violations.add(ManualQuantityViolation.BELOW_ROUTE_MINIMUM);
        }
        if (requestedQuantity % recalculation.packageMultiple() != 0) {
            violations.add(ManualQuantityViolation.NOT_PACKAGE_MULTIPLE);
        }
        if (requestedQuantity > recalculation.donorTransferableQuantity()) {
            violations.add(ManualQuantityViolation.EXCEEDS_DONOR_TRANSFERABLE);
        }
        if (requestedQuantity > recalculation.routeMaximumQuantity()) {
            violations.add(ManualQuantityViolation.EXCEEDS_ROUTE_MAXIMUM);
        }
        if (requestedQuantity > recalculation.receiverCapacityRemaining()) {
            violations.add(ManualQuantityViolation.EXCEEDS_RECEIVER_CAPACITY);
        }

        long hardCeiling = Math.min(recalculation.donorTransferableQuantity(),
                Math.min((long) recalculation.routeMaximumQuantity(), recalculation.receiverCapacityRemaining()));
        long maximumFeasibleQuantity = (hardCeiling / recalculation.packageMultiple()) * recalculation.packageMultiple();
        if (!eligible || maximumFeasibleQuantity < recalculation.routeMinimumQuantity()) {
            maximumFeasibleQuantity = 0;
        }

        long suggestedQuantity = (Math.min(requestedQuantity, maximumFeasibleQuantity) / recalculation.packageMultiple())
                * recalculation.packageMultiple();
        if (suggestedQuantity < recalculation.routeMinimumQuantity()) {
            suggestedQuantity = 0;
        }

        boolean reasonRequired = requestedQuantity != recalculation.recommendedBaseQuantity();
        boolean feasible = eligible && violations.isEmpty();

        ManualQuantityProjection projection = null;
        if (feasible) {
            long donorProtected = donorProjection.donorProtectedQuantity(
                    donorHighRate, donorRetainedDays, donorDisplayMinimum, donorSafetyStock);
            TransferEffectProjection effect = TransferEffectProjection.calculate(
                    receiverProjection, receiverBaseRate, route.leadTimeDays(),
                    receiverTargetCoverageDays, receiverDisplayMinimum,
                    donorProjection, donorBaseRate, donorProtected, requestedQuantity);
            projection = new ManualQuantityProjection(
                    effect.receiverBeforeAvailable(), effect.receiverAfterAvailable(),
                    effect.receiverBeforeCoverageDays(), effect.receiverAfterCoverageDays(), effect.receiverRiskCode(),
                    effect.donorBeforeAvailable(), effect.donorAfterAvailable(),
                    effect.donorBeforeCoverageDays(), effect.donorAfterCoverageDays(), effect.donorRiskCode(),
                    route.leadTimeDays(), analysisDate.plusDays(route.leadTimeDays()),
                    receiverProjection.inboundArrivingBeforeTransfer(), receiverProjection.openTransferInbound(),
                    receiverProjection.openTransferOutbound(),
                    donorProjection.inboundArrivingBeforeDispatch(), donorProjection.openTransferOutbound(),
                    donorProjection.alreadyApprovedDraftQuantity());
        }

        return new ManualQuantityEvaluation(
                requestedQuantity, feasible, reasonRequired,
                recalculation.recommendedBaseQuantity(), maximumFeasibleQuantity, suggestedQuantity,
                violations, List.copyOf(recalculation.candidateEvaluation().reasons()),
                recalculation.routeMinimumQuantity(), recalculation.packageMultiple(),
                recalculation.routeMaximumQuantity(), recalculation.donorTransferableQuantity(),
                recalculation.receiverCapacityRemaining(), projection);
    }
}
