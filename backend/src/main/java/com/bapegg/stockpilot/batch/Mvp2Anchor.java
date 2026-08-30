package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.demand.DemandObservationWindow;

import java.time.OffsetDateTime;

/**
 * One store-SKU with a complete, contract-valid input evidence set for one Batch run: the
 * analysis-date snapshot position, a full 28-day {@link DemandObservationWindow}, the store's
 * owner code (for candidate owner-match checks) and its effective policy. Every anchor returned
 * inside an {@link Mvp2InputGraph} has already passed the input-contract checks in
 * {@code data-model.md}'s Phase 3 mapping -- there is no "incomplete anchor" representation.
 */
public record Mvp2Anchor(
        String storeId,
        String skuId,
        String storeOwnerCode,
        int currentOnHandQuantity,
        int currentReservedQuantity,
        OffsetDateTime currentSnapshotAt,
        boolean currentOutOfStock,
        DemandObservationWindow observationWindow,
        Mvp2Policy policy
) {

    public int currentAvailable() {
        return currentOnHandQuantity - currentReservedQuantity;
    }
}
