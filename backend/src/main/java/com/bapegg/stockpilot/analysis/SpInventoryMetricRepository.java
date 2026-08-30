package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.demand.DemandConfidence;
import com.bapegg.stockpilot.demand.DemandSignalType;
import com.bapegg.stockpilot.demand.InventoryExceptionType;
import com.bapegg.stockpilot.demand.InventorySeverity;
import com.bapegg.stockpilot.demand.MetricQualityFlag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SpInventoryMetricRepository extends JpaRepository<SpInventoryMetric, Long> {

    @Query("""
            SELECT m FROM SpInventoryMetric m
            JOIN FETCH m.inventorySnapshot
            WHERE m.analysisRun.analysisRunId = :analysisRunId
            """)
    List<SpInventoryMetric> findByAnalysisRun_AnalysisRunId(@Param("analysisRunId") Long analysisRunId);

    @Query("""
            SELECT m FROM SpInventoryMetric m
            JOIN FETCH m.inventorySnapshot
            WHERE m.inventoryMetricId = :inventoryMetricId
            """)
    Optional<SpInventoryMetric> findWithSnapshotById(@Param("inventoryMetricId") Long inventoryMetricId);

    /**
     * The Phase 3 atomic output writer's partial-result guard, per current-task.md section 4
     * step 4: a non-zero count for the claimed run means an already-written result exists, and
     * the writer must never delete or overwrite it.
     */
    long countByAnalysisRun_AnalysisRunId(Long analysisRunId);

    /**
     * The MVP-2 run-bound list read API's filter+fixed-order+page query, per current-task.md
     * sections 2-3 -- {@code ...FilterActive=false} means "no filter of this kind", and the
     * paired collection parameter is then a harmless non-empty placeholder never actually
     * evaluated as a match (an empty {@code IN} collection is never bound). Only the ordered id
     * page comes back from this statement; every other list field is bulk-fetched separately by
     * that id set. {@code pageable}'s {@code Sort} must be {@link org.springframework.data.domain.Sort#unsorted()}
     * -- the fixed order below is the only order this contract allows -- and only its offset/page
     * size are used.
     */
    @Query("""
            SELECT m.inventoryMetricId FROM SpInventoryMetric m
                JOIN m.inventorySnapshot snap
            WHERE m.analysisRun.analysisRunId = :runId
                AND m.inventoryExceptionType <> com.bapegg.stockpilot.demand.InventoryExceptionType.NORMAL
                AND (:exceptionTypeFilterActive = false OR m.inventoryExceptionType IN :exceptionTypes)
                AND (:severityFilterActive = false OR m.severity IN :severities)
                AND (:signalFilterActive = false OR m.primaryDemandSignalType IN :signals)
                AND (:confidenceFilterActive = false OR m.demandConfidence IN :confidences)
                AND (:qualityFlagFilterActive = false OR EXISTS (
                        SELECT 1 FROM SpMetricQualityFlag f
                        WHERE f.inventoryMetric = m AND f.flagCode IN :qualityFlags))
                AND (:storeId IS NULL OR snap.storeId = :storeId)
                AND (:skuId IS NULL OR snap.skuId = :skuId)
                AND (:hasExecutableCandidate IS NULL OR
                    (:hasExecutableCandidate = true AND EXISTS (
                        SELECT 1 FROM com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation r
                        WHERE (r.receiverMetric = m OR r.donorMetric = m)
                            AND r.candidateStatus = com.bapegg.stockpilot.rebalance.CandidateStatus.ELIGIBLE
                            AND r.recommendationMode = com.bapegg.stockpilot.rebalance.RecommendationMode.RECOMMENDED))
                    OR
                    (:hasExecutableCandidate = false AND NOT EXISTS (
                        SELECT 1 FROM com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation r
                        WHERE (r.receiverMetric = m OR r.donorMetric = m)
                            AND r.candidateStatus = com.bapegg.stockpilot.rebalance.CandidateStatus.ELIGIBLE
                            AND r.recommendationMode = com.bapegg.stockpilot.rebalance.RecommendationMode.RECOMMENDED)))
            ORDER BY
                CASE WHEN m.severity = com.bapegg.stockpilot.demand.InventorySeverity.CRITICAL THEN 0
                     WHEN m.severity = com.bapegg.stockpilot.demand.InventorySeverity.HIGH THEN 1
                     WHEN m.severity = com.bapegg.stockpilot.demand.InventorySeverity.REVIEW THEN 2
                     ELSE 3 END,
                CASE WHEN EXISTS (
                        SELECT 1 FROM com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation r2
                        WHERE (r2.receiverMetric = m OR r2.donorMetric = m)
                            AND r2.candidateStatus = com.bapegg.stockpilot.rebalance.CandidateStatus.ELIGIBLE
                            AND r2.recommendationMode = com.bapegg.stockpilot.rebalance.RecommendationMode.RECOMMENDED)
                     THEN 0 ELSE 1 END,
                CASE WHEN m.demandConfidence = com.bapegg.stockpilot.demand.DemandConfidence.HIGH THEN 0
                     WHEN m.demandConfidence = com.bapegg.stockpilot.demand.DemandConfidence.MEDIUM THEN 1
                     WHEN m.demandConfidence = com.bapegg.stockpilot.demand.DemandConfidence.LOW THEN 2
                     WHEN m.demandConfidence = com.bapegg.stockpilot.demand.DemandConfidence.NONE THEN 3
                     ELSE 4 END,
                m.expectedShortageQuantity DESC NULLS LAST,
                (m.expectedShortageQuantity * (
                    SELECT ds.averageSellingPrice FROM com.bapegg.stockpilot.inventory.SpDailySale ds
                    WHERE ds.salesDate = :priceDate AND ds.storeId = snap.storeId AND ds.skuId = snap.skuId
                        AND ds.inputSnapshotVersion = :inputVersion)) DESC NULLS LAST,
                snap.storeId ASC, snap.skuId ASC, m.inventoryMetricId ASC
            """)
    List<Long> findPagedIds(
            @Param("runId") Long runId,
            @Param("exceptionTypeFilterActive") boolean exceptionTypeFilterActive,
            @Param("exceptionTypes") Collection<InventoryExceptionType> exceptionTypes,
            @Param("severityFilterActive") boolean severityFilterActive,
            @Param("severities") Collection<InventorySeverity> severities,
            @Param("signalFilterActive") boolean signalFilterActive,
            @Param("signals") Collection<DemandSignalType> signals,
            @Param("confidenceFilterActive") boolean confidenceFilterActive,
            @Param("confidences") Collection<DemandConfidence> confidences,
            @Param("qualityFlagFilterActive") boolean qualityFlagFilterActive,
            @Param("qualityFlags") Collection<MetricQualityFlag> qualityFlags,
            @Param("storeId") String storeId,
            @Param("skuId") String skuId,
            @Param("hasExecutableCandidate") Boolean hasExecutableCandidate,
            @Param("priceDate") LocalDate priceDate,
            @Param("inputVersion") String inputVersion,
            Pageable pageable);

    /** The same filter predicate as {@link #findPagedIds}, without order/page, for {@code totalElements}. */
    @Query("""
            SELECT COUNT(m) FROM SpInventoryMetric m
                JOIN m.inventorySnapshot snap
            WHERE m.analysisRun.analysisRunId = :runId
                AND m.inventoryExceptionType <> com.bapegg.stockpilot.demand.InventoryExceptionType.NORMAL
                AND (:exceptionTypeFilterActive = false OR m.inventoryExceptionType IN :exceptionTypes)
                AND (:severityFilterActive = false OR m.severity IN :severities)
                AND (:signalFilterActive = false OR m.primaryDemandSignalType IN :signals)
                AND (:confidenceFilterActive = false OR m.demandConfidence IN :confidences)
                AND (:qualityFlagFilterActive = false OR EXISTS (
                        SELECT 1 FROM SpMetricQualityFlag f
                        WHERE f.inventoryMetric = m AND f.flagCode IN :qualityFlags))
                AND (:storeId IS NULL OR snap.storeId = :storeId)
                AND (:skuId IS NULL OR snap.skuId = :skuId)
                AND (:hasExecutableCandidate IS NULL OR
                    (:hasExecutableCandidate = true AND EXISTS (
                        SELECT 1 FROM com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation r
                        WHERE (r.receiverMetric = m OR r.donorMetric = m)
                            AND r.candidateStatus = com.bapegg.stockpilot.rebalance.CandidateStatus.ELIGIBLE
                            AND r.recommendationMode = com.bapegg.stockpilot.rebalance.RecommendationMode.RECOMMENDED))
                    OR
                    (:hasExecutableCandidate = false AND NOT EXISTS (
                        SELECT 1 FROM com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation r
                        WHERE (r.receiverMetric = m OR r.donorMetric = m)
                            AND r.candidateStatus = com.bapegg.stockpilot.rebalance.CandidateStatus.ELIGIBLE
                            AND r.recommendationMode = com.bapegg.stockpilot.rebalance.RecommendationMode.RECOMMENDED)))
            """)
    long countPaged(
            @Param("runId") Long runId,
            @Param("exceptionTypeFilterActive") boolean exceptionTypeFilterActive,
            @Param("exceptionTypes") Collection<InventoryExceptionType> exceptionTypes,
            @Param("severityFilterActive") boolean severityFilterActive,
            @Param("severities") Collection<InventorySeverity> severities,
            @Param("signalFilterActive") boolean signalFilterActive,
            @Param("signals") Collection<DemandSignalType> signals,
            @Param("confidenceFilterActive") boolean confidenceFilterActive,
            @Param("confidences") Collection<DemandConfidence> confidences,
            @Param("qualityFlagFilterActive") boolean qualityFlagFilterActive,
            @Param("qualityFlags") Collection<MetricQualityFlag> qualityFlags,
            @Param("storeId") String storeId,
            @Param("skuId") String skuId,
            @Param("hasExecutableCandidate") Boolean hasExecutableCandidate);

    /**
     * The MVP-2 detail read API's rule-version routing lookup, per current-task.md section 1.3
     * -- fetches {@code analysisRun} eagerly too (unlike {@link #findWithSnapshotById}) since the
     * caller must read {@code ruleVersion} before deciding which detail shape to build.
     */
    @Query("""
            SELECT m FROM SpInventoryMetric m
            JOIN FETCH m.inventorySnapshot
            JOIN FETCH m.analysisRun
            WHERE m.inventoryMetricId = :inventoryMetricId
            """)
    Optional<SpInventoryMetric> findWithSnapshotAndRunById(@Param("inventoryMetricId") Long inventoryMetricId);

    /**
     * The MVP-2 list read API's single row source, per current-task.md section 5's query
     * ceiling -- merges what would otherwise be five separate statements (metric+snapshot,
     * product lookup, store lookup, quality flags, D-1 selling price) into one. {@code p}/
     * {@code st} join on the snapshot's plain {@code skuId}/{@code storeId} FK columns (there is
     * no JPA relation to either, per {@link com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation}'s
     * own {@code routeId} javadoc making the same tradeoff); the {@code LEFT JOIN} to
     * {@link SpMetricQualityFlag} multiplies rows one-per-flag (or one row with a {@code null}
     * flag for a metric with none) -- callers group by the metric id in memory. The trailing
     * scalar subquery is the exact (not IN-list-widened) D-1 {@code averageSellingPrice} for this
     * row's own store-SKU, per current-task.md section 2.1's {@code currentSellingPrice}.
     */
    @Query("""
            SELECT m, p, st, f,
                (SELECT ds.averageSellingPrice FROM com.bapegg.stockpilot.inventory.SpDailySale ds
                    WHERE ds.salesDate = :priceDate AND ds.storeId = snap.storeId AND ds.skuId = snap.skuId
                        AND ds.inputSnapshotVersion = :inputVersion)
            FROM SpInventoryMetric m
                JOIN FETCH m.inventorySnapshot snap
                JOIN com.bapegg.stockpilot.catalog.SpProduct p ON p.skuId = snap.skuId
                JOIN com.bapegg.stockpilot.catalog.SpStore st ON st.storeId = snap.storeId
                LEFT JOIN SpMetricQualityFlag f ON f.inventoryMetric = m
            WHERE m.inventoryMetricId IN :inventoryMetricIds
            """)
    List<Object[]> findListRowsByInventoryMetricIdIn(
            @Param("inventoryMetricIds") Collection<Long> inventoryMetricIds,
            @Param("priceDate") LocalDate priceDate,
            @Param("inputVersion") String inputVersion);
}
