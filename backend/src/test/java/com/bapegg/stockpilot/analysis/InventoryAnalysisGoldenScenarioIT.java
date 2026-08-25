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
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.SimpleJobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Golden Scenario ({@code knowledge/project.md} section 4,
 * {@code knowledge/business-rules.md} section 8) against the real Oracle instance:
 * Gangnam stockout risk / HIGH, Hongdae overstock, Seongsu normal, and a 25-unit
 * Hongdae -> Gangnam recommendation. Also verifies that re-running the same analysis
 * date and rule version does not create duplicate logical results, and that Spring
 * Batch's own JobRepository actually persists a COMPLETED JobInstance to Oracle
 * (rather than an in-memory {@code ResourcelessJobRepository}, which would make the
 * idempotency this test exercises meaningless across a process restart).
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
    private JobRepository jobRepository;

    @Autowired
    private Job inventoryAnalysisJob;

    @Autowired
    private SpAnalysisRunRepository analysisRunRepository;

    @Autowired
    private SpInventoryMetricRepository metricRepository;

    @Autowired
    private SpRebalanceRecommendationRepository recommendationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void goldenScenarioAnalysisIsIdempotentAndProducesExpectedResults() throws Exception {
        JobParameters parameters = jobParameters();

        JobExecution firstExecution;
        try {
            firstExecution = jobOperator.start(inventoryAnalysisJob, parameters);
            assertEquals(BatchStatus.COMPLETED, firstExecution.getStatus());
        } catch (JobInstanceAlreadyCompleteException alreadyComplete) {
            // A prior run of this test already completed this analysis date and rule
            // version against the persistent Oracle instance; that is the expected
            // idempotent behavior, not a failure. Ask the *currently wired*
            // JobRepository directly for that execution, rather than assuming it is
            // the one already in Oracle (see the exact-id check below for why).
            firstExecution = jobRepository.getLastJobExecution(inventoryAnalysisJob.getName(), parameters);
            assertNotNull(firstExecution,
                    "JobRepository reported the JobInstance already completed but returned no JobExecution for it.");
            assertEquals(BatchStatus.COMPLETED, firstExecution.getStatus());
        }

        long metricCountAfterFirstRun = metricRepository.count();
        long recommendationCountAfterFirstRun = recommendationRepository.count();

        assertThrows(JobInstanceAlreadyCompleteException.class,
                () -> jobOperator.start(inventoryAnalysisJob, parameters));

        assertEquals(metricCountAfterFirstRun, metricRepository.count());
        assertEquals(recommendationCountAfterFirstRun, recommendationRepository.count());

        // Regression for a Codex review finding: Spring Batch's own idempotency
        // (JobInstanceAlreadyCompleteException above) is meaningless unless its
        // JobRepository actually persists to Oracle rather than silently falling back
        // to an in-memory, single-slot implementation that only "remembers" within
        // the current JVM/ApplicationContext.
        //
        // Codex re-review caught that an exact-JobExecution-id Oracle lookup is not
        // sufficient here: Spring Batch 6.0.4's ResourcelessJobRepository hard-codes
        // JobExecution id 1 for every JobInstance it creates, and this test's own
        // Golden Scenario JobInstance (analysisDate=2026-08-25) genuinely got id 1 the
        // first time it was ever persisted to Oracle -- so a future regression to
        // ResourcelessJobRepository would report id 1 too, coincidentally matching that
        // real historical row and passing an id-based check for the wrong reason.
        //
        // Assert the wired implementation directly instead (proxy-safe, since
        // @EnableJdbcJobRepository wraps the real SimpleJobRepository in a
        // transactional AOP proxy): this fails immediately and unconditionally on a
        // regression, independent of any id coincidence or prior Oracle state.
        Class<?> jobRepositoryTargetClass = AopProxyUtils.ultimateTargetClass(jobRepository);
        assertEquals(SimpleJobRepository.class, jobRepositoryTargetClass,
                "Expected the currently wired JobRepository to be Spring Batch's JDBC-backed "
                        + "SimpleJobRepository (via @EnableJdbcJobRepository on "
                        + "InventoryAnalysisJobConfig), but found " + jobRepositoryTargetClass.getName()
                        + ". That in-memory fallback does not persist to Oracle's BATCH_* tables.");

        // Secondary confirmation that persistence actually happened for this run's own
        // JobExecution (not a regression guard by itself -- the class check above is).
        Integer persistedThisExecution = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM BATCH_JOB_EXECUTION je "
                        + "JOIN BATCH_JOB_INSTANCE ji ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID "
                        + "WHERE je.JOB_EXECUTION_ID = ? AND ji.JOB_NAME = ? AND je.STATUS = 'COMPLETED'",
                Integer.class, firstExecution.getId(), inventoryAnalysisJob.getName());
        assertEquals(1, persistedThisExecution,
                "Expected the JobExecution (id=" + firstExecution.getId() + ") the currently wired "
                        + "JobRepository reported to be persisted as COMPLETED in Oracle's BATCH_JOB_EXECUTION.");

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
