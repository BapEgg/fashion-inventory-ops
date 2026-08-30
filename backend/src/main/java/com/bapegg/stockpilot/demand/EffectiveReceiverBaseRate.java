package com.bapegg.stockpilot.demand;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The route-specific effective receiver BASE rate, per {@code knowledge/business-rules.md}
 * section 10's shared current-basis contract: "대표 KNOWN_EVENT가 해당 route의 도착일~목표
 * 커버리지 종료일과 겹치면 baseline BASE에 같은 uplift BASE를 scale 12 HALF_UP으로 적용한다."
 * Shared by Batch candidate recommendation, the approval transaction and `MANUAL` preview so
 * none of the three can silently disagree about which BASE rate a given route currently implies.
 * <p>
 * {@link TransferScenarioSet} does not use this class -- it already applies the same
 * per-scenario uplift internally (independently for {@code CONSERVATIVE}/{@code BASE}/
 * {@code AGGRESSIVE}, using the caller's raw {@code signalType}/{@code relevantEvent}/
 * {@code rates}). This class exists for the narrower callers that need only the single
 * uplifted BASE rate as a plain input, such as {@link ApprovalBasisRecalculation}.
 */
public final class EffectiveReceiverBaseRate {

    private EffectiveReceiverBaseRate() {
    }

    /**
     * @param signalType    the receiver store-SKU's already-decided primary signal; the uplift
     *                      only ever applies when this is {@code KNOWN_EVENT}
     * @param relevantEvent the classifier's representative event, or {@code null}
     * @param route         the specific route whose lead time decides the arrival date this
     *                      uplift window is anchored to
     */
    public static BigDecimal calculate(
            BigDecimal baselineBaseRate,
            DemandSignalType signalType,
            DemandEvent relevantEvent,
            LocalDate analysisDate,
            TransferRoute route,
            int receiverTargetCoverageDays) {
        if (signalType != DemandSignalType.KNOWN_EVENT || relevantEvent == null) {
            return baselineBaseRate;
        }
        LocalDate arrivalDate = analysisDate.plusDays(route.leadTimeDays());
        LocalDate targetCoverageEnd = arrivalDate.plusDays(receiverTargetCoverageDays);
        return relevantEvent.upliftFor(arrivalDate, targetCoverageEnd)
                .map(factors -> DemandRateCalculation.applyUplift(baselineBaseRate, factors.base()))
                .orElse(baselineBaseRate);
    }
}
