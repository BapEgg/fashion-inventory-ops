package com.bapegg.stockpilot.demand;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The before/after position a feasible manually requested transfer quantity would produce, per
 * {@code knowledge/business-rules.md} section 10's `MANUAL` quantity-test contract. Only ever
 * present on a {@code feasible} {@link ManualQuantityEvaluation} -- an infeasible request gets
 * only its automatic suggestion, never a hypothetical projection of the quantity that was
 * rejected.
 * <p>
 * Same shape and provenance guarantees as {@link TransferScenarioResult}'s before/after/coverage/
 * risk/evidence fields (both are built from {@link TransferEffectProjection}), minus the
 * scenario-specific fields ({@code scenarioType}/{@code demandRate}/{@code rawQuantity}/
 * {@code scenarioQuantity}/{@code feasible}/{@code warningSummary}) that
 * {@link ManualQuantityEvaluation} itself already carries under its own names.
 */
public record ManualQuantityProjection(
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
        int donorAlreadyApprovedDraftQuantity
) {
}
