package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.demand.DemandObservationStatistics;
import com.bapegg.stockpilot.demand.DemandRateCalculation;
import com.bapegg.stockpilot.demand.DemandSignalClassification;
import com.bapegg.stockpilot.demand.InventoryExceptionClassification;
import com.bapegg.stockpilot.demand.InventoryProjection;
import com.bapegg.stockpilot.demand.MetricQualityFlag;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * One anchor's complete in-memory calculation result, per {@code data-model.md}'s Phase 3
 * mapping. {@code projection}/{@code exception} are the *final* values (after
 * {@link Mvp2CalculationOrchestrator}'s step 6 re-classification against real candidate
 * eligibility) -- the provisional {@code hasActionableCandidate=false} classification from step 3
 * is not separately exposed, since nothing downstream needs it once the final value exists.
 * <p>
 * {@code expectedShortageQuantity} is {@code max(BASE target - projectedReceiverBeforeDemand, 0)}
 * from the raw (non-effective) BASE rate, {@code earliestArrivalLeadTimeDays} and the anchor's own
 * policy -- null if BASE is null or {@code projection.isInputInvalid()}. Persistence must store
 * this value as-is, never recompute it.
 */
public record Mvp2MetricResult(
        String storeId,
        String skuId,
        DemandObservationStatistics stats,
        DemandSignalClassification signal,
        DemandRateCalculation rates,
        InventoryProjection projection,
        int earliestArrivalLeadTimeDays,
        InventoryExceptionClassification exception,
        Long expectedShortageQuantity,
        Set<MetricQualityFlag> qualityFlags,
        String calculationVersion
) {

    public Mvp2MetricResult {
        // EnumSet, not Set.copyOf: EnumSet always iterates in the enum's declaration order,
        // matching the stable ordering callers (and Oracle persistence, later) rely on --
        // Set.copyOf gives no iteration-order guarantee at all.
        qualityFlags = qualityFlags.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(MetricQualityFlag.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(qualityFlags));
    }
}
