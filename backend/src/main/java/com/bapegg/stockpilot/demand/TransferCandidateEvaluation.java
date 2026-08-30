package com.bapegg.stockpilot.demand;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Pure deterministic donor-receiver transfer candidate evaluation for one store-SKU pair, per
 * {@code knowledge/business-rules.md} section 7. Independent of Spring/JPA. A single
 * {@code skuId} parameter represents section 7 condition 1's "same SKU" by construction (there
 * is only one SKU to pass); {@code receiverStoreId}/{@code donorStoreId} are validated to be
 * different stores.
 * <p>
 * Every applicable rejection reason is collected, not just a representative one -- section 7:
 * "탈락한 모든 조건을 저장하거나 응답한다." {@link #representativeReason()} picks the
 * first-declared {@link TransferCandidateRejectionReason} present, for sorting only; it never
 * discards the rest. {@link #reasons()} is unmodifiable so a caller cannot mutate eligibility
 * or the representative reason after construction.
 * <p>
 * {@code DISPLAY_MINIMUM_VIOLATION} is this increment's interpretation of section 7 condition 7
 * ("최소 이동수량, 포장 배수, 경로 최대수량과 도착 매장 최대 수용량을 만족한다"): whether the
 * largest shipment the route/capacity/supply allow, floored to the route's package multiple,
 * still clears the route's minimum quantity. The business-rules.md excerpt available to this
 * implementation does not further disambiguate this code's exact name/scope beyond the
 * condition text itself; this mapping is confirmed accepted per current-task.md.
 */
public record TransferCandidateEvaluation(Set<TransferCandidateRejectionReason> reasons) {

    public TransferCandidateEvaluation {
        reasons = reasons.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(TransferCandidateRejectionReason.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(reasons));
    }

    public boolean eligible() {
        return reasons.isEmpty();
    }

    /** The first reason in section 7's declared priority order, if any; the rest are preserved in {@link #reasons()}. */
    public Optional<TransferCandidateRejectionReason> representativeReason() {
        return reasons.stream().findFirst();
    }

    /**
     * @param skuId                                 the shared SKU for both sides (section 7
     *                                               condition 1's "same SKU" is represented by
     *                                               there being exactly one SKU parameter)
     * @param receiverStoreId                       must differ from {@code donorStoreId}
     *                                               (section 7 condition 1)
     * @param donorStoreId                           must differ from {@code receiverStoreId}
     * @param route                                  the active-or-not route row between these
     *                                               two stores, or {@code null} when no such row
     *                                               exists at all (in which case
     *                                               lead-time/minimum/capacity cannot be
     *                                               evaluated and are simply not checked)
     * @param receiverProjection                     the receiver's section 6 projection
     * @param receiverBaseRate                       the receiver's BASE demand rate
     * @param receiverMaximumCapacity                the receiver's policy maximum capacity
     * @param donorProjection                        the donor's section 6 projection
     * @param donorHighRate                           the donor's HIGH demand rate (protects the
     *                                               donor per section 8)
     * @param confirmedInboundAlreadyCoversShortage  already computed elsewhere (order item 4):
     *                                               true when the receiver's own confirmed
     *                                               inbound already resolves its shortage
     * @param pendingTransferConflict                true when an open, not-yet-settled transfer
     *                                               already exists for this exact donor-receiver-
     *                                               SKU lane
     */
    public static TransferCandidateEvaluation evaluate(
            String skuId,
            String receiverStoreId,
            String receiverOwnerCode,
            String donorStoreId,
            String donorOwnerCode,
            TransferRoute route,
            InventoryProjection receiverProjection,
            BigDecimal receiverBaseRate,
            int receiverMaximumCapacity,
            InventoryProjection donorProjection,
            BigDecimal donorHighRate,
            int donorRetainedDays,
            int donorDisplayMinimum,
            int donorSafetyStock,
            boolean confirmedInboundAlreadyCoversShortage,
            boolean pendingTransferConflict) {
        if (skuId == null || skuId.isBlank()) {
            throw new IllegalArgumentException("skuId must not be blank.");
        }
        if (receiverStoreId == null || receiverStoreId.isBlank()
                || donorStoreId == null || donorStoreId.isBlank()) {
            throw new IllegalArgumentException("receiverStoreId and donorStoreId must not be blank.");
        }
        if (receiverStoreId.equals(donorStoreId)) {
            throw new IllegalArgumentException("receiverStoreId and donorStoreId must be different stores.");
        }

        EnumSet<TransferCandidateRejectionReason> reasons = EnumSet.noneOf(TransferCandidateRejectionReason.class);

        boolean ownerMismatch = !receiverOwnerCode.equals(donorOwnerCode) && (route == null || !route.ownerOverride());
        if (ownerMismatch) {
            reasons.add(TransferCandidateRejectionReason.OWNER_MISMATCH);
        }

        boolean routeAllowed = route != null && route.active();
        if (!routeAllowed) {
            reasons.add(TransferCandidateRejectionReason.ROUTE_NOT_ALLOWED);
        }

        long donorProtected = donorProjection.donorProtectedQuantity(
                donorHighRate, donorRetainedDays, donorDisplayMinimum, donorSafetyStock);
        long donorTransferable = donorProjection.projectedDonorAtDispatch() - donorProtected;
        if (donorTransferable <= 0) {
            reasons.add(TransferCandidateRejectionReason.NO_TRANSFERABLE_STOCK);
        }

        if (route != null) {
            // Section 7 condition 6: arrival must not be LATER than the expected stockout day,
            // i.e. arrival <= stockout is fine. atArrival == 0 means stock hits zero exactly on
            // the arrival day (arriving just in time), so only a strictly negative value -- the
            // stockout already happened before arrival -- is too late.
            long atArrival = receiverProjection.receiverAtArrivalWithoutNewTransfer(receiverBaseRate, route.leadTimeDays());
            if (atArrival < 0) {
                reasons.add(TransferCandidateRejectionReason.LEAD_TIME_TOO_LONG);
            }

            long receiverCapacityRemaining = receiverMaximumCapacity - receiverProjection.projectedReceiverBeforeDemand();
            if (receiverCapacityRemaining <= 0) {
                reasons.add(TransferCandidateRejectionReason.CAPACITY_EXCEEDED);
            }

            long maxShippable = Math.min(
                    Math.max(donorTransferable, 0),
                    Math.min((long) route.maximumQuantity(), Math.max(receiverCapacityRemaining, 0)));
            long flooredMaxShippable = (maxShippable / route.packageMultiple()) * route.packageMultiple();
            if (flooredMaxShippable < route.minimumQuantity()) {
                reasons.add(TransferCandidateRejectionReason.DISPLAY_MINIMUM_VIOLATION);
            }
        }

        if (confirmedInboundAlreadyCoversShortage) {
            reasons.add(TransferCandidateRejectionReason.INBOUND_ALREADY_COVERS);
        }

        if (pendingTransferConflict) {
            reasons.add(TransferCandidateRejectionReason.PENDING_TRANSFER_CONFLICT);
        }

        return new TransferCandidateEvaluation(reasons);
    }
}
