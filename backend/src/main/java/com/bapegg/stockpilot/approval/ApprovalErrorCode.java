package com.bapegg.stockpilot.approval;

/**
 * Stable application error codes for the approval transaction, matching the rows
 * {@code V10}/{@code V11} seeded into {@code sp_error_catalog}. This class does not
 * read that table -- it is the Java-side source of truth for which codes this
 * use case can produce; {@link PersistenceErrorTranslator} separately consults
 * {@code sp_error_constraint_map} for arbitrary DB constraint names.
 */
public final class ApprovalErrorCode {

    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String INVALID_DECISION_REQUEST = "INVALID_DECISION_REQUEST";
    public static final String RECOMMENDATION_NOT_FOUND = "RECOMMENDATION_NOT_FOUND";
    public static final String STALE_RECOMMENDATION = "STALE_RECOMMENDATION";
    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
    public static final String DECISION_ALREADY_TERMINAL = "DECISION_ALREADY_TERMINAL";
    public static final String DECISION_CONFLICT = "DECISION_CONFLICT";
    public static final String APPROVAL_LOCK_TIMEOUT = "APPROVAL_LOCK_TIMEOUT";
    public static final String PERSISTENCE_UNAVAILABLE = "PERSISTENCE_UNAVAILABLE";
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    private ApprovalErrorCode() {
    }
}
