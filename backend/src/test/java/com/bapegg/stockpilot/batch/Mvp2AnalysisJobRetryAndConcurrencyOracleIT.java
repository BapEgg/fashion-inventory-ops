package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.analysis.AnalysisRunStatus;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.demand.DemandAnalysisRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies {@code mvp2AnalysisJob}'s restart and concurrency semantics against the real Oracle
 * instance, per current-task.md section 4 and Required tests item 4: a claim-then-fail retries
 * under the same JobInstance as a new JobExecution, a completed triple rejects further relaunch,
 * and a launcher racing an already-{@code RUNNING} JobInstance is rejected with exactly
 * {@link JobExecutionAlreadyRunningException}. The concurrency test uses a {@link MockitoSpyBean}
 * on {@link Mvp2AnalysisExecutor} to hold the first launcher inside its real {@code execute} call
 * (via a latch) until the Step's {@code STARTED} JobExecution row is genuinely committed, so the
 * second launcher's rejection is deterministic rather than racing the first JobInstance's own
 * insert -- per the Codex review finding, a broad {@code catch (Exception)} that also accepted an
 * Oracle {@code ORA-08177} serialization conflict could pass without ever proving the specific
 * already-running rejection contract.
 * <p>
 * Each test owns its own unique {@code inputSnapshotVersion} and cleans up everything it wrote
 * (domain rows, seed rows and this JobInstance's own BATCH_* metadata) in a {@code finally} block,
 * following the same FK-reverse-order convention as the other atomicity/lifecycle Oracle ITs.
 * Skipped (not failed) when DB_URL is not set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class Mvp2AnalysisJobRetryAndConcurrencyOracleIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 10, 20);
    private static final String STORE_ID = "STORE-GANGNAM";
    private static final String SKU_ID = "SKU-CAP-BLACK-FREE";
    private static final String RULE_VERSION = DemandAnalysisRules.RULE_VERSION;
    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.of("+09:00");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("mvp2AnalysisJob")
    private Job mvp2AnalysisJob;

    @Autowired
    private SpAnalysisRunRepository analysisRunRepository;

    @MockitoSpyBean
    private Mvp2AnalysisExecutor executor;

    @Test
    void aFailedJobExecutionRestartsUnderTheSameJobInstanceAndThenRejectsFurtherRelaunch() throws Exception {
        String inputVersion = "MVP2-JOB-RETRY-V1";
        JobParameters batchParams = new Mvp2AnalysisJobParameters(ANALYSIS_DATE, inputVersion, RULE_VERSION).toJobParameters();
        try {
            // First attempt: no seed data at all -- the input adapter finds no anchors and the
            // Tasklet's uncaught InputContractViolationException fails the Step/Job.
            JobExecution first = jobOperator.start(mvp2AnalysisJob, batchParams);
            assertEquals(BatchStatus.FAILED, first.getStatus());
            SpAnalysisRun failedRun = analysisRunRepository
                    .findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(ANALYSIS_DATE, inputVersion, RULE_VERSION)
                    .orElseThrow();
            assertEquals(AnalysisRunStatus.FAILED, failedRun.getRunStatus());
            Long runId = failedRun.getAnalysisRunId();

            // Now make the input contract satisfiable and relaunch the exact same triple.
            insertFullAnchor(inputVersion, 5, 0);

            JobExecution second = jobOperator.start(mvp2AnalysisJob, batchParams);
            assertEquals(BatchStatus.COMPLETED, second.getStatus());
            assertEquals(first.getJobInstance().getInstanceId(), second.getJobInstance().getInstanceId(),
                    "The retry must reuse the same JobInstance, not create a second one.");
            assertNotEquals(first.getId(), second.getId(), "The retry must be a distinct JobExecution.");

            SpAnalysisRun completedRun = analysisRunRepository.findById(runId).orElseThrow();
            assertEquals(AnalysisRunStatus.COMPLETED, completedRun.getRunStatus());

            assertThrows(JobInstanceAlreadyCompleteException.class,
                    () -> jobOperator.start(mvp2AnalysisJob, batchParams),
                    "A further relaunch of the now-completed triple must be rejected, not silently re-run.");
        } finally {
            cleanup(inputVersion);
        }
    }

    @Test
    void aSecondLauncherAgainstAGenuinelyRunningJobInstanceIsRejectedWithJobExecutionAlreadyRunning() throws Exception {
        String inputVersion = "MVP2-JOB-CONCURRENCY-V1";
        try {
            insertFullAnchor(inputVersion, 5, 0);
            JobParameters batchParams = new Mvp2AnalysisJobParameters(ANALYSIS_DATE, inputVersion, RULE_VERSION).toJobParameters();

            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Mockito.doAnswer(invocation -> {
                        entered.countDown();
                        assertTrue(release.await(10, TimeUnit.SECONDS),
                                "release must be signaled by the test before this stub's own wait times out.");
                        return invocation.callRealMethod();
                    })
                    .when(executor).execute(ANALYSIS_DATE, inputVersion, RULE_VERSION);

            ExecutorService pool = Executors.newSingleThreadExecutor();
            Future<JobExecution> firstLaunch;
            try {
                // The first launcher's Job/Step/JobExecution(STARTED) row is committed by Spring
                // Batch's own createJobExecution transaction before the Step ever calls the Tasklet,
                // so by the time this latch fires (deep inside the real executor.execute call) that
                // STARTED row is already visible to any other connection -- the second launch below
                // races nothing.
                firstLaunch = pool.submit(() -> jobOperator.start(mvp2AnalysisJob, batchParams));
                assertTrue(entered.await(10, TimeUnit.SECONDS),
                        "The first launcher must reach the executor before the second launch attempt.");

                assertThrows(JobExecutionAlreadyRunningException.class,
                        () -> jobOperator.start(mvp2AnalysisJob, batchParams),
                        "A launch against an already-RUNNING JobInstance must be rejected with exactly this exception.");
            } finally {
                release.countDown();
                pool.shutdown();
            }

            JobExecution first = firstLaunch.get(15, TimeUnit.SECONDS);
            assertEquals(BatchStatus.COMPLETED, first.getStatus());

            verify(executor, times(1)).execute(ANALYSIS_DATE, inputVersion, RULE_VERSION);

            Long jobInstanceCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT je.JOB_INSTANCE_ID) FROM BATCH_JOB_EXECUTION je "
                            + "JOIN BATCH_JOB_EXECUTION_PARAMS p ON p.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID "
                            + "WHERE p.PARAMETER_NAME = 'inputSnapshotVersion' AND p.PARAMETER_VALUE = ?",
                    Long.class, inputVersion);
            assertEquals(1L, jobInstanceCount, "The rejected second launcher must never create a second JobInstance.");
            Long jobExecutionCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM BATCH_JOB_EXECUTION je "
                            + "JOIN BATCH_JOB_EXECUTION_PARAMS p ON p.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID "
                            + "WHERE p.PARAMETER_NAME = 'inputSnapshotVersion' AND p.PARAMETER_VALUE = ?",
                    Long.class, inputVersion);
            assertEquals(1L, jobExecutionCount, "The rejected second launcher must never create a second JobExecution.");

            SpAnalysisRun run = analysisRunRepository
                    .findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(ANALYSIS_DATE, inputVersion, RULE_VERSION)
                    .orElseThrow();
            assertEquals(AnalysisRunStatus.COMPLETED, run.getRunStatus());
        } finally {
            cleanup(inputVersion);
        }
    }

    private void insertFullAnchor(String version, long onHand, long reserved) {
        LocalDate historyStart = ANALYSIS_DATE.minusDays(28);
        for (int offset = 0; offset < 28; offset++) {
            LocalDate date = historyStart.plusDays(offset);
            insertInventoryDay(date, version, 20, 0);
            insertSalesDay(date, version, 2, 2, 1);
        }
        insertInventoryDay(ANALYSIS_DATE, version, onHand, reserved);
    }

    private void insertInventoryDay(LocalDate date, String version, long onHand, long reserved) {
        OffsetDateTime snapshotAt = date.atStartOfDay(SEOUL_OFFSET).toOffsetDateTime();
        String outOfStockFlag = (onHand - reserved) <= 0 ? "Y" : "N";
        jdbcTemplate.update(
                "INSERT INTO sp_inventory_snapshot (snapshot_date, store_id, sku_id, on_hand_quantity, "
                        + "reserved_quantity, source_type, input_snapshot_version, snapshot_at, out_of_stock_flag) "
                        + "VALUES (?, ?, ?, ?, ?, 'SYNTHETIC', ?, ?, ?)",
                date, STORE_ID, SKU_ID, onHand, reserved, version, snapshotAt, outOfStockFlag);
    }

    private void insertSalesDay(LocalDate date, String version, int soldQuantity, int transactionCount, int maxTransactionQuantity) {
        jdbcTemplate.update(
                "INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type, "
                        + "input_snapshot_version, transaction_count, max_transaction_quantity, average_selling_price) "
                        + "VALUES (?, ?, ?, ?, 'SYNTHETIC', ?, ?, ?, 10000.00)",
                date, STORE_ID, SKU_ID, soldQuantity, version, transactionCount, maxTransactionQuantity);
    }

    private void cleanup(String inputVersion) {
        analysisRunRepository.findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(ANALYSIS_DATE, inputVersion, RULE_VERSION)
                .ifPresent(run -> deleteDomainOutput(run.getAnalysisRunId()));
        deleteBatchMetadata(inputVersion);
        jdbcTemplate.update("DELETE FROM sp_inventory_snapshot WHERE input_snapshot_version = ?", inputVersion);
        jdbcTemplate.update("DELETE FROM sp_daily_sale WHERE input_snapshot_version = ?", inputVersion);
    }

    private void deleteDomainOutput(Long runId) {
        jdbcTemplate.update(
                "DELETE FROM sp_rebalance_scenario WHERE recommendation_id IN "
                        + "(SELECT recommendation_id FROM sp_rebalance_recommendation WHERE receiver_metric_id IN "
                        + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?))", runId);
        jdbcTemplate.update(
                "DELETE FROM sp_candidate_reason WHERE recommendation_id IN "
                        + "(SELECT recommendation_id FROM sp_rebalance_recommendation WHERE receiver_metric_id IN "
                        + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?))", runId);
        jdbcTemplate.update(
                "DELETE FROM sp_rebalance_recommendation WHERE receiver_metric_id IN "
                        + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?)", runId);
        jdbcTemplate.update(
                "DELETE FROM sp_metric_quality_flag WHERE inventory_metric_id IN "
                        + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?)", runId);
        jdbcTemplate.update("DELETE FROM sp_inventory_metric WHERE analysis_run_id = ?", runId);
        jdbcTemplate.update("DELETE FROM sp_analysis_run WHERE analysis_run_id = ?", runId);
    }

    /** FK reverse order (context/params/step -> job execution -> job instance), this JobInstance only. */
    private void deleteBatchMetadata(String inputVersion) {
        List<Long> instanceIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT je.JOB_INSTANCE_ID FROM BATCH_JOB_EXECUTION je "
                        + "JOIN BATCH_JOB_EXECUTION_PARAMS p ON p.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID "
                        + "WHERE p.PARAMETER_NAME = 'inputSnapshotVersion' AND p.PARAMETER_VALUE = ?",
                Long.class, inputVersion);
        for (Long instanceId : instanceIds) {
            List<Long> executionIds = jdbcTemplate.queryForList(
                    "SELECT JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID = ?", Long.class, instanceId);
            for (Long executionId : executionIds) {
                jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT WHERE STEP_EXECUTION_ID IN "
                        + "(SELECT STEP_EXECUTION_ID FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID = ?)", executionId);
                jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID = ?", executionId);
                jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT WHERE JOB_EXECUTION_ID = ?", executionId);
                jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID = ?", executionId);
                jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = ?", executionId);
            }
            jdbcTemplate.update("DELETE FROM BATCH_JOB_INSTANCE WHERE JOB_INSTANCE_ID = ?", instanceId);
        }
    }
}
