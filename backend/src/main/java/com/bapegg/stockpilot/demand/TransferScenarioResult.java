package com.bapegg.stockpilot.demand;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One scenario's sized transfer quantity plus both stores' before/after position, per
 * {@code knowledge/business-rules.md} section 8's closing sentence: "각 결과는 양쪽 매장의
 * 이동 전후 가용재고, 커버리지, 새 품절 위험... 경고를 함께 반환한다." and
 * {@code data-model.md}'s {@code SP_REBALANCE_SCENARIO} timing/basis columns.
 * <p>
 * When {@link #feasible()} is {@code false} (the floored quantity fell below the route's
 * minimum quantity), {@link #scenarioQuantity()} is {@code 0} per the document's "수량 0과
 * 제약 경고를 반환" rule -- {@link #rawQuantity()} still preserves the pre-floor value that
 * triggered the constraint, and {@link #warningSummary()} explains why using the actual
 * package-multiple-floored quantity that fell short, not the pre-floor raw one.
 * <p>
 * {@link #leadTimeDays()} and {@link #expectedArrivalDate()} are preserved explicitly rather
 * than being reconstructable only from the summed before/after available quantities.
 * {@code expectedArrivalDate} is a calendar date only: this pure-Java layer has no source of a
 * time-of-day or timezone, so it is not, by itself, {@code data-model.md}'s
 * {@code expected_arrival_at TIMESTAMP(6) WITH TIME ZONE}. A future persistence mapping must
 * combine this date with an explicitly chosen time-of-day/zone policy rather than defaulting to
 * midnight UTC (or any other zone) silently.
 * <p>
 * The six {@code receiver*}/{@code donor*} fields below are the same confirmed-inbound and
 * open-transfer inputs {@link InventoryProjection} retains, copied through explicitly so a
 * reader of a persisted result can see exactly which direction/quantity evidence produced the
 * before/after available totals -- not just an opaque "inbound was included" flag.
 * <p>
 * {@link #receiverRiskCode()}/{@link #donorRiskCode()} are a deliberately simplified two-value
 * indicator ({@code STOCKOUT_RISK}/{@code OVERSTOCK} vs. {@code NORMAL}) computed only from the
 * hypothetical after-transfer quantity against the same target/protection formulas used
 * elsewhere in this package -- not a full re-run of {@link DemandSignalClassification} or
 * {@link InventoryExceptionClassification}, since those need 28 days of fresh observation data
 * that does not exist for a "what if" post-transfer state.
 */
public record TransferScenarioResult(
        TransferScenarioType scenarioType,
        BigDecimal demandRate,
        long rawQuantity,
        long scenarioQuantity,
        boolean feasible,
        int receiverBeforeAvailable,
        int receiverAfterAvailable,
        BigDecimal receiverBeforeCoverageDays,
        BigDecimal receiverAfterCoverageDays,
        InventoryExceptionType receiverRiskCode,
        int donorBeforeAvailable,
        int donorAfterAvailable,
        BigDecimal donorBeforeCoverageDays,
        BigDecimal donorAfterCoverageDays,
        InventoryExceptionType donorRiskCode,
        int leadTimeDays,
        LocalDate expectedArrivalDate,
        int receiverInboundArrivingBeforeTransfer,
        int receiverOpenTransferInbound,
        int receiverOpenTransferOutbound,
        int donorInboundArrivingBeforeDispatch,
        int donorOpenTransferOutbound,
        int donorAlreadyApprovedDraftQuantity,
        String warningSummary
) {
}
