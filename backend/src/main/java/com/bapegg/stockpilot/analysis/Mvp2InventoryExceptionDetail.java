package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.demand.DemandConfidence;
import com.bapegg.stockpilot.demand.DemandSignalType;
import com.bapegg.stockpilot.demand.InventoryExceptionType;
import com.bapegg.stockpilot.demand.InventorySeverity;
import com.bapegg.stockpilot.demand.MetricQualityFlag;
import com.bapegg.stockpilot.demand.TransferCandidateRejectionReason;
import com.bapegg.stockpilot.demand.TransferScenarioType;
import com.bapegg.stockpilot.rebalance.CandidateStatus;
import com.bapegg.stockpilot.rebalance.DecisionStatus;
import com.bapegg.stockpilot.rebalance.DemandEventType;
import com.bapegg.stockpilot.rebalance.InboundStatus;
import com.bapegg.stockpilot.rebalance.OpenTransferStatus;
import com.bapegg.stockpilot.rebalance.RecommendationMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The MVP-2 store-SKU exception detail: 28-day observation evidence, donor candidates with
 * their rejection reasons and automatic scenarios, and the applied rule thresholds, per
 * current-task.md section 4. Every array field is always non-null.
 */
public record Mvp2InventoryExceptionDetail(
        RunSummary run,
        StoreSummary store,
        ProductSummary product,
        AssumptionNotice assumption,
        MetricDetail metric,
        CurrentSnapshot currentSnapshot,
        PolicyInfo policy,
        ObservationWindow observationWindow,
        List<DemandEventView> demandEvents,
        List<InboundScheduleView> inboundSchedules,
        List<OpenTransferView> openTransfers,
        List<CandidateDetail> candidatesAsReceiver,
        List<CandidateDetail> candidatesAsDonor,
        RuleAssumptions ruleAssumptions
) {

    public record RunSummary(
            Long analysisRunId,
            LocalDate analysisDate,
            String inputSnapshotVersion,
            String ruleVersion,
            OffsetDateTime completedAt
    ) {
    }

    public record StoreSummary(String storeId, String storeName, String region) {
    }

    public record ProductSummary(String skuId, String productName, String category, String color, String sizeName) {
    }

    public record AssumptionNotice(String type, String notice) {
    }

    public record MetricDetail(
            InventoryClassification classification,
            InventoryPriority priority,
            Integer availableQuantity,
            BigDecimal averageDailySales,
            BigDecimal coverageDays,
            InventoryExceptionType inventoryExceptionType,
            InventorySeverity severity,
            DemandSignalType primaryDemandSignalType,
            DemandConfidence demandConfidence,
            Integer projectedAvailable,
            Long expectedShortageQuantity,
            String calculationVersion,
            Integer observableDayCount,
            Integer activeWeekCount,
            BigDecimal salesDayRatio,
            Integer maxDailySales,
            BigDecimal medianDailySales,
            BigDecimal madDailySales,
            Integer maxTransactionQuantity,
            BigDecimal lowDemandRate,
            BigDecimal baseDemandRate,
            BigDecimal highDemandRate,
            List<MetricQualityFlag> qualityFlags
    ) {
    }

    public record CurrentSnapshot(
            LocalDate snapshotDate,
            OffsetDateTime snapshotAt,
            Integer onHandQuantity,
            Integer reservedQuantity,
            Integer availableQuantity,
            boolean outOfStock,
            String sourceType
    ) {
    }

    public record PolicyInfo(
            String source,
            int displayMinimum,
            int safetyStock,
            int maximumCapacity,
            int targetCoverageDays,
            int retainedDays,
            String assumptionType
    ) {
    }

    public record ObservationWindow(
            LocalDate startDate,
            LocalDate endDate,
            int dayCount,
            List<ObservationDay> days
    ) {
    }

    public record ObservationDay(
            LocalDate date,
            Integer onHandQuantity,
            Integer reservedQuantity,
            boolean outOfStock,
            OffsetDateTime snapshotAt,
            Integer soldQuantity,
            Integer transactionCount,
            Integer maxTransactionQuantity,
            BigDecimal averageSellingPrice,
            String inventorySourceType,
            String salesSourceType
    ) {
    }

    public record DemandEventView(
            String eventCode,
            DemandEventType eventType,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal upliftLow,
            BigDecimal upliftBase,
            BigDecimal upliftHigh,
            String sourceType,
            String assumptionType
    ) {
    }

    public record InboundScheduleView(
            String inboundReference,
            Integer quantity,
            OffsetDateTime etaAt,
            InboundStatus inboundStatus,
            String sourceType
    ) {
    }

    public record OpenTransferView(
            String transferReference,
            String direction,
            String donorStoreId,
            String receiverStoreId,
            int quantity,
            OffsetDateTime etaAt,
            OpenTransferStatus transferStatus,
            String sourceType
    ) {
    }

    public record RouteInfo(
            Long routeId,
            boolean active,
            boolean ownerOverride,
            int leadTimeDays,
            int minimumQuantity,
            int packageMultiple,
            int maximumQuantity,
            String assumptionType
    ) {
    }

    public record RejectionReasonView(TransferCandidateRejectionReason reasonCode, int reasonOrder) {
    }

    public record ScenarioView(
            Long scenarioId,
            TransferScenarioType scenarioType,
            BigDecimal demandRate,
            long scenarioQuantity,
            int packageMultiple,
            int receiverBeforeAvailable,
            int receiverAfterAvailable,
            BigDecimal receiverBeforeCoverage,
            BigDecimal receiverAfterCoverage,
            InventoryExceptionType receiverRiskCode,
            int donorBeforeAvailable,
            int donorAfterAvailable,
            BigDecimal donorBeforeCoverage,
            BigDecimal donorAfterCoverage,
            InventoryExceptionType donorRiskCode,
            int leadTimeDays,
            OffsetDateTime expectedArrivalAt,
            boolean inboundIncluded,
            String warningSummary,
            int candidateVersion,
            OffsetDateTime createdAt
    ) {
    }

    public record LatestDecisionView(
            int decisionSequence,
            DecisionStatus decisionStatus,
            Integer selectedQuantity,
            String reasonCode,
            String reason,
            String actorLabel,
            OffsetDateTime decidedAt
    ) {
    }

    public record CandidateDetail(
            Long recommendationId,
            String direction,
            String counterpartStoreId,
            String counterpartStoreName,
            RouteInfo route,
            CandidateStatus candidateStatus,
            int candidateVersion,
            RecommendationMode recommendationMode,
            Integer receiverShortageQuantity,
            Integer donorTransferableQuantity,
            Integer recommendedQuantity,
            Long projectedReceiverAtArrival,
            Long projectedDonorAtDispatch,
            Long receiverCapacityRemaining,
            OffsetDateTime evaluatedAt,
            List<RejectionReasonView> rejectionReasons,
            List<ScenarioView> scenarios,
            LatestDecisionView latestDecision
    ) {
    }

    public record RuleAssumptions(
            int observationWindowDays,
            int minimumObservableDays,
            int minimumLaunchDays,
            BigDecimal stableRepeatMaxWeeklyCv,
            int stableRepeatMinimumActiveWeeks,
            int intermittentMaximumActiveWeeks,
            BigDecimal intermittentMaximumSalesDayRatio,
            BigDecimal spikeAbsoluteMinimum,
            BigDecimal spikeMadMultiplier,
            BigDecimal spikeWindowShareMinimum,
            BigDecimal bulkTransactionMinimumQuantity,
            BigDecimal bulkTransactionShareMinimum,
            int minimumValidWeeklyRates,
            BigDecimal lowDemandRatePercentile,
            BigDecimal baseDemandRatePercentile,
            BigDecimal highDemandRatePercentile,
            String assumptionType
    ) {
    }
}
