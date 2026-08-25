package com.bapegg.stockpilot.rebalance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpRebalanceRecommendationRepository extends JpaRepository<SpRebalanceRecommendation, Long> {

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
}
