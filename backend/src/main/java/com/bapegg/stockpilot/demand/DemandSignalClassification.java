package com.bapegg.stockpilot.demand;

import java.time.LocalDate;
import java.util.List;

/**
 * Pure deterministic {@code primary_demand_signal_type} and {@code demand_confidence}
 * classification for one store-SKU, per {@code knowledge/business-rules.md} sections 3-4.
 * Independent of Spring/JPA. Reads {@link DemandObservationStatistics} (sections 2-3's
 * observation statistics) and {@link DemandEvent} inputs; does not compute either itself.
 * <p>
 * Only the {@code OOS_CENSORED} and {@code INCOMPLETE_EVENT_DATA} quality flags are
 * evaluated here. {@code STALE_INVENTORY} (current-snapshot freshness) and
 * {@code MISSING_INBOUND} (inbound schedule completeness) need inputs this class does not yet
 * take and are deliberately out of scope; a future increment that adds them must fold their
 * effect into the same "any quality flag downgrades confidence to LOW" rule used below.
 */
public record DemandSignalClassification(
        DemandSignalType signalType,
        DemandConfidence confidence,
        DemandEvent relevantEvent,
        boolean incompleteEventData
) {

    public static DemandSignalClassification classify(
            String storeId,
            String skuId,
            DemandObservationStatistics stats,
            PlanHorizon planHorizon,
            List<DemandEvent> events) {
        // Relevant-event lookup and the INCOMPLETE_EVENT_DATA flag are computed unconditionally,
        // before the DATA_INSUFFICIENT check below: quality flags are stored independently of
        // the single primary signal (section 3), so a DATA_INSUFFICIENT row must still carry
        // whatever relevant-event evidence exists rather than silently discarding it.
        LocalDate analysisDate = planHorizon.analysisDate();
        LocalDate observationStart = analysisDate.minusDays(DemandAnalysisRules.OBSERVATION_WINDOW_DAYS);
        LocalDate observationEnd = analysisDate.minusDays(1);

        List<DemandEvent> relevantEvents = RepresentativeEventSelection.selectRelevant(
                storeId, skuId, observationStart, observationEnd, analysisDate, planHorizon.endDate(), events);
        boolean hasKnownEvent = !relevantEvents.isEmpty();
        boolean incompleteEventData = relevantEvents.stream().anyMatch(event -> !event.hasCompleteUplift());
        DemandEvent relevantEvent = RepresentativeEventSelection.representative(relevantEvents).orElse(null);

        if (stats.launchDaysElapsed() < DemandAnalysisRules.MINIMUM_LAUNCH_DAYS
                || stats.observableDayCount() < DemandAnalysisRules.MINIMUM_OBSERVABLE_DAYS) {
            return new DemandSignalClassification(
                    DemandSignalType.DATA_INSUFFICIENT, DemandConfidence.NONE, relevantEvent, incompleteEventData);
        }

        DemandSignalType signalType;
        if (hasKnownEvent) {
            signalType = DemandSignalType.KNOWN_EVENT;
        } else if (stats.spikeCandidate()) {
            signalType = DemandSignalType.UNEXPLAINED_SPIKE;
        } else if (stats.activeWeekCount() <= DemandAnalysisRules.INTERMITTENT_MAXIMUM_ACTIVE_WEEKS
                || stats.salesDayRatio().compareTo(DemandAnalysisRules.INTERMITTENT_MAXIMUM_SALES_DAY_RATIO) < 0) {
            signalType = DemandSignalType.INTERMITTENT;
        } else if (stats.activeWeekCount() >= DemandAnalysisRules.STABLE_REPEAT_MINIMUM_ACTIVE_WEEKS
                && stats.weeklyCoefficientOfVariation() != null
                && stats.weeklyCoefficientOfVariation().compareTo(DemandAnalysisRules.STABLE_REPEAT_MAX_WEEKLY_CV) <= 0) {
            signalType = DemandSignalType.STABLE_REPEAT;
        } else {
            signalType = DemandSignalType.VARIABLE;
        }

        boolean hasAnyQualityFlag = stats.oosCensored() || incompleteEventData;
        DemandConfidence confidence = switch (signalType) {
            case DATA_INSUFFICIENT -> DemandConfidence.NONE;
            case UNEXPLAINED_SPIKE, INTERMITTENT -> DemandConfidence.LOW;
            default -> hasAnyQualityFlag
                    ? DemandConfidence.LOW
                    : (signalType == DemandSignalType.STABLE_REPEAT ? DemandConfidence.HIGH : DemandConfidence.MEDIUM);
        };

        return new DemandSignalClassification(signalType, confidence, relevantEvent, incompleteEventData);
    }
}
