package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.demand.DemandConfidence;
import com.bapegg.stockpilot.demand.DemandSignalType;
import com.bapegg.stockpilot.demand.InventoryExceptionType;
import com.bapegg.stockpilot.demand.InventorySeverity;
import com.bapegg.stockpilot.demand.MetricQualityFlag;
import com.bapegg.stockpilot.demand.TransferCandidateRejectionReason;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * One row of the MVP-2 run-bound inventory-exception review queue, per current-task.md
 * section 3. No candidate is chosen as a representative recommendation here -- see
 * {@code candidatesAsReceiver}/{@code candidatesAsDonor} on {@link Mvp2InventoryExceptionDetail}
 * for the actual per-candidate quantities.
 */
public record Mvp2InventoryExceptionListItem(
        Long inventoryMetricId,
        String storeId,
        String storeName,
        String region,
        String skuId,
        String productName,
        String category,
        String color,
        String sizeName,
        InventoryClassification classification,
        InventoryPriority priority,
        Integer availableQuantity,
        BigDecimal averageDailySales,
        BigDecimal coverageDays,
        InventoryExceptionType inventoryExceptionType,
        InventorySeverity severity,
        DemandSignalType primaryDemandSignalType,
        DemandConfidence demandConfidence,
        BigDecimal baseDemandRate,
        Integer projectedAvailable,
        Long expectedShortageQuantity,
        String calculationVersion,
        List<MetricQualityFlag> qualityFlags,
        Integer upcomingConfirmedInboundQuantity,
        OffsetDateTime nextConfirmedInboundAt,
        BigDecimal currentSellingPrice,
        BigDecimal estimatedSalesImpact,
        int executableCandidateCount,
        int comparisonOnlyCandidateCount,
        int rejectedCandidateCount,
        boolean hasExecutableCandidate,
        AllocatorWorkStatus workStatus,
        List<TransferCandidateRejectionReason> blockingReasons
) {
    public Mvp2InventoryExceptionListItem {
        blockingReasons = List.copyOf(blockingReasons);
    }
}
