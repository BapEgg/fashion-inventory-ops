package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.api.error.ApiErrorCode;
import com.bapegg.stockpilot.api.error.ApiException;
import com.bapegg.stockpilot.demand.DemandAnalysisRules;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link Mvp2AnalysisApplicationService}, per current-task.md's Required
 * tests item 2. Mocked {@link JobOperator}/{@link SpAnalysisRunRepository}/
 * {@link AnalysisLaunchFailureClassifier} -- no Spring context, no Oracle.
 */
class Mvp2AnalysisApplicationServiceTest {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 10, 25);
    private static final String INPUT_SNAPSHOT_VERSION = "MVP2-APPSVC-V1";
    private static final String RULE_VERSION = DemandAnalysisRules.RULE_VERSION;

    private final JobOperator jobOperator = mock(JobOperator.class);
    private final Job mvp2AnalysisJob = mock(Job.class);
    private final SpAnalysisRunRepository analysisRunRepository = mock(SpAnalysisRunRepository.class);
    private final AnalysisLaunchFailureClassifier failureClassifier = mock(AnalysisLaunchFailureClassifier.class);
    private final Mvp2AnalysisApplicationService service =
            new Mvp2AnalysisApplicationService(jobOperator, mvp2AnalysisJob, analysisRunRepository, failureClassifier);

    @Test
    void launchesWithTheExactCanonicalParameters() throws Exception {
        when(analysisRunRepository.findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(
                ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(Optional.empty(), Optional.of(completedRun()));
        JobParameters expected = new com.bapegg.stockpilot.batch.Mvp2AnalysisJobParameters(
                ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION).toJobParameters();
        when(jobOperator.start(eq(mvp2AnalysisJob), eq(expected))).thenReturn(completedExecution(false));

        service.launch(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION);

        verify(jobOperator).start(eq(mvp2AnalysisJob), eq(expected));
    }

    @Test
    void aNewDomainRunIsReportedAsCreatedAndNotAlreadyCompleted() throws Exception {
        when(analysisRunRepository.findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(
                ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(Optional.empty(), Optional.of(completedRun()));
        when(jobOperator.start(any(Job.class), any(JobParameters.class))).thenReturn(completedExecution(false));

        Mvp2AnalysisApplicationService.Mvp2AnalysisLaunchOutcome outcome =
                service.launch(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION);

        assertTrue(outcome.created());
        assertFalse(outcome.alreadyCompleted());
    }

    @Test
    void aPreExistingDomainRowIsReportedAsRestartedNotCreated() throws Exception {
        when(analysisRunRepository.findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(
                ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(Optional.of(failedRun()), Optional.of(completedRun()));
        when(jobOperator.start(any(Job.class), any(JobParameters.class))).thenReturn(completedExecution(false));

        Mvp2AnalysisApplicationService.Mvp2AnalysisLaunchOutcome outcome =
                service.launch(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION);

        assertFalse(outcome.created(), "A restarted (previously FAILED) run must not be reported as created.");
        assertFalse(outcome.alreadyCompleted());
    }

    @Test
    void aJobInstanceAlreadyCompleteExceptionIsAReplayWhenTheDomainRowAgrees() throws Exception {
        when(analysisRunRepository.findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(
                ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(Optional.of(completedRun()), Optional.of(completedRun()));
        when(jobOperator.start(any(Job.class), any(JobParameters.class))).thenThrow(new JobInstanceAlreadyCompleteException("already complete"));

        Mvp2AnalysisApplicationService.Mvp2AnalysisLaunchOutcome outcome =
                service.launch(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION);

        assertFalse(outcome.created());
        assertTrue(outcome.alreadyCompleted());
    }

    /**
     * The P1 race finding: the pre-launch read found no row at all (this request looked first),
     * yet a concurrent request completed the exact same triple before this one's own
     * {@code JobOperator.start} call, so this request receives
     * {@link JobInstanceAlreadyCompleteException} even though {@code existedBefore} was false.
     * The outcome must still be a replay (200/{@code alreadyCompleted=true}), never
     * {@code created=true}/201.
     */
    @Test
    void aReplayRacingAnEmptyPreReadIsNeverReportedAsCreated() throws Exception {
        when(analysisRunRepository.findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(
                ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(Optional.empty(), Optional.of(completedRun()));
        when(jobOperator.start(any(Job.class), any(JobParameters.class)))
                .thenThrow(new JobInstanceAlreadyCompleteException("completed by a concurrent request"));

        Mvp2AnalysisApplicationService.Mvp2AnalysisLaunchOutcome outcome =
                service.launch(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION);

        assertFalse(outcome.created(), "A replay must never be reported as created, regardless of what the pre-read saw.");
        assertTrue(outcome.alreadyCompleted());
    }

    @Test
    void aStepExitCodeOfAlreadyCompletedIsAReplayEvenWhenStartReturnsNormally() throws Exception {
        when(analysisRunRepository.findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(
                ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(Optional.of(completedRun()), Optional.of(completedRun()));
        when(jobOperator.start(any(Job.class), any(JobParameters.class))).thenReturn(completedExecution(true));

        Mvp2AnalysisApplicationService.Mvp2AnalysisLaunchOutcome outcome =
                service.launch(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION);

        assertTrue(outcome.alreadyCompleted());
    }

    @Test
    void batchCompletedButNoDomainRunIsAnInternalServerError() throws Exception {
        when(analysisRunRepository.findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(
                ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(Optional.empty(), Optional.empty());
        when(jobOperator.start(any(Job.class), any(JobParameters.class))).thenReturn(completedExecution(false));

        ApiException e = assertThrows(ApiException.class, () -> service.launch(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION));
        assertEquals(ApiErrorCode.INTERNAL_SERVER_ERROR, e.code());
    }

    @Test
    void batchCompletedButDomainRunIsNotCompletedIsAnInternalServerError() throws Exception {
        when(analysisRunRepository.findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(
                ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(Optional.empty(), Optional.of(failedRun()));
        when(jobOperator.start(any(Job.class), any(JobParameters.class))).thenReturn(completedExecution(false));

        ApiException e = assertThrows(ApiException.class, () -> service.launch(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION));
        assertEquals(ApiErrorCode.INTERNAL_SERVER_ERROR, e.code());
    }

    @Test
    void aJobExecutionAlreadyRunningExceptionDelegatesToTheClassifier() throws Exception {
        JobExecutionAlreadyRunningException thrown = new JobExecutionAlreadyRunningException("running");
        ApiException classified = new ApiException(ApiErrorCode.ANALYSIS_ALREADY_RUNNING, "already running");
        when(analysisRunRepository.findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(
                ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION)).thenReturn(Optional.empty());
        when(jobOperator.start(any(Job.class), any(JobParameters.class))).thenThrow(thrown);
        when(failureClassifier.classifyThrown(thrown)).thenReturn(classified);

        ApiException e = assertThrows(ApiException.class, () -> service.launch(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION));
        assertSame(classified, e);
    }

    @Test
    void aFailedBatchStatusDelegatesToTheClassifiersFailedExecutionPath() throws Exception {
        JobExecution failed = executionWithStatus(BatchStatus.FAILED);
        ApiException classified = new ApiException(ApiErrorCode.ANALYSIS_EXECUTION_FAILED, "failed");
        when(analysisRunRepository.findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(
                ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION)).thenReturn(Optional.empty());
        when(jobOperator.start(any(Job.class), any(JobParameters.class))).thenReturn(failed);
        when(failureClassifier.classifyFailedExecution(failed)).thenReturn(classified);

        ApiException e = assertThrows(ApiException.class, () -> service.launch(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION));
        assertSame(classified, e);
    }

    @Test
    void aBlankInputSnapshotVersionCarriesARequiredFieldError() {
        ApiException e = assertThrows(ApiException.class, () -> service.launch(ANALYSIS_DATE, "   "));
        assertEquals(ApiErrorCode.VALIDATION_ERROR, e.code());
        assertEquals(1, e.fieldErrors().size());
        assertEquals("inputSnapshotVersion", e.fieldErrors().get(0).field());
        assertEquals("REQUIRED", e.fieldErrors().get(0).code());
    }

    @Test
    void anInputSnapshotVersionWithOuterWhitespaceCarriesAFormatFieldError() {
        ApiException e = assertThrows(ApiException.class, () -> service.launch(ANALYSIS_DATE, " " + INPUT_SNAPSHOT_VERSION));
        assertEquals("inputSnapshotVersion", e.fieldErrors().get(0).field());
        assertEquals("FORMAT", e.fieldErrors().get(0).code());
    }

    @Test
    void anInputSnapshotVersionLongerThanSixtyFourCharactersCarriesASizeFieldError() {
        ApiException e = assertThrows(ApiException.class, () -> service.launch(ANALYSIS_DATE, "V".repeat(65)));
        assertEquals("inputSnapshotVersion", e.fieldErrors().get(0).field());
        assertEquals("SIZE", e.fieldErrors().get(0).code());
    }

    private static SpAnalysisRun completedRun() {
        SpAnalysisRun run = new SpAnalysisRun(ANALYSIS_DATE, RULE_VERSION, INPUT_SNAPSHOT_VERSION);
        run.markCompleted();
        return run;
    }

    private static SpAnalysisRun failedRun() {
        SpAnalysisRun run = new SpAnalysisRun(ANALYSIS_DATE, RULE_VERSION, INPUT_SNAPSHOT_VERSION);
        run.markFailed();
        return run;
    }

    private static JobExecution completedExecution(boolean alreadyCompletedExit) {
        JobExecution execution = executionWithStatus(BatchStatus.COMPLETED);
        StepExecution stepExecution = new StepExecution(1L, "mvp2AnalysisStep", execution);
        stepExecution.setExitStatus(alreadyCompletedExit ? new ExitStatus("ALREADY_COMPLETED") : ExitStatus.COMPLETED);
        execution.addStepExecution(stepExecution);
        return execution;
    }

    private static JobExecution executionWithStatus(BatchStatus status) {
        JobInstance jobInstance = new JobInstance(1L, "mvp2AnalysisJob");
        JobExecution execution = new JobExecution(1L, jobInstance, new JobParameters());
        execution.setStatus(status);
        return execution;
    }
}
