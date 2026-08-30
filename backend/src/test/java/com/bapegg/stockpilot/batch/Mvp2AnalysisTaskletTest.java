package com.bapegg.stockpilot.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link Mvp2AnalysisTasklet}, per current-task.md's Required tests item 2.
 * Builds real (but disconnected) Spring Batch domain objects directly -- {@link JobInstance},
 * {@link JobExecution}, {@link StepExecution}, {@link StepContext}, {@link ChunkContext} -- rather
 * than a Spring context, since all four are plain constructible POJOs.
 */
class Mvp2AnalysisTaskletTest {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 9, 30);
    private static final String INPUT_SNAPSHOT_VERSION = "MVP-2-GS-V1";
    private static final String RULE_VERSION = "MVP-2";

    private final Mvp2AnalysisExecutor executor = mock(Mvp2AnalysisExecutor.class);
    private final Mvp2AnalysisTasklet tasklet = new Mvp2AnalysisTasklet(executor);

    @Test
    void callsTheExecutorExactlyOnceWithTheParsedArgumentsAndReturnsFinished() throws Exception {
        when(executor.execute(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(new Mvp2ExecutionOutcome(42L, false));
        StepContribution contribution = contributionFor(jobParameters());

        RepeatStatus status = tasklet.execute(contribution, chunkContextFor(contribution));

        assertEquals(RepeatStatus.FINISHED, status);
        verify(executor, times(1)).execute(eq(ANALYSIS_DATE), eq(INPUT_SNAPSHOT_VERSION), eq(RULE_VERSION));
        assertEquals(ExitStatus.EXECUTING, contribution.getExitStatus(),
                "A fresh (non-already-completed) run must not override the default exit status.");
    }

    @Test
    void anAlreadyCompletedOutcomeSetsTheExitStatusToAlreadyCompleted() throws Exception {
        when(executor.execute(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION))
                .thenReturn(new Mvp2ExecutionOutcome(42L, true));
        StepContribution contribution = contributionFor(jobParameters());

        RepeatStatus status = tasklet.execute(contribution, chunkContextFor(contribution));

        assertEquals(RepeatStatus.FINISHED, status);
        assertEquals("ALREADY_COMPLETED", contribution.getExitStatus().getExitCode());
    }

    @Test
    void anExecutorExceptionPropagatesUnchangedWithoutBeingCaught() {
        RuntimeException failure = new Mvp2RunAlreadyRunningException("simulated already-running failure");
        when(executor.execute(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION)).thenThrow(failure);
        StepContribution contribution = contributionFor(jobParameters());

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> tasklet.execute(contribution, chunkContextFor(contribution)));

        assertSame(failure, thrown);
    }

    @Test
    void anInvalidJobParametersShapeIsRejectedBeforeTheExecutorIsCalled() {
        JobParameters missingRuleVersion = new org.springframework.batch.core.job.parameters.JobParametersBuilder()
                .addLocalDate("analysisDate", ANALYSIS_DATE, true)
                .addString("inputSnapshotVersion", INPUT_SNAPSHOT_VERSION, true)
                .toJobParameters();
        StepContribution contribution = contributionFor(missingRuleVersion);

        assertThrows(org.springframework.batch.core.job.parameters.InvalidJobParametersException.class,
                () -> tasklet.execute(contribution, chunkContextFor(contribution)));

        org.mockito.Mockito.verifyNoInteractions(executor);
    }

    private static JobParameters jobParameters() {
        return new Mvp2AnalysisJobParameters(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION).toJobParameters();
    }

    private static StepContribution contributionFor(JobParameters parameters) {
        JobInstance jobInstance = new JobInstance(1L, "mvp2AnalysisJob");
        JobExecution jobExecution = new JobExecution(1L, jobInstance, parameters);
        StepExecution stepExecution = new StepExecution(1L, "mvp2AnalysisStep", jobExecution);
        return new StepContribution(stepExecution);
    }

    private static ChunkContext chunkContextFor(StepContribution contribution) {
        return new ChunkContext(new StepContext(contribution.getStepExecution()));
    }
}
