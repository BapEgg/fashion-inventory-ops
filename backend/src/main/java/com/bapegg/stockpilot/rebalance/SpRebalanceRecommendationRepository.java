package com.bapegg.stockpilot.rebalance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpRebalanceRecommendationRepository extends JpaRepository<SpRebalanceRecommendation, Long> {

    @Query("""
            SELECT r FROM SpRebalanceRecommendation r
            JOIN FETCH r.receiverMetric rm JOIN FETCH rm.inventorySnapshot
            JOIN FETCH r.donorMetric dm JOIN FETCH dm.inventorySnapshot
            WHERE rm.analysisRun.analysisRunId = :analysisRunId
            """)
    List<SpRebalanceRecommendation> findByReceiverMetric_AnalysisRun_AnalysisRunId(
            @Param("analysisRunId") Long analysisRunId);
}
