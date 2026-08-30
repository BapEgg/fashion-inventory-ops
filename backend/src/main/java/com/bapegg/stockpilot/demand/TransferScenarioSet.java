package com.bapegg.stockpilot.demand;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Pure deterministic {@code NO_ACTION}/{@code CONSERVATIVE}/{@code BASE}/{@code AGGRESSIVE}
 * scenario calculation for one donor-receiver-route combination, per
 * {@code knowledge/business-rules.md} section 8. Independent of Spring/JPA.
 * <p>
 * {@link #comparisonOnly()} mirrors section 6: a {@code VARIABLE} primary signal may compare
 * these four scenarios but must not have any one of them treated as a default recommendation.
 * This class still computes all four results for a {@code VARIABLE} metric -- it only carries
 * the flag a caller must check before presenting or auto-selecting one.
 * <p>
 * Every other signal/confidence combination that section 6 says must not receive an automatic
 * quantity ({@code DATA_INSUFFICIENT}, {@code UNEXPLAINED_SPIKE}, {@code INTERMITTENT}, an
 * incomplete-uplift {@code KNOWN_EVENT}, or -- per the same lesson learned on
 * {@link InventoryExceptionClassification} -- any other quality-flagged metric whose confidence
 * is {@code NONE}/{@code LOW}) is rejected outright rather than silently computed, since a
 * caller that reaches this method is expected to have already routed those to
 * {@code REVIEW_REQUIRED}.
 * <p>
 * {@code NO_ACTION}'s own transfer quantity is always {@code 0} -- but that is a statement about
 * the recommended quantity only, not about demand: its before/after coverage and risk still use
 * the real (event-uplifted, when applicable) {@code BASE} rate, the same as if no action were
 * taken while demand continued normally.
 */
public record TransferScenarioSet(
        List<TransferScenarioResult> scenarios,
        boolean comparisonOnly
) {

    public TransferScenarioSet {
        scenarios = Collections.unmodifiableList(List.copyOf(scenarios));
    }

    /**
     * @param confidence         the store-SKU's already-decided
     *                           {@link DemandSignalClassification#classify} confidence. Checked
     *                           directly (rather than re-deriving a signal-type list) so every
     *                           quality-flagged case is caught, matching
     *                           {@link InventoryExceptionClassification}; {@code VARIABLE} is
     *                           exempt since its whole purpose is showing comparison scenarios
     *                           even when confidence is not {@code HIGH}.
     * @param analysisDate       this analysis run's reference date, used with the route's lead
     *                           time to compute each scenario's arrival date and, for
     *                           {@code KNOWN_EVENT}, its arrival-through-target-coverage window
     * @param relevantEvent      the classifier's relevant {@code KNOWN_EVENT} event, or
     *                           {@code null} when the signal is not {@code KNOWN_EVENT} or no
     *                           event applies; uplift is applied per auto scenario (including
     *                           {@code NO_ACTION}'s own risk/coverage rate) only when this event
     *                           has complete uplift and overlaps that scenario's own
     *                           arrival-through-target-coverage window
     * @param rates              the receiver's already-computed low/base/high demand rates;
     *                           must not be {@link DemandRateCalculation#reviewRequired()} --
     *                           the caller is expected to have already routed a
     *                           non-auto-quantifiable signal to {@code REVIEW_REQUIRED} before
     *                           ever reaching scenario sizing
     * @param receiverProjection the receiver's section 6 projection; must not be
     *                           {@link InventoryProjection#isInputInvalid()}. Its confirmed-
     *                           inbound/open-transfer fields are copied onto every result as
     *                           explicit evidence.
     * @param receiverMaximumCapacity the receiver's policy maximum capacity; must be positive,
     *                           matching V6's {@code ck_sp_policy_values} (`maximum_capacity >
     *                           0`) -- a non-positive value is a data error, not a legitimate
     *                           "no room" business state, and must not silently produce a
     *                           zero-quantity result indistinguishable from a real
     *                           {@code NO_ACTION}
     * @param donorProjection    the donor's section 6 projection; must not be
     *                           {@link InventoryProjection#isInputInvalid()}. Its confirmed-
     *                           inbound/open-transfer/draft fields are copied onto every result
     *                           as explicit evidence.
     * @param donorRates         the donor's own low/base/high demand rates: {@code HIGH}
     *                           protects the donor per section 8 regardless of which
     *                           receiver-side scenario is being sized; {@code BASE} is used
     *                           only for the donor's own reported coverage days
     * @param route              the specific active route this shipment would use; required,
     *                           since a scenario cannot be sized without lead time/minimum/
     *                           package/maximum, and must be {@link TransferRoute#active()}
     */
    public static TransferScenarioSet calculate(
            DemandSignalType signalType,
            DemandConfidence confidence,
            DemandRateCalculation rates,
            LocalDate analysisDate,
            DemandEvent relevantEvent,
            InventoryProjection receiverProjection,
            int receiverTargetCoverageDays,
            int receiverDisplayMinimum,
            int receiverMaximumCapacity,
            InventoryProjection donorProjection,
            DemandRateCalculation donorRates,
            int donorRetainedDays,
            int donorDisplayMinimum,
            int donorSafetyStock,
            TransferRoute route) {
        if (route == null) {
            throw new IllegalArgumentException("route must not be null.");
        }
        if (!route.active()) {
            throw new IllegalArgumentException("route must be active.");
        }
        if (receiverMaximumCapacity <= 0) {
            throw new IllegalArgumentException("receiverMaximumCapacity must be positive.");
        }
        if (rates.reviewRequired()) {
            throw new IllegalStateException(
                    "Scenario quantities require a computed low/base/high demand rate, not a reviewRequired() rate calculation.");
        }
        if (donorRates.reviewRequired()) {
            throw new IllegalStateException("Scenario quantities require the donor's computed low/base/high demand rate too.");
        }
        if (receiverProjection.isInputInvalid() || donorProjection.isInputInvalid()) {
            throw new IllegalStateException(
                    "Scenario quantities require a valid (non-NON_ACTIONABLE) projection for both stores.");
        }
        boolean cannotAutoQuantify = (confidence == DemandConfidence.NONE || confidence == DemandConfidence.LOW)
                && signalType != DemandSignalType.VARIABLE;
        if (cannotAutoQuantify) {
            throw new IllegalStateException("Scenario quantities require an auto-quantifiable signal: signalType="
                    + signalType + ", confidence=" + confidence + " must not receive automatic scenarios.");
        }

        LocalDate arrivalDate = analysisDate.plusDays(route.leadTimeDays());
        LocalDate targetCoverageEnd = arrivalDate.plusDays(receiverTargetCoverageDays);
        Optional<DemandEvent.UpliftFactors> uplift = signalType == DemandSignalType.KNOWN_EVENT && relevantEvent != null
                ? relevantEvent.upliftFor(arrivalDate, targetCoverageEnd)
                : Optional.empty();

        BigDecimal donorBaseRate = donorRates.baseDemandRate();
        long donorProtected = donorProjection.donorProtectedQuantity(
                donorRates.highDemandRate(), donorRetainedDays, donorDisplayMinimum, donorSafetyStock);
        long donorTransferableQuantity = Math.max(donorProjection.projectedDonorAtDispatch() - donorProtected, 0);
        long receiverCapacityRemaining =
                Math.max(receiverMaximumCapacity - receiverProjection.projectedReceiverBeforeDemand(), 0);

        Sizing common = new Sizing(receiverProjection, route, arrivalDate, receiverTargetCoverageDays,
                receiverDisplayMinimum, donorProjection, donorBaseRate, donorProtected,
                donorTransferableQuantity, receiverCapacityRemaining);

        // NO_ACTION never changes either store's available quantity, but its before/after
        // coverage and risk still need a real demand rate -- the same event-uplifted BASE rate
        // the BASE auto scenario itself uses, not a literal 0 standing in for "no demand".
        BigDecimal noActionRate = uplift.map(factors -> DemandRateCalculation.applyUplift(rates.baseDemandRate(), factors.base()))
                .orElse(rates.baseDemandRate());

        List<TransferScenarioResult> scenarios = List.of(
                common.noAction(noActionRate),
                common.autoScenario(TransferScenarioType.CONSERVATIVE, rates.lowDemandRate(),
                        uplift.map(DemandEvent.UpliftFactors::low)),
                common.autoScenario(TransferScenarioType.BASE, rates.baseDemandRate(),
                        uplift.map(DemandEvent.UpliftFactors::base)),
                common.autoScenario(TransferScenarioType.AGGRESSIVE, rates.highDemandRate(),
                        uplift.map(DemandEvent.UpliftFactors::high)));

        return new TransferScenarioSet(scenarios, signalType == DemandSignalType.VARIABLE);
    }

    /** Bundles the values every scenario in one set shares, so each sizing call needs no long parameter list. */
    private record Sizing(
            InventoryProjection receiverProjection,
            TransferRoute route,
            LocalDate arrivalDate,
            int receiverTargetCoverageDays,
            int receiverDisplayMinimum,
            InventoryProjection donorProjection,
            BigDecimal donorBaseRate,
            long donorProtected,
            long donorTransferableQuantity,
            long receiverCapacityRemaining
    ) {

        TransferScenarioResult noAction(BigDecimal riskRate) {
            return build(TransferScenarioType.NO_ACTION, riskRate, 0, 0, true, null);
        }

        TransferScenarioResult autoScenario(TransferScenarioType type, BigDecimal baseRate,
                Optional<BigDecimal> upliftFactor) {
            // Per section 5: multiply then immediately re-fix at scale 12 HALF_UP -- never reuse
            // a 2-decimal display rounding as an input to this multiplication.
            BigDecimal rate = upliftFactor.map(factor -> DemandRateCalculation.applyUplift(baseRate, factor))
                    .orElse(baseRate);

            long targetQuantity = receiverProjection.receiverTargetQuantity(
                    rate, route.leadTimeDays(), receiverTargetCoverageDays, receiverDisplayMinimum);
            long receiverNeed = Math.max(targetQuantity - receiverProjection.projectedReceiverBeforeDemand(), 0);
            long rawQuantity = Math.min(
                    Math.min(receiverNeed, donorTransferableQuantity),
                    Math.min((long) route.maximumQuantity(), receiverCapacityRemaining));
            long flooredQuantity = (rawQuantity / route.packageMultiple()) * route.packageMultiple();
            boolean feasible = flooredQuantity >= route.minimumQuantity();
            long scenarioQuantity = feasible ? flooredQuantity : 0;

            String warningSummary = feasible
                    ? null
                    : "flooredQuantity " + flooredQuantity + " (from rawQuantity " + rawQuantity
                            + ", package multiple " + route.packageMultiple()
                            + ") is below the route's minimum quantity " + route.minimumQuantity()
                            + "; scenarioQuantity forced to 0.";
            return build(type, rate, rawQuantity, scenarioQuantity, feasible, warningSummary);
        }

        private TransferScenarioResult build(
                TransferScenarioType type, BigDecimal rate, long rawQuantity, long scenarioQuantity,
                boolean feasible, String warningSummary) {
            TransferEffectProjection effect = TransferEffectProjection.calculate(
                    receiverProjection, rate, route.leadTimeDays(), receiverTargetCoverageDays, receiverDisplayMinimum,
                    donorProjection, donorBaseRate, donorProtected, scenarioQuantity);

            return new TransferScenarioResult(
                    type, rate, rawQuantity, scenarioQuantity, feasible,
                    effect.receiverBeforeAvailable(), effect.receiverAfterAvailable(),
                    effect.receiverBeforeCoverageDays(), effect.receiverAfterCoverageDays(),
                    effect.receiverRiskCode(),
                    effect.donorBeforeAvailable(), effect.donorAfterAvailable(),
                    effect.donorBeforeCoverageDays(), effect.donorAfterCoverageDays(),
                    effect.donorRiskCode(),
                    route.leadTimeDays(), arrivalDate,
                    receiverProjection.inboundArrivingBeforeTransfer(), receiverProjection.openTransferInbound(),
                    receiverProjection.openTransferOutbound(),
                    donorProjection.inboundArrivingBeforeDispatch(), donorProjection.openTransferOutbound(),
                    donorProjection.alreadyApprovedDraftQuantity(),
                    warningSummary);
        }
    }
}
