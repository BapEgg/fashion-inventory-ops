package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.api.error.ApiErrorCode;
import com.bapegg.stockpilot.api.error.ApiException;
import com.bapegg.stockpilot.api.error.ApiFieldError;
import com.bapegg.stockpilot.batch.Mvp2AnalysisJobParameters;
import com.bapegg.stockpilot.demand.DemandAnalysisRules;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Synchronously launches {@code mvp2AnalysisJob} for one (analysisDate, inputSnapshotVersion)
 * pair, per current-task.md section 2: build canonical parameters, always call
 * {@code JobOperator.start} (never short-circuit on a pre-read Batch/domain check, so a
 * Batch-metadata-behind-domain repair case still runs), then re-read the exact domain row as the
 * one source of truth for the response. The current {@code JobOperator} is synchronous, so this
 * method returns only once the whole analysis has actually finished (or failed).
 */
@Service
public class Mvp2AnalysisApplicationService {

    private static final String ALREADY_COMPLETED_EXIT_CODE = "ALREADY_COMPLETED";

    private final JobOperator jobOperator;
    private final Job mvp2AnalysisJob;
    private final SpAnalysisRunRepository analysisRunRepository;
    private final AnalysisLaunchFailureClassifier failureClassifier;

    public Mvp2AnalysisApplicationService(
            JobOperator jobOperator,
            @Qualifier("mvp2AnalysisJob") Job mvp2AnalysisJob,
            SpAnalysisRunRepository analysisRunRepository,
            AnalysisLaunchFailureClassifier failureClassifier) {
        this.jobOperator = jobOperator;
        this.mvp2AnalysisJob = mvp2AnalysisJob;
        this.analysisRunRepository = analysisRunRepository;
        this.failureClassifier = failureClassifier;
    }

    public Mvp2AnalysisLaunchOutcome launch(LocalDate analysisDate, String inputSnapshotVersion) {
        Mvp2AnalysisJobParameters parameters = buildParameters(analysisDate, inputSnapshotVersion);
        boolean existedBefore = findDomainRun(analysisDate, inputSnapshotVersion).isPresent();

        JobParameters batchParameters = parameters.toJobParameters();
        boolean alreadyCompleted = startAndAwaitCompletion(batchParameters);

        SpAnalysisRun run = findDomainRun(analysisDate, inputSnapshotVersion)
                .orElseThrow(() -> new ApiException(ApiErrorCode.INTERNAL_SERVER_ERROR,
                        "Batch reported completion but no domain analysis run was found."));
        if (run.getRunStatus() != AnalysisRunStatus.COMPLETED) {
            throw new ApiException(ApiErrorCode.INTERNAL_SERVER_ERROR,
                    "Batch reported completion but the domain analysis run is " + run.getRunStatus() + ".");
        }

        // Per the P1 finding: a request whose pre-read found no row can still turn out to be a
        // replay if a concurrent request completed the exact same triple between that read and
        // this call's own JobOperator.start -- alreadyCompleted, not existedBefore, is therefore
        // the deciding signal for "was this actually a fresh creation" in every case.
        boolean created = !existedBefore && !alreadyCompleted;
        return new Mvp2AnalysisLaunchOutcome(run, created, alreadyCompleted);
    }

    /**
     * Isolates the try/catch around {@code JobOperator.start} itself from the post-return status
     * check below: {@link ApiException} extends {@link RuntimeException}, so a
     * {@code classifyFailedExecution}/{@code classifyThrown} throw must never sit inside a
     * {@code catch (RuntimeException e)} block of its own, or it would immediately be re-caught
     * and re-classified into an unrelated (and here, unstubbed-in-tests) result.
     */
    private boolean startAndAwaitCompletion(JobParameters batchParameters) {
        JobExecution execution;
        try {
            execution = jobOperator.start(mvp2AnalysisJob, batchParameters);
        } catch (JobInstanceAlreadyCompleteException e) {
            // Per section 3: Batch already-complete is only a valid replay if the domain row it
            // supposedly completed for actually agrees -- checked by the caller immediately after
            // this method returns, not assumed true here.
            return true;
        } catch (JobExecutionAlreadyRunningException | JobRestartException | InvalidJobParametersException e) {
            throw failureClassifier.classifyThrown(e);
        } catch (RuntimeException e) {
            throw failureClassifier.classifyThrown(e);
        }

        if (execution.getStatus() != BatchStatus.COMPLETED) {
            throw failureClassifier.classifyFailedExecution(execution);
        }
        return execution.getStepExecutions().stream()
                .anyMatch(stepExecution -> ALREADY_COMPLETED_EXIT_CODE.equals(stepExecution.getExitStatus().getExitCode()));
    }

    private java.util.Optional<SpAnalysisRun> findDomainRun(LocalDate analysisDate, String inputSnapshotVersion) {
        return analysisRunRepository.findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(
                analysisDate, inputSnapshotVersion, DemandAnalysisRules.RULE_VERSION);
    }

    /**
     * {@code analysisDate}/{@code ruleVersion} are already validated by the request DTO and this
     * server's own constant respectively, so a failure here means a validation-rule mismatch
     * between the DTO and this record -- {@code inputSnapshotVersion} (client-controlled) maps to
     * a validation error carrying a proper {@code inputSnapshotVersion} field error (per the P2
     * finding: Bean Validation cannot express this record's own blank/whitespace/length rules
     * itself, but the resulting failure must still surface exactly like one that could), anything
     * else is this server's own bug (internal error).
     */
    private Mvp2AnalysisJobParameters buildParameters(LocalDate analysisDate, String inputSnapshotVersion) {
        try {
            return new Mvp2AnalysisJobParameters(analysisDate, inputSnapshotVersion, DemandAnalysisRules.RULE_VERSION);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.startsWith("inputSnapshotVersion")) {
                throw new ApiException(ApiErrorCode.VALIDATION_ERROR, message,
                        List.of(new ApiFieldError("inputSnapshotVersion", inputSnapshotVersionFieldErrorCode(message), message)));
            }
            if (message.startsWith("analysisDate")) {
                throw new ApiException(ApiErrorCode.VALIDATION_ERROR, message,
                        List.of(new ApiFieldError("analysisDate", "REQUIRED", message)));
            }
            throw new ApiException(ApiErrorCode.INTERNAL_SERVER_ERROR, message, e);
        }
    }

    /** Mirrors {@link Mvp2AnalysisJobParameters}'s own exact message wording for each distinct violation kind. */
    private static String inputSnapshotVersionFieldErrorCode(String message) {
        if (message.contains("leading or trailing whitespace")) {
            return "FORMAT";
        }
        if (message.contains("at most") && message.contains("characters")) {
            return "SIZE";
        }
        return "REQUIRED";
    }

    public record Mvp2AnalysisLaunchOutcome(SpAnalysisRun run, boolean created, boolean alreadyCompleted) {
    }
}
