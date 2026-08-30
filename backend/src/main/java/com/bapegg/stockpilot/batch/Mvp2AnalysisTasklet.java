package com.bapegg.stockpilot.batch;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Single-call bridge from one Batch {@code StepExecution}'s {@link JobParameters} to
 * {@link Mvp2AnalysisExecutor#execute}, per current-task.md section 2. Depends on nothing but the
 * executor -- no calculation, entity conversion, persistence or retry logic is duplicated here.
 * <p>
 * Deliberately does not catch or translate any exception the executor throws: an input-contract
 * violation, an output-contract violation or {@link Mvp2RunAlreadyRunningException} must fail this
 * Step (and its Job) exactly as Spring Batch fails any other uncaught Tasklet exception, while the
 * corresponding domain {@code FAILED} bookkeeping stays entirely the executor/lifecycle's own
 * responsibility.
 */
@Component
public class Mvp2AnalysisTasklet implements Tasklet {

    private static final ExitStatus ALREADY_COMPLETED = new ExitStatus("ALREADY_COMPLETED");

    private final Mvp2AnalysisExecutor executor;

    public Mvp2AnalysisTasklet(Mvp2AnalysisExecutor executor) {
        this.executor = executor;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws InvalidJobParametersException {
        JobParameters parameters = chunkContext.getStepContext().getStepExecution().getJobParameters();
        Mvp2AnalysisJobParameters jobParameters = Mvp2AnalysisJobParameters.from(parameters);

        Mvp2ExecutionOutcome outcome = executor.execute(
                jobParameters.analysisDate(), jobParameters.inputSnapshotVersion(), jobParameters.ruleVersion());

        if (outcome.alreadyCompleted()) {
            contribution.setExitStatus(ALREADY_COMPLETED);
        }
        return RepeatStatus.FINISHED;
    }
}
