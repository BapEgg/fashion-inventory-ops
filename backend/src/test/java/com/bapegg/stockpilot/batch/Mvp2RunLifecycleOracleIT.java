package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.analysis.AnalysisRunStatus;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link Mvp2RunLifecycleService}/{@link Mvp2RunLifecycleTransactions}' run claim and
 * state transitions against the real Oracle instance, per current-task.md section 3. Every
 * {@code claim}/{@code markFailed} call runs in its own {@code REQUIRES_NEW} transaction and
 * therefore commits independently and immediately -- there is no surrounding
 * {@code @Transactional} to roll this class's writes back, so each test uses its own unique
 * {@code (analysisDate, inputSnapshotVersion, ruleVersion)} natural key AND deletes its own run
 * row in a {@code finally} block, so the test stays safely re-runnable rather than finding its own
 * previous run already sitting in a non-{@code STARTED}-eligible state. Skipped (not failed) when
 * DB_URL is not set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class Mvp2RunLifecycleOracleIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 8, 28);

    @Autowired
    private Mvp2RunLifecycleService lifecycleService;

    @Autowired
    private SpAnalysisRunRepository analysisRunRepository;

    @Test
    void aNewNaturalKeyIsClaimedAsStartedAndPersistedAsRunning() {
        Long runId = null;
        try {
            Mvp2RunClaim claim = lifecycleService.claim(ANALYSIS_DATE, "MVP2-LC-NEW-V1", "MVP2-LC-NEW");
            runId = claim.runId();

            assertEquals(Mvp2RunClaimStatus.STARTED, claim.status());
            SpAnalysisRun run = analysisRunRepository.findById(claim.runId()).orElseThrow();
            assertEquals(AnalysisRunStatus.RUNNING, run.getRunStatus());
            assertNull(run.getCompletedAt());
        } finally {
            deleteRun(runId);
        }
    }

    @Test
    void aRunningNaturalKeyIsClaimedAsAlreadyRunningWithoutChangingState() {
        Long runId = null;
        try {
            Mvp2RunClaim first = lifecycleService.claim(ANALYSIS_DATE, "MVP2-LC-RUN-V1", "MVP2-LC-RUN");
            runId = first.runId();

            Mvp2RunClaim second = lifecycleService.claim(ANALYSIS_DATE, "MVP2-LC-RUN-V1", "MVP2-LC-RUN");

            assertEquals(Mvp2RunClaimStatus.ALREADY_RUNNING, second.status());
            assertEquals(first.runId(), second.runId());
            SpAnalysisRun run = analysisRunRepository.findById(first.runId()).orElseThrow();
            assertEquals(AnalysisRunStatus.RUNNING, run.getRunStatus());
        } finally {
            deleteRun(runId);
        }
    }

    @Test
    void aCompletedNaturalKeyIsClaimedAsAlreadyCompletedWithoutChangingState() {
        Long runId = null;
        try {
            Mvp2RunClaim claim = lifecycleService.claim(ANALYSIS_DATE, "MVP2-LC-DONE-V1", "MVP2-LC-DONE");
            runId = claim.runId();
            SpAnalysisRun run = analysisRunRepository.findById(claim.runId()).orElseThrow();
            run.markCompleted();
            analysisRunRepository.saveAndFlush(run);

            Mvp2RunClaim replay = lifecycleService.claim(ANALYSIS_DATE, "MVP2-LC-DONE-V1", "MVP2-LC-DONE");

            assertEquals(Mvp2RunClaimStatus.ALREADY_COMPLETED, replay.status());
            assertEquals(claim.runId(), replay.runId());
            SpAnalysisRun reloaded = analysisRunRepository.findById(claim.runId()).orElseThrow();
            assertEquals(AnalysisRunStatus.COMPLETED, reloaded.getRunStatus());
        } finally {
            deleteRun(runId);
        }
    }

    @Test
    void aFailedNaturalKeyIsRestartedInPlaceWithTheSameIdAndOriginalStartedAt() {
        Long runId = null;
        try {
            Mvp2RunClaim claim = lifecycleService.claim(ANALYSIS_DATE, "MVP2-LC-FAIL-V1", "MVP2-LC-FAIL");
            runId = claim.runId();
            lifecycleService.markFailed(claim.runId());
            SpAnalysisRun failed = analysisRunRepository.findById(claim.runId()).orElseThrow();
            assertEquals(AnalysisRunStatus.FAILED, failed.getRunStatus());
            OffsetDateTime originalStartedAt = failed.getStartedAt();

            Mvp2RunClaim restarted = lifecycleService.claim(ANALYSIS_DATE, "MVP2-LC-FAIL-V1", "MVP2-LC-FAIL");

            assertEquals(Mvp2RunClaimStatus.STARTED, restarted.status());
            assertEquals(claim.runId(), restarted.runId());
            SpAnalysisRun reloaded = analysisRunRepository.findById(claim.runId()).orElseThrow();
            assertEquals(AnalysisRunStatus.RUNNING, reloaded.getRunStatus());
            assertNull(reloaded.getCompletedAt());
            assertEquals(originalStartedAt, reloaded.getStartedAt());
        } finally {
            deleteRun(runId);
        }
    }

    @Test
    void aConcurrentInsertRaceAgainstAPreExistingFailedRowIsResolvedInANewTransaction() {
        // Simulates the race current-task.md section 2 describes: by the time claim()'s own
        // insert executes, a row for this exact natural key already exists (inserted here
        // directly, bypassing the service) -- the insert must hit uq_sp_analysis_run, and the
        // service must recover by resolving state from a fresh transaction rather than retrying
        // the same insert or inserting a second row.
        Long runId = null;
        try {
            SpAnalysisRun preExisting = analysisRunRepository.saveAndFlush(
                    new SpAnalysisRun(ANALYSIS_DATE, "MVP2-LC-RACE", "MVP2-LC-RACE-V1"));
            runId = preExisting.getAnalysisRunId();
            preExisting.markFailed();
            analysisRunRepository.saveAndFlush(preExisting);

            Mvp2RunClaim claim = lifecycleService.claim(ANALYSIS_DATE, "MVP2-LC-RACE-V1", "MVP2-LC-RACE");

            assertEquals(Mvp2RunClaimStatus.STARTED, claim.status());
            assertEquals(preExisting.getAnalysisRunId(), claim.runId(),
                    "The race must resolve onto the pre-existing row, not insert a second one.");
            SpAnalysisRun reloaded = analysisRunRepository.findById(claim.runId()).orElseThrow();
            assertEquals(AnalysisRunStatus.RUNNING, reloaded.getRunStatus());
        } finally {
            deleteRun(runId);
        }
    }

    @Test
    void twoConcurrentClaimersResolveToOneRunAndOnlyOneStarts() throws Exception {
        String inputVersion = "MVP2-LC-THREAD-RACE-V1";
        String ruleVersion = "MVP2-LC-THREAD-RACE";
        Long runId = null;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Mvp2RunClaim>> futures = java.util.stream.IntStream.range(0, 2)
                    .mapToObj(ignored -> pool.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(5, TimeUnit.SECONDS), "Both claimers must be released together.");
                        return lifecycleService.claim(ANALYSIS_DATE, inputVersion, ruleVersion);
                    }))
                    .toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both claimers must be ready before release.");
            start.countDown();

            Mvp2RunClaim first = futures.get(0).get(10, TimeUnit.SECONDS);
            Mvp2RunClaim second = futures.get(1).get(10, TimeUnit.SECONDS);
            runId = first.runId();

            assertEquals(first.runId(), second.runId());
            assertEquals(
                    Set.of(Mvp2RunClaimStatus.STARTED, Mvp2RunClaimStatus.ALREADY_RUNNING),
                    Set.copyOf(List.of(first.status(), second.status())));
            SpAnalysisRun persisted = analysisRunRepository
                    .findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(
                            ANALYSIS_DATE, inputVersion, ruleVersion)
                    .orElseThrow();
            assertEquals(runId, persisted.getAnalysisRunId());
            assertEquals(AnalysisRunStatus.RUNNING, persisted.getRunStatus());
        } finally {
            pool.shutdownNow();
            if (runId == null) {
                runId = analysisRunRepository
                        .findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(
                                ANALYSIS_DATE, inputVersion, ruleVersion)
                        .map(SpAnalysisRun::getAnalysisRunId)
                        .orElse(null);
            }
            deleteRun(runId);
        }
    }

    @Test
    void markFailedOnlyWorksFromRunningAndRejectsASecondCallInARow() {
        Long runId = null;
        try {
            Mvp2RunClaim claim = lifecycleService.claim(ANALYSIS_DATE, "MVP2-LC-MF-V1", "MVP2-LC-MF");
            runId = claim.runId();
            lifecycleService.markFailed(claim.runId());

            assertThrows(IllegalStateException.class, () -> lifecycleService.markFailed(claim.runId()),
                    "markFailed() must not work twice in a row -- the second call finds FAILED, not RUNNING.");
        } finally {
            deleteRun(runId);
        }
    }

    private void deleteRun(Long runId) {
        if (runId != null) {
            analysisRunRepository.deleteById(runId);
        }
    }
}
