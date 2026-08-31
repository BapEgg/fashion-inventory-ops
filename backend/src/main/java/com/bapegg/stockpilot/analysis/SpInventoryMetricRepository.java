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
    /**
     * The MVP-2 run-bound list read API's filter+workStatus+sort+page query, per redesign spec
     * sections 4.2-4.5. Only the ordered id page comes back from this statement; every other list
     * field is bulk-fetched separately by that id set. There is one {@code findPagedIds*} method
     * per {@link ExceptionSortKey}/{@link ExceptionSortDirection} combination -- JPQL
     * {@code @Query} text is a compile-time constant, so it cannot branch on a runtime sort
     * parameter, and {@link InventoryExceptionQuerySql} is the single place each variant's shared
     * filter/order fragments are assembled, so the ten variants below can never drift from each
     * other or from {@link #countPaged}. {@code pageable}'s {@code Sort} must be
     * {@link org.springframework.data.domain.Sort#unsorted()} -- only its offset/page size are
     * used, the fixed order embedded in the chosen method is the only order this contract allows.
     */
    @Query(InventoryExceptionQuerySql.FIND_PAGED_IDS_WORK_PRIORITY_ASC)
    List<Long> findPagedIdsByWorkPriorityAsc(
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
            @Param("workStatusFilterActive") boolean workStatusFilterActive,
            @Param("workStatusNames") Collection<String> workStatusNames,
            @Param("priceDate") LocalDate priceDate,
            @Param("inputVersion") String inputVersion,
            Pageable pageable);

    @Query(InventoryExceptionQuerySql.FIND_PAGED_IDS_WORK_PRIORITY_DESC)
    List<Long> findPagedIdsByWorkPriorityDesc(
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
            @Param("workStatusFilterActive") boolean workStatusFilterActive,
            @Param("workStatusNames") Collection<String> workStatusNames,
            @Param("priceDate") LocalDate priceDate,
            @Param("inputVersion") String inputVersion,
            Pageable pageable);

    @Query(InventoryExceptionQuerySql.FIND_PAGED_IDS_SALES_EXPOSURE_ASC)
    List<Long> findPagedIdsBySalesExposureAsc(
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
            @Param("workStatusFilterActive") boolean workStatusFilterActive,
            @Param("workStatusNames") Collection<String> workStatusNames,
            @Param("priceDate") LocalDate priceDate,
            @Param("inputVersion") String inputVersion,
            Pageable pageable);

    @Query(InventoryExceptionQuerySql.FIND_PAGED_IDS_SALES_EXPOSURE_DESC)
    List<Long> findPagedIdsBySalesExposureDesc(
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
            @Param("workStatusFilterActive") boolean workStatusFilterActive,
            @Param("workStatusNames") Collection<String> workStatusNames,
            @Param("priceDate") LocalDate priceDate,
            @Param("inputVersion") String inputVersion,
            Pageable pageable);

    @Query(InventoryExceptionQuerySql.FIND_PAGED_IDS_SHORTAGE_QUANTITY_ASC)
    List<Long> findPagedIdsByShortageQuantityAsc(
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
            @Param("workStatusFilterActive") boolean workStatusFilterActive,
            @Param("workStatusNames") Collection<String> workStatusNames,
            @Param("priceDate") LocalDate priceDate,
            @Param("inputVersion") String inputVersion,
            Pageable pageable);

    @Query(InventoryExceptionQuerySql.FIND_PAGED_IDS_SHORTAGE_QUANTITY_DESC)
    List<Long> findPagedIdsByShortageQuantityDesc(
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
            @Param("workStatusFilterActive") boolean workStatusFilterActive,
            @Param("workStatusNames") Collection<String> workStatusNames,
            @Param("priceDate") LocalDate priceDate,
            @Param("inputVersion") String inputVersion,
            Pageable pageable);

    @Query(InventoryExceptionQuerySql.FIND_PAGED_IDS_COVERAGE_DAYS_ASC)
    List<Long> findPagedIdsByCoverageDaysAsc(
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
            @Param("workStatusFilterActive") boolean workStatusFilterActive,
            @Param("workStatusNames") Collection<String> workStatusNames,
            @Param("priceDate") LocalDate priceDate,
            @Param("inputVersion") String inputVersion,
            Pageable pageable);

    @Query(InventoryExceptionQuerySql.FIND_PAGED_IDS_COVERAGE_DAYS_DESC)
    List<Long> findPagedIdsByCoverageDaysDesc(
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
            @Param("workStatusFilterActive") boolean workStatusFilterActive,
            @Param("workStatusNames") Collection<String> workStatusNames,
            @Param("priceDate") LocalDate priceDate,
            @Param("inputVersion") String inputVersion,
            Pageable pageable);

    @Query(InventoryExceptionQuerySql.FIND_PAGED_IDS_STORE_PRODUCT_ASC)
    List<Long> findPagedIdsByStoreProductAsc(
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
            @Param("workStatusFilterActive") boolean workStatusFilterActive,
            @Param("workStatusNames") Collection<String> workStatusNames,
            @Param("priceDate") LocalDate priceDate,
            @Param("inputVersion") String inputVersion,
            Pageable pageable);

    @Query(InventoryExceptionQuerySql.FIND_PAGED_IDS_STORE_PRODUCT_DESC)
    List<Long> findPagedIdsByStoreProductDesc(
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
            @Param("workStatusFilterActive") boolean workStatusFilterActive,
            @Param("workStatusNames") Collection<String> workStatusNames,
            @Param("priceDate") LocalDate priceDate,
            @Param("inputVersion") String inputVersion,
            Pageable pageable);

    /** The same filter predicate every {@code findPagedIds*} variant shares, without order/page, for {@code totalElements}. */
    @Query(InventoryExceptionQuerySql.COUNT_PAGED)
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
            @Param("hasExecutableCandidate") Boolean hasExecutableCandidate,
            @Param("workStatusFilterActive") boolean workStatusFilterActive,
            @Param("workStatusNames") Collection<String> workStatusNames);

    /**
     * The run-wide {@code AllocatorWorkSummary} aggregate, per section 4.4 -- unaffected by the
     * caller's current page or filters, identical on every page of the same run.
     * {@code Object[]} order: {@code totalReviewTargets, criticalCount, decisionRequiredCount,
     * onHoldCount, reviewInputCount, noTransferOptionCount, completedCount,
     * estimatedSalesExposureTotal, estimatedSalesExposureUnknownCount}.
     */
    @Query(InventoryExceptionQuerySql.SUMMARY)
    List<Object[]> summarize(@Param("runId") Long runId, @Param("priceDate") LocalDate priceDate, @Param("inputVersion") String inputVersion);

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
