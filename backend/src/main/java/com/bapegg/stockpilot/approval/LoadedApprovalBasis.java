package com.bapegg.stockpilot.approval;

import com.bapegg.stockpilot.demand.InventoryProjection;
import com.bapegg.stockpilot.demand.TransferRoute;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The pure, already-cross-validated inputs {@link CurrentApprovalBasisLoader#load} reads for one
 * recommendation, per {@code knowledge/business-rules.md} section 10. Carries only primitives
 * and pure {@code demand} types -- never a JPA entity -- so a caller ({@link
 * ApprovalTransactionExecutor} or {@code ManualQuantityTestExecutor}) can safely use it outside
 * the loader's own persistence-context boundary and feed it straight into
 * {@code demand.ApprovalBasisRecalculation}/{@code demand.ManualQuantityEvaluation} without
 * either use case repeating the loader's queries or cross-checks itself.
 */
public record LoadedApprovalBasis(
        String skuId,
        String receiverStoreId,
        String receiverOwnerCode,
        String donorStoreId,
        String donorOwnerCode,
        TransferRoute route,
        LocalDate analysisDate,
        InventoryProjection receiverProjection,
        BigDecimal receiverBaseRate,
        int receiverTargetCoverageDays,
        int receiverDisplayMinimum,
        int receiverMaximumCapacity,
        InventoryProjection donorProjection,
        BigDecimal donorBaseRate,
        BigDecimal donorHighRate,
        int donorRetainedDays,
        int donorDisplayMinimum,
        int donorSafetyStock,
        boolean receiverHasConfirmedInbound,
        boolean pendingTransferConflict
) {
}
