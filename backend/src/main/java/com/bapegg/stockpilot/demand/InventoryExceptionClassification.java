package com.bapegg.stockpilot.demand;

import java.math.BigDecimal;

/**
 * Pure deterministic {@code inventory_exception_type} and {@code severity} determination for
 * one store-SKU, per {@code knowledge/business-rules.md} sections 4, 6 and 9. Independent of
 * Spring/JPA.
 * <p>
 * Section 9's priority keys (severity, actionability, confidence, shortage, revenue impact,
 * tie-break) are a service-layer sort order across many metrics; only the severity value itself
 * ({@code CRITICAL}/{@code HIGH}/{@code REVIEW}, or absent) is decided here, per metric.
 */
public record InventoryExceptionClassification(
        InventoryExceptionType exceptionType,
        InventorySeverity severity
) {

    /**
     * @param confidence                  the store-SKU's already-decided
     *                                    {@link DemandSignalClassification#classify}
     *                                    confidence. Section 4 already folds every quality
     *                                    flag and the "no auto quantity" signal types
     *                                    ({@code DATA_INSUFFICIENT}, {@code UNEXPLAINED_SPIKE},
     *                                    {@code INTERMITTENT}, incomplete-uplift
     *                                    {@code KNOWN_EVENT}) into {@code NONE}/{@code LOW}, so
     *                                    checking confidence alone here (rather than
     *                                    re-deriving a partial list from the signal type) is
     *                                    both simpler and catches every quality-flagged case,
     *                                    e.g. an otherwise-{@code STABLE_REPEAT} metric that
     *                                    carries {@code OOS_CENSORED}.
     * @param earliestArrivalLeadTimeDays days until the earliest of this store-SKU's active
     *                                    inbound routes or confirmed inbound schedule -- the
     *                                    "가장 빠른 활성 경로 또는 확정 입고" timing section 6
     *                                    checks the {@code BASE} projection against
     * @param receiverTargetCoverageDays  from this store-SKU's policy (falls back to the demo
     *                                    default upstream, same as {@link PlanHorizon})
     * @param retainedDays                from the same policy row, used only for the donor-side
     *                                    OVERSTOCK check
     * @param displayMinimum              shared by both the receiver- and donor-side formulas,
     *                                    since it is a single policy value for this store-SKU
     * @param safetyStock                 used only for the donor-side OVERSTOCK check
     * @param hasActionableCandidate      whether at least one {@link TransferCandidateEvaluation}
     *                                    for this store-SKU is {@link TransferCandidateEvaluation#eligible()}.
     *                                    Section 7's candidate rejection rules are not
     *                                    re-implemented here -- the caller is expected to have
     *                                    already evaluated the relevant candidates and reduced
     *                                    them to this one fact. Only read when the metric is
     *                                    {@code STOCKOUT_RISK} but not already {@code CRITICAL}:
     *                                    section 9's {@code CRITICAL} (already-past-earliest-
     *                                    arrival stockout) fires regardless of candidate
     *                                    availability, matching the document's own precedence.
     */
    public static InventoryExceptionClassification classify(
            InventoryProjection projection,
            DemandConfidence confidence,
            DemandRateCalculation rates,
            int earliestArrivalLeadTimeDays,
            int receiverTargetCoverageDays,
            int retainedDays,
            int displayMinimum,
            int safetyStock,
            boolean hasActionableCandidate) {
        // Validated unconditionally, before any early return: these policy inputs are only
        // otherwise consumed by InventoryProjection's methods in the branches below, so a
        // negative value paired with an invalid projection or a low/none confidence would
        // previously reach NON_ACTIONABLE/REVIEW_REQUIRED without ever being checked.
        if (earliestArrivalLeadTimeDays < 0 || receiverTargetCoverageDays < 0 || retainedDays < 0
                || displayMinimum < 0 || safetyStock < 0) {
            throw new IllegalArgumentException("earliestArrivalLeadTimeDays, receiverTargetCoverageDays, "
                    + "retainedDays, displayMinimum and safetyStock must not be negative.");
        }

        if (projection.isInputInvalid()) {
            return new InventoryExceptionClassification(InventoryExceptionType.NON_ACTIONABLE, null);
        }

        boolean cannotAutoQuantify = confidence == DemandConfidence.NONE
                || confidence == DemandConfidence.LOW
                || rates.reviewRequired();
        if (cannotAutoQuantify) {
            return new InventoryExceptionClassification(InventoryExceptionType.REVIEW_REQUIRED, InventorySeverity.REVIEW);
        }

        BigDecimal baseRate = rates.baseDemandRate();
        long atEarliestArrival = projection.receiverAtArrivalWithoutNewTransfer(baseRate, earliestArrivalLeadTimeDays);
        if (atEarliestArrival <= 0) {
            // Section 9's CRITICAL: BASE projected stock is already non-positive at (or before)
            // the earliest possible arrival, regardless of whether an actionable candidate
            // exists to fix it.
            return new InventoryExceptionClassification(InventoryExceptionType.STOCKOUT_RISK, InventorySeverity.CRITICAL);
        }

        long targetQuantity = projection.receiverTargetQuantity(
                baseRate, earliestArrivalLeadTimeDays, receiverTargetCoverageDays, displayMinimum);
        boolean shortOfTargetCoverage = projection.projectedReceiverBeforeDemand() < targetQuantity;
        if (shortOfTargetCoverage) {
            // Section 9's HIGH requires an actionable candidate; without one this stays
            // STOCKOUT_RISK but with an undetermined (null) severity, per the same table.
            InventorySeverity severity = hasActionableCandidate ? InventorySeverity.HIGH : null;
            return new InventoryExceptionClassification(InventoryExceptionType.STOCKOUT_RISK, severity);
        }

        BigDecimal highRate = rates.highDemandRate();
        long donorProtected = projection.donorProtectedQuantity(highRate, retainedDays, displayMinimum, safetyStock);
        if (projection.projectedDonorAtDispatch() - donorProtected > 0) {
            return new InventoryExceptionClassification(InventoryExceptionType.OVERSTOCK, null);
        }

        return new InventoryExceptionClassification(InventoryExceptionType.NORMAL, null);
    }
}
