package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.api.error.ApiErrorCode;
import com.bapegg.stockpilot.api.error.ApiException;
import com.bapegg.stockpilot.batch.InputContractViolationException;
import com.bapegg.stockpilot.batch.Mvp2RunAlreadyRunningException;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Classifies an MVP-2 Job launch failure into one stable {@link ApiException}, per
 * current-task.md section 3's priority list. Two distinct shapes of failure reach this class:
 * a {@link RuntimeException} thrown directly out of {@code JobOperator.start} ({@link #classifyThrown}),
 * and a normally-returned {@link JobExecution} whose {@link BatchStatus} is not
 * {@code COMPLETED} ({@link #classifyFailedExecution}) -- the latter's real cause lives in
 * {@link JobExecution#getAllFailureExceptions()}, not in a thrown exception.
 */
@Component
public class AnalysisLaunchFailureClassifier {

    private static final int ORACLE_SERIALIZATION_CONFLICT_ERROR_CODE = 8177;

    /**
     * {@code JobOperator.start}'s own declared exceptions ({@link JobExecutionAlreadyRunningException},
     * {@link JobRestartException}) are checked, but a true first-JobInstance-insert race can also
     * surface as a raw unchecked {@link DataAccessException} straight out of the JDBC DAO -- this
     * accepts the common {@link Exception} supertype so both shapes reach the same classification.
     */
    public ApiException classifyThrown(Exception e) {
        if (e instanceof JobExecutionAlreadyRunningException || contains(e, Mvp2RunAlreadyRunningException.class)) {
            return new ApiException(ApiErrorCode.ANALYSIS_ALREADY_RUNNING, "The JobInstance is already running.", e);
        }
        if (isLaunchConflict(e)) {
            return new ApiException(ApiErrorCode.ANALYSIS_LAUNCH_CONFLICT,
                    "Simultaneous first JobInstance creation conflicted (ORA-8177).", e);
        }
        if (contains(e, InputContractViolationException.class)) {
            return new ApiException(ApiErrorCode.ANALYSIS_INPUT_INVALID, "The versioned input contract was not satisfied.", e);
        }
        if (e instanceof JobRestartException) {
            return new ApiException(ApiErrorCode.ANALYSIS_RESTART_UNAVAILABLE, "The JobInstance cannot be restarted.", e);
        }
        if (isConnectionFailure(e)) {
            return new ApiException(ApiErrorCode.PERSISTENCE_UNAVAILABLE, "Persistence is temporarily unavailable.", e);
        }
        return new ApiException(ApiErrorCode.INTERNAL_SERVER_ERROR, "Unexpected launch failure.", e);
    }

    public ApiException classifyFailedExecution(JobExecution execution) {
        List<Throwable> failures = execution.getAllFailureExceptions();

        for (Throwable failure : failures) {
            if (contains(failure, Mvp2RunAlreadyRunningException.class)) {
                return new ApiException(ApiErrorCode.ANALYSIS_ALREADY_RUNNING, "The domain run is already RUNNING.", failure);
            }
        }
        for (Throwable failure : failures) {
            if (isLaunchConflict(failure)) {
                return new ApiException(ApiErrorCode.ANALYSIS_LAUNCH_CONFLICT,
                        "Simultaneous first JobInstance creation conflicted (ORA-8177).", failure);
            }
        }
        for (Throwable failure : failures) {
            if (contains(failure, InputContractViolationException.class)) {
                return new ApiException(ApiErrorCode.ANALYSIS_INPUT_INVALID, "The versioned input contract was not satisfied.", failure);
            }
        }
        if (execution.getStatus() == BatchStatus.STOPPED || execution.getStatus() == BatchStatus.ABANDONED) {
            return new ApiException(ApiErrorCode.ANALYSIS_RESTART_UNAVAILABLE,
                    "The Job ended " + execution.getStatus() + " and is not eligible for automatic restart.");
        }
        for (Throwable failure : failures) {
            if (isConnectionFailure(failure)) {
                return new ApiException(ApiErrorCode.PERSISTENCE_UNAVAILABLE, "Persistence is temporarily unavailable.", failure);
            }
        }
        Throwable first = failures.isEmpty() ? null : failures.get(0);
        return new ApiException(ApiErrorCode.ANALYSIS_EXECUTION_FAILED, "The Job failed for an unclassified reason.", first);
    }

    /**
     * Classifies a {@link DataAccessException} raised outside a Job launch entirely -- a
     * {@code GET}/pre-launch domain-run read that failed against Oracle directly -- into the same
     * two persistence-shaped codes {@link #classifyThrown}/{@link #classifyFailedExecution} use,
     * so {@link com.bapegg.stockpilot.api.error.AnalysisApiExceptionHandler} has one single
     * classification boundary for every persistence failure this controller can produce, not two
     * separate ones that could disagree.
     */
    public ApiException classifyDataAccess(DataAccessException e) {
        if (isConnectionFailure(e)) {
            return new ApiException(ApiErrorCode.PERSISTENCE_UNAVAILABLE, "Persistence is temporarily unavailable.", e);
        }
        return new ApiException(ApiErrorCode.INTERNAL_SERVER_ERROR, "Unexpected persistence failure.", e);
    }

    private static boolean contains(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static boolean isLaunchConflict(Throwable throwable) {
        return findSqlException(throwable)
                .map(sql -> sql.getErrorCode() == ORACLE_SERIALIZATION_CONFLICT_ERROR_CODE)
                .orElse(false);
    }

    private static Optional<SQLException> findSqlException(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return Optional.of(sqlException);
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return Optional.empty();
    }

    /**
     * Walks the entire cause chain -- not just an {@link SQLException} that happens to be the
     * immediate cause of a {@link DataAccessException} node -- since a real connection failure can
     * sit arbitrarily deep beneath whatever driver/pool wrapper produced it.
     */
    private static boolean isConnectionFailure(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataAccessResourceFailureException || cause instanceof TransientDataAccessResourceException) {
                return true;
            }
            if (cause instanceof SQLException sqlException
                    && sqlException.getSQLState() != null && sqlException.getSQLState().startsWith("08")) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
