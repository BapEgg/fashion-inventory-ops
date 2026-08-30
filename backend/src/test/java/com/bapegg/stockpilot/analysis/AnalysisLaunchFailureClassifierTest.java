package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.api.error.ApiErrorCode;
import com.bapegg.stockpilot.api.error.ApiException;
import com.bapegg.stockpilot.batch.InputContractViolationException;
import com.bapegg.stockpilot.batch.Mvp2RunAlreadyRunningException;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure unit tests for {@link AnalysisLaunchFailureClassifier}'s priority list, per
 * current-task.md section 3. No Spring context, no Oracle -- {@link JobExecution}/
 * {@link JobInstance} are plain constructible POJOs.
 */
class AnalysisLaunchFailureClassifierTest {

    private final AnalysisLaunchFailureClassifier classifier = new AnalysisLaunchFailureClassifier();

    @Test
    void classifiesJobExecutionAlreadyRunningAsAnalysisAlreadyRunning() {
        ApiException e = classifier.classifyThrown(new JobExecutionAlreadyRunningException("already running"));
        assertEquals(ApiErrorCode.ANALYSIS_ALREADY_RUNNING, e.code());
    }

    @Test
    void classifiesAnOracleSerializationConflictAsAnalysisLaunchConflict() {
        SQLException sqlException = new SQLException("ORA-08177", "72000", 8177);
        ApiException e = classifier.classifyThrown(new UncategorizedSQLException("insert", "sql", sqlException));
        assertEquals(ApiErrorCode.ANALYSIS_LAUNCH_CONFLICT, e.code());
    }

    @Test
    void classifiesJobRestartExceptionAsAnalysisRestartUnavailable() {
        ApiException e = classifier.classifyThrown(new JobRestartException("cannot restart"));
        assertEquals(ApiErrorCode.ANALYSIS_RESTART_UNAVAILABLE, e.code());
    }

    @Test
    void classifiesAConnectionFailureAsPersistenceUnavailable() {
        ApiException e = classifier.classifyThrown(new DataAccessResourceFailureException("connection lost"));
        assertEquals(ApiErrorCode.PERSISTENCE_UNAVAILABLE, e.code());
    }

    @Test
    void classifiesARepositoryConnectionFailureAsPersistenceUnavailable() {
        ApiException e = classifier.classifyDataAccess(new DataAccessResourceFailureException("connection lost"));
        assertEquals(ApiErrorCode.PERSISTENCE_UNAVAILABLE, e.code());
    }

    @Test
    void classifiesANonConnectionRepositoryFailureAsInternalServerError() {
        ApiException e = classifier.classifyDataAccess(new DataIntegrityViolationException("unexpected constraint failure"));
        assertEquals(ApiErrorCode.INTERNAL_SERVER_ERROR, e.code());
    }

    @Test
    void classifiesAnUnrelatedRuntimeExceptionAsInternalServerError() {
        ApiException e = classifier.classifyThrown(new IllegalStateException("bug"));
        assertEquals(ApiErrorCode.INTERNAL_SERVER_ERROR, e.code());
    }

    @Test
    void classifiesAServerCreatedInvalidJobParametersAsInternalServerError() {
        ApiException e = classifier.classifyThrown(new InvalidJobParametersException("should never happen"));
        assertEquals(ApiErrorCode.INTERNAL_SERVER_ERROR, e.code());
    }

    /**
     * Per the P2 finding: the directly-thrown path did not classify
     * {@link InputContractViolationException} at all before this fix, even nested one level deep
     * under an unrelated wrapper.
     */
    @Test
    void classifiesAThrownExceptionWithANestedInputContractViolationAsAnalysisInputInvalid() {
        RuntimeException wrapped = new RuntimeException("wrapper", new InputContractViolationException("no anchors"));
        ApiException e = classifier.classifyThrown(wrapped);
        assertEquals(ApiErrorCode.ANALYSIS_INPUT_INVALID, e.code());
    }

    /**
     * Per the P2 finding: connection-failure detection previously only read an
     * {@link SQLException}'s SQLState when it was the *immediate* cause of a
     * {@code DataAccessException} node -- this must instead find it at any depth, under any kind
     * of wrapper.
     */
    @Test
    void classifiesAThrownExceptionWithADeeplyNestedSqlState08AsPersistenceUnavailable() {
        SQLException connectionFailure = new SQLException("connection refused", "08001");
        RuntimeException wrapped = new RuntimeException("outer", new RuntimeException("middle", connectionFailure));
        ApiException e = classifier.classifyThrown(wrapped);
        assertEquals(ApiErrorCode.PERSISTENCE_UNAVAILABLE, e.code());
    }

    @Test
    void classifiesAFailedExecutionWithADeeplyNestedSqlState08AsPersistenceUnavailable() {
        SQLException connectionFailure = new SQLException("connection refused", "08001");
        RuntimeException wrapped = new RuntimeException("outer", new RuntimeException("middle", connectionFailure));
        JobExecution execution = failedExecution(BatchStatus.FAILED);
        execution.addFailureException(wrapped);

        ApiException e = classifier.classifyFailedExecution(execution);
        assertEquals(ApiErrorCode.PERSISTENCE_UNAVAILABLE, e.code());
    }

    @Test
    void classifiesAFailedExecutionCarryingMvp2RunAlreadyRunningAsAnalysisAlreadyRunning() {
        JobExecution execution = failedExecution(BatchStatus.FAILED);
        execution.addFailureException(new Mvp2RunAlreadyRunningException("already running"));

        ApiException e = classifier.classifyFailedExecution(execution);
        assertEquals(ApiErrorCode.ANALYSIS_ALREADY_RUNNING, e.code());
    }

    @Test
    void classifiesAFailedExecutionCarryingInputContractViolationAsAnalysisInputInvalid() {
        JobExecution execution = failedExecution(BatchStatus.FAILED);
        execution.addFailureException(new InputContractViolationException("no anchors"));

        ApiException e = classifier.classifyFailedExecution(execution);
        assertEquals(ApiErrorCode.ANALYSIS_INPUT_INVALID, e.code());
    }

    @Test
    void classifiesAStoppedExecutionAsAnalysisRestartUnavailableEvenWithNoFailureException() {
        JobExecution execution = failedExecution(BatchStatus.STOPPED);

        ApiException e = classifier.classifyFailedExecution(execution);
        assertEquals(ApiErrorCode.ANALYSIS_RESTART_UNAVAILABLE, e.code());
    }

    @Test
    void classifiesAnAbandonedExecutionAsAnalysisRestartUnavailable() {
        JobExecution execution = failedExecution(BatchStatus.ABANDONED);

        ApiException e = classifier.classifyFailedExecution(execution);
        assertEquals(ApiErrorCode.ANALYSIS_RESTART_UNAVAILABLE, e.code());
    }

    @Test
    void classifiesAnUnclassifiedFailedExecutionAsAnalysisExecutionFailed() {
        JobExecution execution = failedExecution(BatchStatus.FAILED);
        execution.addFailureException(new RuntimeException("some other bug"));

        ApiException e = classifier.classifyFailedExecution(execution);
        assertEquals(ApiErrorCode.ANALYSIS_EXECUTION_FAILED, e.code());
    }

    private static JobExecution failedExecution(BatchStatus status) {
        JobInstance jobInstance = new JobInstance(1L, "mvp2AnalysisJob");
        JobExecution execution = new JobExecution(1L, jobInstance, new JobParameters());
        execution.setStatus(status);
        return execution;
    }
}
