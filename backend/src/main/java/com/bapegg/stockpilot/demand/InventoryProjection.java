package com.bapegg.stockpilot.demand;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure deterministic receiver/donor projected-inventory calculation for one store-SKU, per
 * {@code knowledge/business-rules.md} section 6. Independent of Spring/JPA.
 * <p>
 * Per section 2, a negative {@link #currentAvailable()} (reserved exceeds on-hand) or a
 * negative {@link #projectedReceiverBeforeDemand()}/{@link #projectedDonorAtDispatch()} is a
 * {@code NON_ACTIONABLE} input condition, not a programming error -- none of the three is
 * clamped to zero or rejected here. {@link #isInputInvalid()} lets the caller route such a
 * metric to {@code NON_ACTIONABLE} instead.
 * <p>
 * The five confirmed-inbound/open-transfer inputs are retained as fields (not just folded into
 * the two projected totals) so a consumer such as {@link TransferScenarioResult} can preserve
 * the actual direction/quantity evidence behind a projection, rather than reducing it to an
 * opaque "inbound was included" flag.
 * <p>
 * The canonical constructor itself -- not just {@link #calculate}, its only normal entry point
 * -- enforces that {@link #projectedReceiverBeforeDemand()} and
 * {@link #projectedDonorAtDispatch()} are exactly what section 6's formulas derive from
 * {@link #currentAvailable()} and the five evidence fields. A caller that builds this record
 * directly (bypassing {@code calculate}) cannot otherwise construct a projection whose derived
 * totals disagree with the evidence a consumer like {@link TransferScenarioResult} trusts as
 * that projection's provenance.
 */
public record InventoryProjection(
        int currentAvailable,
        int projectedReceiverBeforeDemand,
        int projectedDonorAtDispatch,
        int inboundArrivingBeforeTransfer,
        int openTransferInbound,
        int openTransferOutbound,
        int inboundArrivingBeforeDispatch,
        int alreadyApprovedDraftQuantity
) {

    public InventoryProjection {
        if (inboundArrivingBeforeTransfer < 0 || openTransferInbound < 0 || openTransferOutbound < 0
                || inboundArrivingBeforeDispatch < 0 || alreadyApprovedDraftQuantity < 0) {
            throw new IllegalArgumentException(
                    "inboundArrivingBeforeTransfer, openTransferInbound, openTransferOutbound, "
                            + "inboundArrivingBeforeDispatch and alreadyApprovedDraftQuantity must not be negative.");
        }
        // Widened to long and converted back with a checked cast so that an out-of-int-range
        // true sum cannot wrap into an in-range value that coincidentally matches a
        // similarly-wrapped projectedReceiverBeforeDemand/projectedDonorAtDispatch argument,
        // flipping this consistency check from a rejection into a false pass.
        long expectedProjectedReceiverBeforeDemand =
                (long) currentAvailable + inboundArrivingBeforeTransfer + openTransferInbound - openTransferOutbound;
        long expectedProjectedDonorAtDispatch = (long) currentAvailable + inboundArrivingBeforeDispatch
                - openTransferOutbound - alreadyApprovedDraftQuantity;
        if (Math.toIntExact(expectedProjectedReceiverBeforeDemand) != projectedReceiverBeforeDemand) {
            throw new IllegalArgumentException("projectedReceiverBeforeDemand (" + projectedReceiverBeforeDemand
                    + ") does not match currentAvailable + inboundArrivingBeforeTransfer + openTransferInbound "
                    + "- openTransferOutbound (" + expectedProjectedReceiverBeforeDemand + ").");
        }
        if (Math.toIntExact(expectedProjectedDonorAtDispatch) != projectedDonorAtDispatch) {
            throw new IllegalArgumentException("projectedDonorAtDispatch (" + projectedDonorAtDispatch
                    + ") does not match currentAvailable + inboundArrivingBeforeDispatch - openTransferOutbound "
                    + "- alreadyApprovedDraftQuantity (" + expectedProjectedDonorAtDispatch + ").");
        }
    }

    public static InventoryProjection calculate(
            int onHandQuantity,
            int reservedQuantity,
            int inboundArrivingBeforeTransfer,
            int openTransferInbound,
            int openTransferOutbound,
            int inboundArrivingBeforeDispatch,
            int alreadyApprovedDraftQuantity) {
        if (onHandQuantity < 0 || reservedQuantity < 0 || inboundArrivingBeforeTransfer < 0
                || openTransferInbound < 0 || openTransferOutbound < 0
                || inboundArrivingBeforeDispatch < 0 || alreadyApprovedDraftQuantity < 0) {
            throw new IllegalArgumentException("Quantities must not be negative.");
        }
        // reservedQuantity > onHandQuantity is deliberately NOT rejected here: section 2 lists
        // it as one of two NON_ACTIONABLE input conditions, so currentAvailable is allowed to
        // go negative and is reported via isInputInvalid() rather than thrown.

        // Widened to long so that summing several individually in-range int inputs cannot
        // silently wrap a 32-bit int (the same class of defect fixed in RebalanceCalculation);
        // Math.toIntExact below throws if an actual out-of-range result would otherwise wrap.
        long currentAvailableLong = (long) onHandQuantity - reservedQuantity;
        long projectedReceiverBeforeDemandLong =
                currentAvailableLong + inboundArrivingBeforeTransfer + openTransferInbound - openTransferOutbound;
        long projectedDonorAtDispatchLong =
                currentAvailableLong + inboundArrivingBeforeDispatch - openTransferOutbound - alreadyApprovedDraftQuantity;

        return new InventoryProjection(
                Math.toIntExact(currentAvailableLong),
                Math.toIntExact(projectedReceiverBeforeDemandLong),
                Math.toIntExact(projectedDonorAtDispatchLong),
                inboundArrivingBeforeTransfer,
                openTransferInbound,
                openTransferOutbound,
                inboundArrivingBeforeDispatch,
                alreadyApprovedDraftQuantity);
    }

    public boolean isInputInvalid() {
        return currentAvailable < 0 || projectedReceiverBeforeDemand < 0 || projectedDonorAtDispatch < 0;
    }

    /** Per section 6: may be negative -- this is the pre-arrival stockout-risk signal itself. */
    public long receiverAtArrivalWithoutNewTransfer(BigDecimal rate, int leadTimeDays) {
        if (leadTimeDays < 0) {
            throw new IllegalArgumentException("leadTimeDays must not be negative.");
        }
        return (long) projectedReceiverBeforeDemand - ceilDemand(rate, leadTimeDays);
    }

    /** Per section 8's target-quantity formula, reused here for the section 6 STOCKOUT_RISK check. */
    public long receiverTargetQuantity(BigDecimal rate, int leadTimeDays, int receiverTargetCoverageDays,
            int receiverDisplayMinimum) {
        if (leadTimeDays < 0 || receiverTargetCoverageDays < 0 || receiverDisplayMinimum < 0) {
            throw new IllegalArgumentException(
                    "leadTimeDays, receiverTargetCoverageDays and receiverDisplayMinimum must not be negative.");
        }
        // Widened to long before adding: leadTimeDays and receiverTargetCoverageDays are each
        // individually in-range ints, but V6 does not cap either one tightly, so their plain
        // int sum could itself overflow before ever reaching ceilDemand.
        long totalDays = (long) leadTimeDays + receiverTargetCoverageDays;
        return ceilDemand(rate, totalDays) + receiverDisplayMinimum;
    }

    /** Per section 8's donor-protection formula, reused here for the section 6 OVERSTOCK check. */
    public long donorProtectedQuantity(BigDecimal highRate, int donorRetainedDays, int donorDisplayMinimum,
            int donorSafetyStock) {
        if (donorRetainedDays < 0 || donorDisplayMinimum < 0 || donorSafetyStock < 0) {
            throw new IllegalArgumentException(
                    "donorRetainedDays, donorDisplayMinimum and donorSafetyStock must not be negative.");
        }
        return ceilDemand(highRate, donorRetainedDays) + donorDisplayMinimum + donorSafetyStock;
    }

    private static long ceilDemand(BigDecimal rate, long days) {
        return rate.multiply(BigDecimal.valueOf(days)).setScale(0, RoundingMode.CEILING).longValueExact();
    }
}
