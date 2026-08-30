package com.bapegg.stockpilot.api.error;

/**
 * Stable application error codes the analysis REST contract can produce, matching the rows
 * {@code V10}/{@code V11}/{@code V14} seeded into {@code sp_error_catalog}. This class does not
 * read that table -- it is only the Java-side source of truth for which codes this use case
 * produces. {@code VALIDATION_ERROR}/{@code PERSISTENCE_UNAVAILABLE}/{@code INTERNAL_SERVER_ERROR}
 * reuse the same generic rows {@code V10} already seeded for the approval use case; they are not
 * duplicated here.
 */
public final class ApiErrorCode {

    public static final String ANALYSIS_ALREADY_RUNNING = "ANALYSIS_ALREADY_RUNNING";
    public static final String ANALYSIS_LAUNCH_CONFLICT = "ANALYSIS_LAUNCH_CONFLICT";
    public static final String ANALYSIS_INPUT_INVALID = "ANALYSIS_INPUT_INVALID";
    public static final String ANALYSIS_RESTART_UNAVAILABLE = "ANALYSIS_RESTART_UNAVAILABLE";
    public static final String ANALYSIS_NOT_FOUND = "ANALYSIS_NOT_FOUND";
    public static final String ANALYSIS_EXECUTION_FAILED = "ANALYSIS_EXECUTION_FAILED";

    public static final String ANALYSIS_RESULTS_NOT_READY = "ANALYSIS_RESULTS_NOT_READY";
    public static final String INVENTORY_EXCEPTION_NOT_FOUND = "INVENTORY_EXCEPTION_NOT_FOUND";

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String PERSISTENCE_UNAVAILABLE = "PERSISTENCE_UNAVAILABLE";
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    private ApiErrorCode() {
    }
}
