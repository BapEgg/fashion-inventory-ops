package com.bapegg.stockpilot.analysis;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * Launches {@code inventoryAnalysisJob} for one analysis date. Idempotency is Spring
 * Batch's own: relaunching JobParameters that already completed throws
 * {@link JobInstanceAlreadyCompleteException}, which this service treats as a
 * successful no-op rather than an error, so {@code POST /api/analyses} is safe to call
 * more than once for the same date.
 * <p>
 * {@code alreadyCompleted} in the outcome must not be derived solely from that
 * exception: the domain {@link SpAnalysisRun} row is a separate source of truth from
 * Spring Batch's own JobInstance metadata (e.g. after a crash between the tasklet's
 * commit and Spring Batch recording its own completion, or any other reason the two
 * fall out of sync). If a completed domain run already existed before this call, the
 * Job still launches and completes as a tasklet no-op (per
 * {@code InventoryAnalysisTasklet}'s own check) rather than throwing, so that case is
 * checked directly against the domain table first.
 */
@Service
public class AnalysisRunService {

    private final JobOperator jobOperator;
    private final Job inventoryAnalysisJob;
    private final SpAnalysisRunRepository analysisRunRepository;

    public AnalysisRunService(
            JobOperator jobOperator, Job inventoryAnalysisJob, SpAnalysisRunRepository analysisRunRepository) {
        this.jobOperator = jobOperator;
        this.inventoryAnalysisJob = inventoryAnalysisJob;
        this.analysisRunRepository = analysisRunRepository;
    }

    public AnalysisRunOutcome runAnalysis(LocalDate analysisDate) {
        boolean alreadyCompleted = analysisRunRepository
                .findByAnalysisDateAndRuleVersion(analysisDate, InventoryAnalysisRules.RULE_VERSION)
                .isPresent();

        JobParameters parameters = new JobParametersBuilder()
                .addLocalDate("analysisDate", analysisDate)
                .addString("ruleVersion", InventoryAnalysisRules.RULE_VERSION)
                .toJobParameters();

        try {
            JobExecution execution = jobOperator.start(inventoryAnalysisJob, parameters);
            if (execution.getStatus() != BatchStatus.COMPLETED) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Analysis job did not complete: status=" + execution.getStatus());
            }
        } catch (JobInstanceAlreadyCompleteException e) {
            alreadyCompleted = true;
        } catch (JobExecutionAlreadyRunningException | JobRestartException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Analysis for this date and rule version is already running.", e);
        } catch (InvalidJobParametersException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid analysis parameters.", e);
        }

        SpAnalysisRun run = analysisRunRepository
                .findByAnalysisDateAndRuleVersion(analysisDate, InventoryAnalysisRules.RULE_VERSION)
                .orElseThrow(() -> new IllegalStateException(
                        "Analysis job reported completion but no SpAnalysisRun was persisted."));
        return new AnalysisRunOutcome(run, alreadyCompleted);
    }

    public record AnalysisRunOutcome(SpAnalysisRun analysisRun, boolean alreadyCompleted) {
    }
}
