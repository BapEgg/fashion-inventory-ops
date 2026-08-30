package com.bapegg.stockpilot.rebalance;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SpRebalanceRecommendationRepository extends JpaRepository<SpRebalanceRecommendation, Long> {

    /**
     * The approval transaction's first lock, per business-rules.md section 10: every
     * decision path locks the recommendation row before anything else. A 3-second
     * {@code jakarta.persistence.lock.timeout} is an {@code ASSUMPTION} demo technical
     * policy (matches Oracle's {@code FOR UPDATE WAIT 3}), not a real operational SLA.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT r FROM SpRebalanceRecommendation r WHERE r.recommendationId = :recommendationId")
    Optional<SpRebalanceRecommendation> lockById(@Param("recommendationId") Long recommendationId);

    @Query("""
            SELECT r FROM SpRebalanceRecommendation r
            JOIN FETCH r.receiverMetric rm JOIN FETCH rm.inventorySnapshot
            JOIN FETCH r.donorMetric dm JOIN FETCH dm.inventorySnapshot
            WHERE rm.analysisRun.analysisRunId = :analysisRunId
            """)
    List<SpRebalanceRecommendation> findByReceiverMetric_AnalysisRun_AnalysisRunId(
            @Param("analysisRunId") Long analysisRunId);

    @Query("""
            SELECT r FROM SpRebalanceRecommendation r
            JOIN FETCH r.receiverMetric rm JOIN FETCH rm.inventorySnapshot
            JOIN FETCH r.donorMetric dm JOIN FETCH dm.inventorySnapshot
            WHERE r.recommendationId = :recommendationId
            """)
    Optional<SpRebalanceRecommendation> findWithMetricsById(@Param("recommendationId") Long recommendationId);

    @Query("""
            SELECT r FROM SpRebalanceRecommendation r
            JOIN FETCH r.receiverMetric rm JOIN FETCH rm.inventorySnapshot
            JOIN FETCH r.donorMetric dm JOIN FETCH dm.inventorySnapshot
            WHERE rm.inventoryMetricId = :inventoryMetricId OR dm.inventoryMetricId = :inventoryMetricId
            """)
    List<SpRebalanceRecommendation> findByReceiverMetricIdOrDonorMetricId(
            @Param("inventoryMetricId") Long inventoryMetricId);

    /**
     * The MVP-2 list read API's candidate-summary counts, per current-task.md section 3 --
     * bulk-fetched (without the receiver/donor JOIN FETCH the single-metric detail query above
     * uses, since the id set alone is all this call needs) then aggregated per metric id in
     * Java, one statement regardless of page size.
     */
    @Query("""
            SELECT r FROM SpRebalanceRecommendation r
            WHERE r.receiverMetric.inventoryMetricId IN :inventoryMetricIds
                OR r.donorMetric.inventoryMetricId IN :inventoryMetricIds
            """)
    List<SpRebalanceRecommendation> findByReceiverMetricIdInOrDonorMetricIdIn(
            @Param("inventoryMetricIds") Collection<Long> inventoryMetricIds);
}
