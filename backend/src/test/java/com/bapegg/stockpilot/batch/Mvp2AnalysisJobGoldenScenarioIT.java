package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.analysis.AnalysisRunStatus;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.analysis.SpInventoryMetricRepository;
import com.bapegg.stockpilot.analysis.SpMetricQualityFlagRepository;
import com.bapegg.stockpilot.demand.DemandAnalysisRules;
import com.bapegg.stockpilot.demand.TransferCandidateRejectionReason;
import com.bapegg.stockpilot.demand.TransferScenarioType;
import com.bapegg.stockpilot.rebalance.CandidateStatus;
import com.bapegg.stockpilot.rebalance.SpCandidateReason;
import com.bapegg.stockpilot.rebalance.SpCandidateReasonRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendationRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceScenario;
import com.bapegg.stockpilot.rebalance.SpRebalanceScenarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the official {@code (2026-09-30, MVP-2-GS-V1, MVP-2)} Golden Scenario triple end to
 * end through the production {@code mvp2AnalysisJob} (not {@link Mvp2AnalysisExecutor} directly),
 * per current-task.md's Required tests items 5-6. This is the single owner of that triple: no
 * other test writes to, deletes, or resets it, and this test never deletes it either -- a
 * previous run (this JVM or an earlier CI run) having already completed it is an accepted outcome
 * ({@link JobInstanceAlreadyCompleteException}), not a failure, exactly as
 * current-task.md's restart/concurrency semantics require of any caller relaunching an already
 * {@code COMPLETED} triple. Skipped (not failed) when DB_URL is not set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class Mvp2AnalysisJobGoldenScenarioIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 9, 30);
    private static final String INPUT_SNAPSHOT_VERSION = "MVP-2-GS-V1";
    private static final String RULE_VERSION = DemandAnalysisRules.RULE_VERSION;

    private static final String RECEIVER = "STORE-MVP2-RECEIVER-A";
    private static final String DONOR_A = "STORE-MVP2-DONOR-A";
    private static final String DONOR_B = "STORE-MVP2-DONOR-B";

    private static final String GS01 = "SKU-MVP2-GS01-STABLE";
    private static final String GS02 = "SKU-MVP2-GS02-EVENT";
    private static final String GS05 = "SKU-MVP2-GS05-INBOUND";
    private static final String GS06 = "SKU-MVP2-GS06-ROUTE";

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("mvp2AnalysisJob")
    private Job mvp2AnalysisJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SpAnalysisRunRepository analysisRunRepository;

    @Autowired
    private SpInventoryMetricRepository metricRepository;

    @Autowired
    private SpMetricQualityFlagRepository qualityFlagRepository;

    @Autowired
    private SpRebalanceRecommendationRepository recommendationRepository;

    @Autowired
    private SpCandidateReasonRepository candidateReasonRepository;

    @Autowired
    private SpRebalanceScenarioRepository scenarioRepository;

    @Test
    void theOfficialGoldenTripleCompletesThroughTheProductionJobAndPersistsTheExactGoldenCounts() throws Exception {
        Mvp2AnalysisJobParameters jobParameters =
                new Mvp2AnalysisJobParameters(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION);
        JobParameters batchParameters = jobParameters.toJobParameters();

        try {
            JobExecution execution = jobOperator.start(mvp2AnalysisJob, batchParameters);
            assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        } catch (JobInstanceAlreadyCompleteException alreadyComplete) {
            // Accepted: a previous run already completed this exact official triple.
        }

        SpAnalysisRun run = analysisRunRepository
                .findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION)
                .orElseThrow();
        assertEquals(AnalysisRunStatus.COMPLETED, run.getRunStatus());
        Long runId = run.getAnalysisRunId();

        List<SpInventoryMetric> metrics = metricRepository.findByAnalysisRun_AnalysisRunId(runId);
        assertEquals(12, metrics.size(), "6 SKUs x 2 anchors (receiver + donor) each.");

        Set<Long> metricIds = metrics.stream().map(SpInventoryMetric::getInventoryMetricId).collect(Collectors.toSet());
        long flagCount = qualityFlagRepository.findAll().stream()
                .filter(f -> metricIds.contains(f.getInventoryMetric().getInventoryMetricId()))
                .count();
        assertEquals(1, flagCount, "Only GS-04's receiver anchor carries OOS_CENSORED.");

        List<SpRebalanceRecommendation> candidates = recommendationRepository.findByReceiverMetric_AnalysisRun_AnalysisRunId(runId);
        assertEquals(4, candidates.size(), "GS-01, GS-02, GS-05, GS-06 only -- GS-03/04 are gated out.");
        long eligibleCount = candidates.stream().filter(c -> c.getCandidateStatus() == CandidateStatus.ELIGIBLE).count();
        assertEquals(2, eligibleCount);
        assertEquals(2, candidates.size() - eligibleCount);

        Set<Long> recommendationIds = candidates.stream().map(SpRebalanceRecommendation::getRecommendationId).collect(Collectors.toSet());
        long reasonCount = candidateReasonRepository.findAll().stream()
                .filter(r -> recommendationIds.contains(r.getRecommendation().getRecommendationId()))
                .count();
        assertEquals(3, reasonCount, "GS-05 has 1 (INBOUND_ALREADY_COVERS) and GS-06 has 2 (OWNER_MISMATCH, LEAD_TIME_TOO_LONG).");

        long scenarioCount = candidates.stream()
                .mapToLong(c -> scenarioRepository.findByRecommendation_RecommendationIdOrderByScenarioType(c.getRecommendationId()).size())
                .sum();
        assertEquals(8, scenarioCount, "Only GS-01 and GS-02 are ELIGIBLE, 4 scenarios each.");

        assertGs01(candidates);
        assertGs02(candidates);
        assertGs05(candidates);
        assertGs06(candidates);

        assertBatchMetadata(batchParameters);
    }

    private void assertGs01(List<SpRebalanceRecommendation> candidates) {
        SpRebalanceRecommendation candidate = candidateFor(candidates, GS01);
        assertEquals(CandidateStatus.ELIGIBLE, candidate.getCandidateStatus());
        assertEquals(DONOR_A, candidate.getDonorMetric().getInventorySnapshot().getStoreId());
        assertEquals(11, candidate.getRecommendedQuantity());
        List<SpRebalanceScenario> scenarios =
                scenarioRepository.findByRecommendation_RecommendationIdOrderByScenarioType(candidate.getRecommendationId());
        assertEquals(4, scenarios.size());
        SpRebalanceScenario base = scenarioOfType(scenarios, TransferScenarioType.BASE);
        assertEquals(11L, base.getScenarioQuantity());
    }

    private void assertGs02(List<SpRebalanceRecommendation> candidates) {
        SpRebalanceRecommendation candidate = candidateFor(candidates, GS02);
        assertEquals(CandidateStatus.ELIGIBLE, candidate.getCandidateStatus());
        assertEquals(20, candidate.getRecommendedQuantity());
        List<SpRebalanceScenario> scenarios =
                scenarioRepository.findByRecommendation_RecommendationIdOrderByScenarioType(candidate.getRecommendationId());
        SpRebalanceScenario base = scenarioOfType(scenarios, TransferScenarioType.BASE);
        assertEquals(0, new BigDecimal("3.000000000000").compareTo(base.getDemandRate()));
        assertEquals(20L, base.getScenarioQuantity());
    }

    private void assertGs05(List<SpRebalanceRecommendation> candidates) {
        SpRebalanceRecommendation candidate = candidateFor(candidates, GS05);
        assertEquals(CandidateStatus.REJECTED, candidate.getCandidateStatus());
        assertNull(candidate.getRecommendedQuantity());
        List<SpCandidateReason> reasons = candidateReasonRepository.findAll().stream()
                .filter(r -> candidate.getRecommendationId().equals(r.getRecommendation().getRecommendationId()))
                .toList();
        assertTrue(reasons.stream().anyMatch(r -> r.getReasonCode() == TransferCandidateRejectionReason.INBOUND_ALREADY_COVERS));
        assertTrue(scenarioRepository.findByRecommendation_RecommendationIdOrderByScenarioType(candidate.getRecommendationId()).isEmpty());
    }

    private void assertGs06(List<SpRebalanceRecommendation> candidates) {
        SpRebalanceRecommendation candidate = candidateFor(candidates, GS06);
        assertEquals(CandidateStatus.REJECTED, candidate.getCandidateStatus());
        assertEquals(DONOR_B, candidate.getDonorMetric().getInventorySnapshot().getStoreId());
        List<SpCandidateReason> reasons = candidateReasonRepository.findAll().stream()
                .filter(r -> candidate.getRecommendationId().equals(r.getRecommendation().getRecommendationId()))
                .sorted((a, b) -> Integer.compare(a.getReasonOrder(), b.getReasonOrder()))
                .toList();
        assertEquals(
                List.of(TransferCandidateRejectionReason.OWNER_MISMATCH, TransferCandidateRejectionReason.LEAD_TIME_TOO_LONG),
                reasons.stream().map(SpCandidateReason::getReasonCode).toList());
    }

    private static SpRebalanceRecommendation candidateFor(List<SpRebalanceRecommendation> candidates, String skuId) {
        return candidates.stream()
                .filter(c -> RECEIVER.equals(c.getReceiverMetric().getInventorySnapshot().getStoreId())
                        && skuId.equals(c.getReceiverMetric().getInventorySnapshot().getSkuId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected exactly one persisted candidate for " + skuId));
    }

    private static SpRebalanceScenario scenarioOfType(List<SpRebalanceScenario> scenarios, TransferScenarioType type) {
        return scenarios.stream().filter(s -> s.getScenarioType() == type).findFirst().orElseThrow();
    }

    /**
     * Confirms the Batch metadata itself, not just the domain result: the JobInstance/JobExecution
     * relationship, and each parameter's real Oracle-stored type/identifying flag.
     */
    private void assertBatchMetadata(JobParameters batchParameters) {
        Long jobInstanceId = jdbcTemplate.queryForObject(
                "SELECT DISTINCT je.JOB_INSTANCE_ID FROM BATCH_JOB_EXECUTION je "
                        + "JOIN BATCH_JOB_EXECUTION_PARAMS p ON p.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID "
                        + "WHERE p.PARAMETER_NAME = 'inputSnapshotVersion' AND p.PARAMETER_VALUE = ?",
                Long.class, INPUT_SNAPSHOT_VERSION);

        Long jobExecutionId = jdbcTemplate.queryForObject(
                "SELECT MAX(JOB_EXECUTION_ID) FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID = ?",
                Long.class, jobInstanceId);
        String status = jdbcTemplate.queryForObject(
                "SELECT STATUS FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = ?", String.class, jobExecutionId);
        assertEquals("COMPLETED", status);

        List<java.util.Map<String, Object>> params = jdbcTemplate.queryForList(
                "SELECT PARAMETER_NAME, PARAMETER_TYPE, IDENTIFYING FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID = ?",
                jobExecutionId);
        assertEquals(3, params.size());
        for (java.util.Map<String, Object> row : params) {
            String name = (String) row.get("PARAMETER_NAME");
            String type = (String) row.get("PARAMETER_TYPE");
            String identifying = String.valueOf(row.get("IDENTIFYING")).trim();
            assertEquals("Y", identifying, "Parameter " + name + " must be identifying.");
            if ("analysisDate".equals(name)) {
                assertEquals(LocalDate.class.getName(), type);
            } else {
                assertTrue("inputSnapshotVersion".equals(name) || "ruleVersion".equals(name), "Unexpected parameter " + name);
                assertEquals(String.class.getName(), type);
            }
        }
    }
}
