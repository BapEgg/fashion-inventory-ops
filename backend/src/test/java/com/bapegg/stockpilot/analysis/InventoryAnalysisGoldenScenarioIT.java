package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Golden Scenario ({@code knowledge/project.md} section 4,
 * {@code knowledge/business-rules.md} section 8) against the real Oracle instance:
 * Gangnam stockout risk / HIGH, Hongdae overstock, Seongsu normal, and a 25-unit
 * Hongdae -> Gangnam recommendation. Also verifies that re-running the same analysis
 * date and rule version does not create duplicate logical results.
 * <p>
 * Skipped (not failed) when DB_URL is not set, since Oracle is not H2-substitutable
 * infrastructure for this project.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class InventoryAnalysisGoldenScenarioIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 8, 25);

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job inventoryAnalysisJob;

    @Autowired
    private SpAnalysisRunRepository analysisRunRepository;

    @Autowired
    private SpInventoryMetricRepository metricRepository;

    @Autowired
    private SpRebalanceRecommendationRepository recommendationRepository;

    @Test
    void goldenScenarioAnalysisIsIdempotentAndProducesExpectedResults() throws Exception {
        JobParameters parameters = jobParameters();

        try {
            JobExecution execution = jobOperator.start(inventoryAnalysisJob, parameters);
            assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        } catch (JobInstanceAlreadyCompleteException alreadyComplete) {
            // A prior run of this test already completed this analysis date and rule
            // version against the persistent Oracle instance; that is the expected
            // idempotent behavior, not a failure.
        }

        long metricCountAfterFirstRun = metricRepository.count();
        long recommendationCountAfterFirstRun = recommendationRepository.count();

        assertThrows(JobInstanceAlreadyCompleteException.class,
                () -> jobOperator.start(inventoryAnalysisJob, parameters));

        assertEquals(metricCountAfterFirstRun, metricRepository.count());
        assertEquals(recommendationCountAfterFirstRun, recommendationRepository.count());

        SpAnalysisRun run = analysisRunRepository
                .findByAnalysisDateAndRuleVersion(ANALYSIS_DATE, InventoryAnalysisRules.RULE_VERSION)
                .orElseThrow(() -> new AssertionError("Expected analysis run to be persisted."));
        assertEquals(AnalysisRunStatus.COMPLETED, run.getRunStatus());

        List<SpInventoryMetric> metrics = metricRepository.findByAnalysisRun_AnalysisRunId(run.getAnalysisRunId());
        SpInventoryMetric gangnam = metricByStore(metrics, "STORE-GANGNAM");
        SpInventoryMetric hongdae = metricByStore(metrics, "STORE-HONGDAE");
        SpInventoryMetric seongsu = metricByStore(metrics, "STORE-SEONGSU");

        assertEquals(InventoryClassification.STOCKOUT_RISK, gangnam.getClassification());
        assertEquals(InventoryPriority.HIGH, gangnam.getPriority());
        assertEquals(InventoryClassification.OVERSTOCK, hongdae.getClassification());
        assertNull(hongdae.getPriority());
        assertEquals(InventoryClassification.NORMAL, seongsu.getClassification());
        assertNull(seongsu.getPriority());

        List<SpRebalanceRecommendation> recommendations = recommendationRepository
                .findByReceiverMetric_AnalysisRun_AnalysisRunId(run.getAnalysisRunId());
        assertEquals(1, recommendations.size());

        SpRebalanceRecommendation recommendation = recommendations.get(0);
        assertEquals("STORE-GANGNAM", recommendation.getReceiverMetric().getInventorySnapshot().getStoreId());
        assertEquals("STORE-HONGDAE", recommendation.getDonorMetric().getInventorySnapshot().getStoreId());
        assertEquals(25, recommendation.getReceiverShortageQuantity());
        assertEquals(30, recommendation.getDonorTransferableQuantity());
        assertEquals(25, recommendation.getRecommendedQuantity());
        assertTrue(recommendation.getReceiverShortageQuantity() >= recommendation.getRecommendedQuantity());
    }

    private static SpInventoryMetric metricByStore(List<SpInventoryMetric> metrics, String storeId) {
        return metrics.stream()
                .filter(metric -> storeId.equals(metric.getInventorySnapshot().getStoreId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No metric found for store " + storeId));
    }

    private static JobParameters jobParameters() {
        return new JobParametersBuilder()
                .addLocalDate("analysisDate", ANALYSIS_DATE)
                .addString("ruleVersion", InventoryAnalysisRules.RULE_VERSION)
                .toJobParameters();
    }
}
