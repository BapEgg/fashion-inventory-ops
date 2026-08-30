package com.bapegg.stockpilot.demand;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure deterministic before/after available quantity, coverage and risk for one hypothetical
 * transfer quantity, shared by {@link TransferScenarioSet} (one of four auto-sized scenario
 * quantities) and {@link ManualQuantityEvaluation} (an arbitrary user-requested quantity), per
 * {@code knowledge/business-rules.md} section 8's closing sentence: "각 결과는 양쪽 매장의 이동
 * 전후 가용재고, 커버리지, 새 품절 위험... 경고를 함께 반환한다." Neither caller re-derives this
 * formula itself -- both feed their own quantity into {@link #calculate}.
 * <p>
 * {@link #receiverRiskCode()}/{@link #donorRiskCode()} are the same deliberately simplified
 * two-value indicator ({@code STOCKOUT_RISK}/{@code OVERSTOCK} vs. {@code NORMAL}) documented on
 * {@link TransferScenarioResult}, computed only from the hypothetical after-transfer quantity
 * against the target/protection formulas used elsewhere in this package.
 */
public record TransferEffectProjection(
        int receiverBeforeAvailable,
        int receiverAfterAvailable,
        BigDecimal receiverBeforeCoverageDays,
        BigDecimal receiverAfterCoverageDays,
        InventoryExceptionType receiverRiskCode,
        int donorBeforeAvailable,
        int donorAfterAvailable,
        BigDecimal donorBeforeCoverageDays,
        BigDecimal donorAfterCoverageDays,
        InventoryExceptionType donorRiskCode
) {

    /**
     * @param receiverRate the rate used for both the receiver's coverage-days display and its
     *                     stockout-risk target -- callers pass whichever rate their own contract
     *                     specifies (an event-uplifted scenario rate, or the receiver's plain
     *                     current BASE rate for a manual test)
     * @param donorRate    the rate used only for the donor's coverage-days display; donor
     *                     protection itself is a separate, already-computed {@code donorProtected}
     *                     input, not re-derived here
     * @param quantity     the hypothetical transfer quantity: added to the receiver's projected
     *                     before-demand available, subtracted from the donor's projected
     *                     at-dispatch available
     */
    public static TransferEffectProjection calculate(
            InventoryProjection receiverProjection,
            BigDecimal receiverRate,
            int leadTimeDays,
            int receiverTargetCoverageDays,
            int receiverDisplayMinimum,
            InventoryProjection donorProjection,
            BigDecimal donorRate,
            long donorProtected,
            long quantity) {
        int receiverBeforeAvailable = receiverProjection.projectedReceiverBeforeDemand();
        int receiverAfterAvailable = Math.toIntExact(receiverBeforeAvailable + quantity);
        int donorBeforeAvailable = donorProjection.projectedDonorAtDispatch();
        int donorAfterAvailable = Math.toIntExact(donorBeforeAvailable - quantity);

        long targetForRisk = receiverProjection.receiverTargetQuantity(
                receiverRate, leadTimeDays, receiverTargetCoverageDays, receiverDisplayMinimum);
        InventoryExceptionType receiverRiskCode = receiverAfterAvailable < targetForRisk
                ? InventoryExceptionType.STOCKOUT_RISK
                : InventoryExceptionType.NORMAL;
        InventoryExceptionType donorRiskCode = donorAfterAvailable - donorProtected > 0
                ? InventoryExceptionType.OVERSTOCK
                : InventoryExceptionType.NORMAL;

        return new TransferEffectProjection(
                receiverBeforeAvailable, receiverAfterAvailable,
                coverageDays(receiverBeforeAvailable, receiverRate), coverageDays(receiverAfterAvailable, receiverRate),
                receiverRiskCode,
                donorBeforeAvailable, donorAfterAvailable,
                coverageDays(donorBeforeAvailable, donorRate), coverageDays(donorAfterAvailable, donorRate),
                donorRiskCode);
    }

    private static BigDecimal coverageDays(int availableQuantity, BigDecimal rate) {
        if (rate == null || rate.signum() <= 0) {
            return null;
        }
        return BigDecimal.valueOf(availableQuantity).divide(rate, DemandAnalysisRules.RATE_SCALE, RoundingMode.HALF_UP);
    }
}
