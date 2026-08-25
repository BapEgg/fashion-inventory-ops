package com.bapegg.stockpilot.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpInventoryMetricRepository extends JpaRepository<SpInventoryMetric, Long> {

    @Query("""
            SELECT m FROM SpInventoryMetric m
            JOIN FETCH m.inventorySnapshot
            WHERE m.analysisRun.analysisRunId = :analysisRunId
            """)
    List<SpInventoryMetric> findByAnalysisRun_AnalysisRunId(@Param("analysisRunId") Long analysisRunId);
}
